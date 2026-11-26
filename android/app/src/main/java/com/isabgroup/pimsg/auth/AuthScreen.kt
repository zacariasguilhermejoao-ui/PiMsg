package com.isabgroup.pimsg.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.telephony.TelephonyManager
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.ui.PiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import com.google.i18n.phonenumbers.PhoneNumberUtil

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
    Canvas(Modifier.size(21.dp)) {
        val width = size.minDimension * 0.22f
        val stroke = Stroke(width = width, cap = StrokeCap.Butt)
        drawArc(Color(0xFF4285F4), -45f, 90f, false, style = stroke)
        drawArc(Color(0xFFEA4335), 45f, 90f, false, style = stroke)
        drawArc(Color(0xFFFBBC05), 135f, 90f, false, style = stroke)
        drawArc(Color(0xFF34A853), 225f, 90f, false, style = stroke)
        drawLine(Color(0xFF4285F4), Offset(size.width * 0.50f, size.height * 0.50f), Offset(size.width * 0.96f, size.height * 0.50f), width)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    val (detectedRegion, dialCode) = remember(context) { detectDialCode(context) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun signIn(credential: AuthCredential) {
        auth.signInWithCredential(credential).await()
        onSignedIn()
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
                error = "Não foi possível entrar com o Google. Tente novamente."
            } finally {
                busy = false
            }
        }
    }

    fun sendCode() {
        val activity = context.findActivity() ?: return
        val localNumber = phone.filter(Char::isDigit)
        if (dialCode == 0) { error = "Não foi possível detetar o código do país."; return }
        if (localNumber.length !in 6..15) { error = "Verifique o número de telefone."; return }
        val number = "+$dialCode$localNumber"
        busy = true; error = null
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    try { signIn(credential) } catch (e: Exception) { error = "Não foi possível confirmar o número." }
                    busy = false
                }
            }
            override fun onVerificationFailed(e: FirebaseException) {
                error = "Não foi possível enviar o código. Verifique o número."
                busy = false
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                verificationId = id
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

    fun confirmCode() {
        val id = verificationId ?: return
        scope.launch {
            busy = true; error = null
            try { signIn(PhoneAuthProvider.getCredential(id, code.trim())) }
            catch (e: Exception) { error = "Código incorreto ou expirado." }
            finally { busy = false }
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
            enabled = !busy,
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
                value = phone, onValueChange = { value -> phone = value.filter(Char::isDigit).take(15) }, singleLine = true,
                prefix = { if (dialCode > 0) Text("+$dialCode", color = PiColors.White) },
                placeholder = { Text("Número de telefone", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            if (dialCode == 0) {
                Text("Não foi possível detetar o país. Ative o cartão SIM ou defina a região do telemóvel.", color = PiColors.Danger, fontSize = 12.sp)
            } else {
                Text("País detetado automaticamente: $detectedRegion", color = PiColors.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start).padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::sendCode, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.SurfaceHigh, contentColor = PiColors.White),
            ) { Text("Receber código", fontSize = 16.sp) }
        } else {
            OutlinedTextField(
                value = code, onValueChange = { code = it.take(6) }, singleLine = true,
                placeholder = { Text("Código de 6 dígitos", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = ::confirmCode, enabled = !busy && code.length >= 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = androidx.compose.ui.graphics.Color.Black),
            ) { Text("Confirmar", fontSize = 16.sp) }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = PiColors.Danger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Termos de uso", color = PiColors.Gray, fontSize = 12.sp, modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/terms") })
            Text("  ·  ", color = PiColors.Gray, fontSize = 12.sp)
            Text("Privacidade", color = PiColors.Gray, fontSize = 12.sp, modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/privacy") })
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("Reembolso", color = PiColors.Gray, fontSize = 12.sp, modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/refund") })
            Text("  ·  ", color = PiColors.Gray, fontSize = 12.sp)
            Text("Regras da comunidade", color = PiColors.Gray, fontSize = 12.sp, modifier = Modifier.clickable { com.isabgroup.pimsg.ui.openUrl(context, "https://pimsg.tchilopop.com/community") })
        }
    }
}
