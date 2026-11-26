package com.isabgroup.pimsg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import com.isabgroup.pimsg.auth.AuthScreen
import com.isabgroup.pimsg.chat.ChatScreen
import com.isabgroup.pimsg.home.HomeScreen
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiMsgTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        setContent { PiMsgTheme { AppRoot() } }
    }
}

private data class ChatRef(val id: String, val title: String)

@Composable
private fun AppRoot() {
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var chat by remember { mutableStateOf<ChatRef?>(null) }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1600); splash = false }

    Crossfade(targetState = splash, label = "splash") { showing ->
        val current = chat
        when {
            showing -> SplashScreen()
            !signedIn -> AuthScreen(onSignedIn = { signedIn = true })
            current != null -> {
                BackHandler { chat = null }
                ChatScreen(convId = current.id, title = current.title, onBack = { chat = null })
            }
            else -> HomeScreen(
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    signedIn = false
                },
                onOpenChat = { id, title -> chat = ChatRef(id, title) },
            )
        }
    }
}

// Tela de abertura: a arte enviada, ocupando o ecra todo. O logotipo fica no centro e sobrevive aos cortes.
@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(PiColors.Background)) {
        Image(
            painterResource(R.drawable.splash),
            contentDescription = "PiMsg",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
