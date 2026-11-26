package com.isabgroup.pimsg.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.MultiFactorInfo
import com.google.firebase.auth.TotpMultiFactorGenerator
import com.google.firebase.auth.TotpSecret
import com.isabgroup.pimsg.data.Api
import com.isabgroup.pimsg.ui.PiButton
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.ScreenFrame
import com.isabgroup.pimsg.ui.piFieldColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser
    val factors = remember { mutableStateListOf<MultiFactorInfo>() }
    var secret by remember { mutableStateOf<TotpSecret?>(null) }
    var secretText by remember { mutableStateOf("") }
    var qrCodeUri by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshFactors() {
        factors.clear()
        runCatching { user?.multiFactor?.enrolledFactors?.let { factors.addAll(it) } }
    }

    LaunchedEffect(Unit) {
        runCatching { user?.reload()?.await() }
        refreshFactors()
    }

    fun startTotpEnrollment() {
        if (busy) return
        scope.launch {
            busy = true
            message = null
            try {
                val current = FirebaseAuth.getInstance().currentUser
                    ?: throw IllegalStateException("Inicie sessão para configurar a segurança.")
                current.reload().await()
                if (current.email.isNullOrBlank() || !current.isEmailVerified) {
                    throw IllegalStateException("A verificação com autenticador exige uma conta Google com e-mail verificado.")
                }
                val clientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (clientId == 0) throw IllegalStateException("A configuração OAuth do Google não está disponível.")
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(context.getString(clientId))
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credentialResult = CredentialManager.create(context).getCredential(context, request)
                val credential = credentialResult.credential
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    throw IllegalStateException("Não foi possível confirmar a conta Google.")
                }
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                current.reauthenticate(GoogleAuthProvider.getCredential(google.idToken, null)).await()
                val session = current.multiFactor.session.await()
                val generated = TotpMultiFactorGenerator.generateSecret(session).await()
                secret = generated
                secretText = generated.sharedSecretKey
                qrCodeUri = generated.generateQrCodeUrl(current.email ?: current.uid, "PiMsg")
                otp = ""
                message = "Adicione esta chave ao Google Authenticator e introduza o código de seis dígitos gerado."
            } catch (e: Exception) {
                val detail = e.message.orEmpty()
                message = when {
                    detail.contains("OPERATION_NOT_ALLOWED", true) || detail.contains("not enabled", true) ->
                        "O Firebase ainda não tem a autenticação TOTP ativada. É necessário ativar a verificação multifator TOTP no Firebase Authentication com Identity Platform."
                    else -> detail.takeIf { it.isNotBlank() } ?: "Não foi possível iniciar a configuração do autenticador."
                }
            } finally { busy = false }
        }
    }

    fun confirmTotp() {
        val pending = secret ?: return
        if (otp.length != 6 || busy) return
        scope.launch {
            busy = true
            try {
                val current = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Sessão terminada.")
                val assertion = TotpMultiFactorGenerator.getAssertionForEnrollment(pending, otp)
                current.multiFactor.enroll(assertion, "Google Authenticator").await()
                secret = null
                secretText = ""
                qrCodeUri = ""
                otp = ""
                refreshFactors()
                message = "Verificação em duas etapas ativada."
            } catch (e: Exception) {
                message = "Não foi possível confirmar o código. Verifique os seis dígitos e tente novamente."
            } finally { busy = false }
        }
    }

    ScreenFrame("Segurança da conta", onBack) {
        Text("Proteja o acesso à sua conta com uma segunda confirmação no início de sessão.", color = PiColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Text("VERIFICAÇÃO EM DUAS ETAPAS", color = PiColors.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (factors.any { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }) {
            Column(Modifier.fillMaxWidth().background(PiColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("Google Authenticator ativo", color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("A sua conta exige um código temporário do autenticador nos acessos que solicitarem verificação adicional.", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                val factor = factors.first { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        try {
                            FirebaseAuth.getInstance().currentUser?.multiFactor?.unenroll(factor).await()
                            refreshFactors()
                            message = "Autenticador removido da conta."
                        } catch (e: Exception) {
                            message = "Não foi possível remover o autenticador. Poderá ser necessário iniciar sessão novamente."
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Remover autenticador", color = PiColors.Danger) }
            }
        } else if (secret == null) {
            Text("Google Authenticator", color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Associe uma aplicação autenticadora à sua conta. A configuração exige uma conta Google com e-mail verificado e TOTP ativado no Firebase.", color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            PiButton("Configurar autenticador", { startTotpEnrollment() }, enabled = !busy)
        } else {
            Text("1. Adicione a chave ao autenticador", color = PiColors.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("No Google Authenticator, escolha adicionar uma conta com chave de configuração e introduza esta chave:", color = PiColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(secretText, color = PiColors.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("Chave do PiMsg", secretText))
                    message = "Chave copiada."
                }) { Text("Copiar chave", color = Color(0xFF8AB4F8)) }
                TextButton(onClick = {
                    runCatching { secret?.openInOtpApp(qrCodeUri) }
                        .onFailure { message = "Não foi possível abrir uma aplicação autenticadora neste aparelho." }
                }) { Text("Abrir autenticador", color = Color(0xFF8AB4F8)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("2. Confirme o código", color = PiColors.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = otp, onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                label = { Text("Código de seis dígitos") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = piFieldColors(), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PiButton("Ativar verificação em duas etapas", { confirmTotp() }, enabled = otp.length == 6 && !busy)
            TextButton(onClick = { secret = null; secretText = ""; qrCodeUri = ""; otp = ""; message = "Configuração cancelada." }) {
                Text("Cancelar", color = PiColors.TextSecondary)
            }
        }
        if (factors.none { it.factorId == TotpMultiFactorGenerator.FACTOR_ID }) {
            Text("A verificação em duas etapas só fica ativa depois de confirmar o primeiro código. Guarde a chave num local seguro.", color = PiColors.Gray, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 14.dp))
        }
        message?.let { Text(it, color = PiColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
    }
}
