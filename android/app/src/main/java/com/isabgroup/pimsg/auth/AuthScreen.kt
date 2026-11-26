package com.isabgroup.pimsg.auth

import android.content.Context
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
import androidx.compose.material3.Text
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
import com.google.firebase.auth.FirebaseAuthException
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import org.json.JSONObject
import com.google.firebase.auth.GoogleAuthProvider
import com.isabgroup.pimsg.R
import com.isabgroup.pimsg.ui.PiColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
        val localNumber = phone.filter(Char::isDigit)
        if (dialCode == 0) { error = "Não foi possível detetar o código do país."; return }
        if (localNumber.length !in 6..14) { error = "Verifique o número de telefone."; return }
        val number = "+$dialCode$localNumber"
        scope.launch {
            busy = true
            error = null
            try {
                auth.signInAnonymously().await()
                Api.patch("/v1/me", JSONObject().put("phone_e164", number))
                onSignedIn()
            } catch (e: Exception) {
                auth.signOut()
                error = when (e) {
                    is FirebaseAuthException -> if (e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") {
                        "Ative o fornecedor Anónimo em Firebase Authentication para permitir o registo por número sem SMS."
                    } else {
                        "Não foi possível iniciar o registo. Verifique a configuração do Firebase."
                    }
                    is ApiException -> when (e.code) {
                        409 -> "Este número já está associado a outra conta."
                        429 -> "Muitas contas foram criadas nesta rede. Tente mais tarde."
                        else -> "Não foi possível criar a conta. Verifique a ligação e tente novamente."
                    }
                    else -> "Não foi possível criar a conta. Verifique a ligação e tente novamente."
                }
            } finally {
                busy = false
            }
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
        OutlinedTextField(
            value = phone, onValueChange = { value -> phone = value.filter(Char::isDigit).take(14) }, singleLine = true,
            prefix = { if (dialCode > 0) Text("+$dialCode", color = PiColors.White) },
            placeholder = { Text("Número de telefone", color = PiColors.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = ::continueWithPhone, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiColors.SurfaceHigh, contentColor = PiColors.White),
        ) { Text("Continuar com número", fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        Text(
            "O número não será confirmado. Sem associar Google, não poderá recuperar a conta após reinstalar a app ou trocar de aparelho.",
            color = PiColors.Gray, fontSize = 11.sp,
        )

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
