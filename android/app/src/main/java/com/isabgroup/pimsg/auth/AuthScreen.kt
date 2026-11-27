package com.isabgroup.pimsg.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.telephony.TelephonyManager
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import org.json.JSONObject
import com.google.firebase.auth.GoogleAuthProvider
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.ui.PiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.google.i18n.phonenumbers.PhoneNumberUtil

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun detectDialCode(context: Context): Pair<String, Int> {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    val localeRegion = Locale.getDefault().country
    val candidates = listOf(telephony?.networkCountryIso, telephony?.simCountryIso, localeRegion)
    val util = PhoneNumberUtil.getInstance()
    for (candidate in candidates) {
        val region = candidate?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 } ?: continue
        val code = runCatching { util.getCountryCodeForRegion(region) }.getOrDefault(0)
        if (code > 0) return region to code
    }
    return localeRegion to 0
}

@Composable
private fun GoogleMark() {
    Image(
        painter = painterResource(R.drawable.google_g),
        contentDescription = "Google",
        modifier = Modifier.size(20.dp),
    )
}

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    val dialCode = remember(context) { detectDialCode(context).second }
    var phone by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mfaResolver by remember { mutableStateOf<MultiFactorResolver?>(null) }
    var mfaCode by remember { mutableStateOf("") }

    suspend fun signIn(credential: AuthCredential) {
        try {
            auth.signInWithCredential(credential).await()
            onSignedIn()
        } catch (e: FirebaseAuthMultiFactorException) {
            mfaResolver = e.resolver
            mfaCode = ""
            error = null
        }
    }

    fun verifySecondFactor() {
        val resolver = mfaResolver ?: return
        val hint = resolver.hints.firstOrNull { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }
        if (hint == null) {
            error = "A conta exige um método de verificação que esta versão ainda não suporta."
            return
        }
        if (mfaCode.length != 6) {
            error = "Introduza o código de seis dígitos do autenticador."
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                val assertion = TotpMultiFactorGenerator.getAssertionForSignIn(hint.uid, mfaCode)
                resolver.resolveSignIn(assertion).await()
                mfaResolver = null
                onSignedIn()
            } catch (e: Exception) {
                error = "Código de verificação incorreto ou expirado."
            } finally {
                busy = false
            }
        }
    }

    fun googleSignIn() {
        scope.launch {
            busy = true; error = null
            try {
                val webClientIdRes = context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName,
                )
                if (webClientIdRes == 0) {
                    throw IllegalStateException(
                        "Firebase não tem um cliente OAuth Web. Adicione um cliente Web ao projeto Firebase e atualize GOOGLE_SERVICES_JSON."
                    )
                }
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(webClientIdRes))
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val cred = result.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val google = GoogleIdTokenCredential.createFrom(cred.data)
                    signIn(GoogleAuthProvider.getCredential(google.idToken, null))
                } else {
                    error = "Não foi possível entrar com o Google."
                }
            } catch (e: Exception) {
                val detail = e.message?.lineSequence()?.firstOrNull()?.take(180)
                error = when (e) {
                    is IllegalStateException -> detail ?: "Configuração do Google incompleta no Firebase."
                    else -> "Falha no Google (" + e.javaClass.simpleName + "): " + (detail ?: "sem detalhes do erro")
                }
            } finally {
                busy = false
            }
        }
    }

    fun continueWithPhone() {
        val activity = context.findActivity()
        val localNumber = phone.filter(Char::isDigit)
        if (activity == null) { error = "Não foi possível iniciar a confirmação neste ecrã."; return }
        if (dialCode == 0) { error = "Não foi possível detetar o código do país."; return }
        if (localNumber.length !in 6..14) { error = "Verifique o número de telefone."; return }
        val number = "+$dialCode$localNumber"
        busy = true
        error = null
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    try { signIn(credential) }
                    catch (e: Exception) { error = "Não foi possível confirmar este número." }
                    finally { busy = false }
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                error = when ((e as? FirebaseAuthException)?.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Ative a autenticação por telefone no Firebase Authentication."
                    "ERROR_TOO_MANY_REQUESTS" -> "Foram feitas demasiadas tentativas. Tente mais tarde."
                    else -> "Não foi possível enviar o código. Confirme o número e a configuração do Firebase."
                }
                busy = false
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
                smsCode = ""
                busy = false
            }
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(number)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    fun confirmPhoneCode() {
        val id = verificationId ?: return
        if (smsCode.length != 6) { error = "Introduza o código recebido por SMS."; return }
        scope.launch {
            busy = true
            error = null
            try {
                signIn(PhoneAuthProvider.getCredential(id, smsCode))
            } catch (e: Exception) {
                error = "Código incorreto ou expirado. Tente novamente."
            } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(PiColors.Background).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.pimsg_logo), "PiMsg", Modifier.height(44.dp))
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = ::googleSignIn,
            enabled = !busy && mfaResolver == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = Color(0xFF1F1F1F)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoogleMark()
                Text("Continuar com Google", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("ou use o seu número", color = PiColors.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PiColors.White, unfocusedBorderColor = PiColors.GrayDark,
            focusedTextColor = PiColors.White, unfocusedTextColor = PiColors.White, cursorColor = PiColors.White,
        )
        if (verificationId == null) {
            OutlinedTextField(
                value = phone, onValueChange = { value -> phone = value.filter(Char::isDigit).take(14) }, singleLine = true, enabled = mfaResolver == null,
                prefix = { if (dialCode > 0) Text("+$dialCode", color = PiColors.White) },
                placeholder = { Text("Número de telefone", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::continueWithPhone, enabled = !busy && mfaResolver == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.SurfaceHigh, contentColor = PiColors.White),
            ) { Text("Receber código por SMS", fontSize = 16.sp) }
        } else {
            OutlinedTextField(
                value = smsCode, onValueChange = { smsCode = it.filter(Char::isDigit).take(6) }, singleLine = true,
                placeholder = { Text("Código de 6 dígitos", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::confirmPhoneCode, enabled = !busy && smsCode.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = Color.Black),
            ) { Text("Confirmar número", fontSize = 16.sp) }
            TextButton(onClick = { verificationId = null; smsCode = ""; error = null }) {
                Text("Alterar número", color = PiColors.TextSecondary)
            }
        }

        if (mfaResolver != null) {
            Spacer(Modifier.height(18.dp))
            Text("Verificação adicional", color = PiColors.White, fontSize = 16.sp)
            Text("Introduza o código de seis dígitos gerado pela aplicação autenticadora.", color = PiColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = mfaCode,
                onValueChange = { mfaCode = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                placeholder = { Text("Código de 6 dígitos", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = ::verifySecondFactor,
                enabled = !busy && mfaCode.length == 6,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = Color.Black),
            ) { Text("Confirmar código") }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = PiColors.Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Ao continuar, confirma que leu e aceita os Termos de Utilização e a Política de Privacidade do PiMsg.",
            color = PiColors.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Termos de Utilização", color = Color(0xFF8AB4F8), fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/terms") })
            Text("  ·  ", color = PiColors.Gray, fontSize = 12.sp)
            Text("Privacidade", color = Color(0xFF8AB4F8), fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/privacy") })
        }
        Text(
            "Regras da comunidade",
            color = PiColors.White, fontSize = 12.sp, textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(top = 8.dp).clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/community") },
        )
    }
}
