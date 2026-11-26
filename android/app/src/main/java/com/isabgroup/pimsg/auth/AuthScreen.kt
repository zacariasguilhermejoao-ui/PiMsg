package com.isabgroup.pimsg.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(context.getString(R.string.default_web_client_id))
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
        val number = phone.trim().let { if (it.startsWith("+")) it else "+$it" }
        if (number.length < 8) { error = "Escreva o número com o código do país, por exemplo +244..."; return }
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
        Text("Converse e venda sem risco de perder a conta.", color = PiColors.TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = ::googleSignIn,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = androidx.compose.ui.graphics.Color.Black),
        ) { Text("Continuar com Google", fontSize = 16.sp) }

        Spacer(Modifier.height(28.dp))
        Text("ou use o seu número", color = PiColors.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PiColors.White, unfocusedBorderColor = PiColors.GrayDark,
            focusedTextColor = PiColors.White, unfocusedTextColor = PiColors.White, cursorColor = PiColors.White,
        )
        if (verificationId == null) {
            OutlinedTextField(
                value = phone, onValueChange = { phone = it }, singleLine = true,
                placeholder = { Text("+244 9XX XXX XXX", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
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
        Text(
            "Ao continuar, aceita os Termos de uso e a Política de privacidade da ISAB Group.",
            color = PiColors.Gray, fontSize = 12.sp,
        )
    }
}
