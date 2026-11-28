package com.isabgroup.pimsg.auth

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.isabgroup.pimsg.ui.PiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.data.ApiException
import org.json.JSONObject
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FacebookAuthProvider
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.isabgroup.pimsg.MainActivity
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

private fun describeAuthError(e: Exception, provider: String): String = when {
    e is FirebaseAuthUserCollisionException ->
        "Já existe uma conta com este e-mail criada por outro método (por exemplo Google ou e-mail). Entra com esse método."
    e is FirebaseNetworkException -> "Sem ligação à Internet. Liga-te e tenta novamente."
    e is ApiException && e.code == 401 -> "O servidor não reconheceu a sessão. Tenta novamente dentro de instantes."
    e is ApiException && e.code == 403 -> runCatching { org.json.JSONObject(e.message ?: "{}").optString("error") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: "Esta conta não pode entrar neste momento."
    e is ApiException && e.code == 429 -> "Demasiadas contas criadas a partir desta rede. Tenta mais tarde."
    e is ApiException -> "O servidor respondeu com erro ${e.code}. Tenta novamente."
    else -> "Falha no $provider (" + e.javaClass.simpleName + "): " + (e.message?.lineSequence()?.firstOrNull()?.take(160) ?: "sem detalhes")
}

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val hostActivity = context as? MainActivity
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    val dialCode = remember(context) { detectDialCode(context).second }
    var phone by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var legalAccepted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mfaResolver by remember { mutableStateOf<MultiFactorResolver?>(null) }
    var mfaCode by remember { mutableStateOf("") }
    var showEmailLogin by remember { mutableStateOf(false) }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }

    fun facebookSignIn() {
        if (!legalAccepted) {
            error = "Leia e aceite os Termos de Utilização e a Política de Privacidade."
            return
        }
        val activity = hostActivity
        if (activity == null) {
            error = "Não foi possível iniciar o Facebook neste ecrã."
            return
        }
        busy = true
        error = null
        // Limpa qualquer sessão Facebook antiga/inválida antes de iniciar o login.
        runCatching { LoginManager.getInstance().logOut() }
        LoginManager.getInstance().logInWithReadPermissions(activity, listOf("email", "public_profile"))
    }

    suspend fun signIn(credential: AuthCredential) {
        try {
            if (!legalAccepted) throw IllegalStateException("Leia e aceite os Termos de Utilização e a Política de Privacidade.")
            auth.signInWithCredential(credential).await()
            try {
                Api.post("/v1/legal/accept", JSONObject().put("terms_version", "1.3").put("privacy_version", "1.3"))
            } catch (apiError: Exception) {
                auth.signOut()
                throw apiError
            }
            onSignedIn()
        } catch (e: FirebaseAuthMultiFactorException) {
            mfaResolver = e.resolver
            mfaCode = ""
            error = null
        }
    }

    DisposableEffect(hostActivity) {
        val manager = hostActivity?.facebookCallbackManager
        if (manager != null) {
            LoginManager.getInstance().registerCallback(manager, object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult) {
                    scope.launch {
                        try {
                            signIn(FacebookAuthProvider.getCredential(result.accessToken.token))
                        } catch (e: Exception) {
                            error = describeAuthError(e, "Facebook")
                        } finally {
                            busy = false
                        }
                    }
                }
                override fun onCancel() { busy = false }
                override fun onError(exception: FacebookException) {
                    busy = false
                    error = "Falha no Facebook Login: " + (exception.message?.lineSequence()?.firstOrNull()?.take(160) ?: "tenta novamente") + "."
                }
            })
        }
        onDispose {
            if (manager != null) LoginManager.getInstance().unregisterCallback(manager)
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
                Api.post("/v1/legal/accept", JSONObject().put("terms_version", "1.3").put("privacy_version", "1.3"))
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
                    is ApiException, is FirebaseAuthUserCollisionException, is FirebaseNetworkException -> describeAuthError(e, "Google")
                    else -> "Falha no Google (" + e.javaClass.simpleName + "): " + (detail ?: "sem detalhes do erro")
                }
            } finally {
                busy = false
            }
        }
    }

    fun signInWithEmail() {
        val address = loginEmail.trim()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() || loginPassword.isBlank()) {
            error = "Introduza o e-mail e a palavra-passe."
            return
        }
        scope.launch {
            busy = true
            error = null
            try {
                auth.signInWithEmailAndPassword(address, loginPassword).await()
                if (auth.currentUser?.isEmailVerified != true) {
                    auth.signOut()
                    throw IllegalStateException("Este e-mail ainda não foi confirmado. Confirme-o antes de entrar.")
                }
                Api.post("/v1/legal/accept", JSONObject().put("terms_version", "1.3").put("privacy_version", "1.3"))
                onSignedIn()
            } catch (e: Exception) {
                error = e.message?.takeIf { it.contains("confirmado") }
                    ?: "Não foi possível entrar com este e-mail e palavra-passe. Verifique os dados."
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
            if (!legalAccepted) { error = "Leia e aceite os Termos de Utilização e a Política de Privacidade."; return@launch }
            busy = true
            error = null
            try {
                // A conta é criada com Firebase Anonymous; o número fica guardado
                // como contacto não verificado no perfil PiMsg. Não usa Phone Auth/SMS.
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                Api.patch("/v1/me", JSONObject().put("phone_e164", number))
                Api.post("/v1/legal/accept", JSONObject().put("terms_version", "1.3").put("privacy_version", "1.3"))
                onSignedIn()
            } catch (e: Exception) {
                error = when (e) {
                    is ApiException -> "A conta foi iniciada, mas não foi possível guardar o número. Verifique a ligação e tente novamente."
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = legalAccepted,
                onCheckedChange = { legalAccepted = it },
                enabled = !busy,
            )
            Text(
                "Li e aceito os Termos de Utilização e a Política de Privacidade do PiMsg.",
                color = PiColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f).clickable { legalAccepted = !legalAccepted },
            )
        }

        Button(
            onClick = ::googleSignIn,
            enabled = !busy && mfaResolver == null && legalAccepted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiColors.White, contentColor = Color(0xFF1F1F1F)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GoogleMark()
                Text("Continuar com Google", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = ::facebookSignIn,
            enabled = !busy && mfaResolver == null && legalAccepted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2), contentColor = Color.White),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("f", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Continuar com Facebook", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("ou use o seu número", color = PiColors.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PiColors.White,
            unfocusedBorderColor = PiColors.GrayDark,
            focusedTextColor = PiColors.White,
            unfocusedTextColor = PiColors.White,
            cursorColor = PiColors.White,
            focusedPlaceholderColor = PiColors.Gray,
            unfocusedPlaceholderColor = PiColors.Gray,
            focusedContainerColor = PiColors.Surface,
            unfocusedContainerColor = PiColors.Surface,
        )
        OutlinedTextField(
            shape = RoundedCornerShape(22.dp),
            value = phone, onValueChange = { value -> phone = value.filter(Char::isDigit).take(14) }, singleLine = true, enabled = mfaResolver == null,
            prefix = { if (dialCode > 0) Text("+$dialCode", color = PiColors.White) },
            placeholder = { Text("Número de telefone", color = PiColors.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors,
        )
        Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = ::continueWithPhone, enabled = !busy && mfaResolver == null && legalAccepted,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PiColors.SurfaceHigh, contentColor = PiColors.White),
        ) { Text("Criar conta com número", fontSize = 16.sp) }

        TextButton(onClick = { showEmailLogin = !showEmailLogin; error = null }) {
            Text(if (showEmailLogin) "Ocultar início de sessão por e-mail" else "Já tens uma conta? Entrar com e-mail", color = PiColors.White)
        }
        if (showEmailLogin) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                shape = RoundedCornerShape(22.dp),
                value = loginEmail,
                onValueChange = { loginEmail = it.take(180) },
                singleLine = true,
                placeholder = { Text("E-mail", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                shape = RoundedCornerShape(22.dp),
                value = loginPassword,
                onValueChange = { loginPassword = it },
                singleLine = true,
                placeholder = { Text("Palavra-passe", color = PiColors.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = ::signInWithEmail,
                enabled = !busy && loginEmail.isNotBlank() && loginPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PiColors.SurfaceHigh, contentColor = PiColors.White),
            ) { Text("Entrar com e-mail", fontSize = 16.sp) }
            TextButton(
                onClick = {
                    val address = loginEmail.trim()
                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                        error = "Introduza o e-mail da sua conta para recuperar a palavra-passe."
                    } else {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                auth.sendPasswordResetEmail(address).await()
                                error = "Enviámos uma ligação para criar uma nova palavra-passe."
                            } catch (_: Exception) {
                                error = "Não foi possível enviar a recuperação. Confirme o e-mail e tente novamente."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && loginEmail.isNotBlank(),
            ) { Text("Esqueci-me da palavra-passe", color = PiColors.White) }
        }

        if (mfaResolver != null) {
            Spacer(Modifier.height(18.dp))
            Text("Verificação adicional", color = PiColors.White, fontSize = 16.sp)
            Text("Introduza o código de seis dígitos gerado pela aplicação autenticadora.", color = PiColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
            shape = RoundedCornerShape(22.dp),
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
            "A tua aceitação é registada com a versão dos documentos e a data da confirmação.",
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
