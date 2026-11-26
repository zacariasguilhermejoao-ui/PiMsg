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
import com.isabgroup.pimsg.chat.ChatRef
import com.isabgroup.pimsg.chat.ChatScreen
import com.isabgroup.pimsg.home.HomeScreen
import com.isabgroup.pimsg.home.StoryItem
import com.isabgroup.pimsg.home.StoryViewer
import com.isabgroup.pimsg.screens.LinksScreen
import com.isabgroup.pimsg.screens.ProfileScreen
import com.isabgroup.pimsg.screens.SettingsScreen
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

private sealed interface Screen {
    data object Home : Screen
    data class Chat(val ref: ChatRef) : Screen
    data object Profile : Screen
    data object Settings : Screen
    data object Links : Screen
    data class Stories(val items: List<StoryItem>) : Screen
}

@Composable
private fun AppRoot() {
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1600); splash = false }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        signedIn = false
        screen = Screen.Home
    }

    Crossfade(targetState = splash, label = "splash") { showing ->
        when {
            showing -> SplashScreen()
            !signedIn -> AuthScreen(onSignedIn = { signedIn = true })
            else -> {
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (val s = screen) {
                    is Screen.Home -> HomeScreen(
                        onSignOut = ::signOut,
                        onOpenChat = { screen = Screen.Chat(it) },
                        onOpenStories = { screen = Screen.Stories(it) },
                        onNavigate = { key ->
                            when (key) {
                                "profile" -> screen = Screen.Profile
                                "settings" -> screen = Screen.Settings
                                "links" -> screen = Screen.Links
                            }
                        },
                    )
                    is Screen.Chat -> ChatScreen(ref = s.ref, onBack = { screen = Screen.Home })
                    is Screen.Profile -> ProfileScreen(onBack = { screen = Screen.Home })
                    is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home }, onSignOut = ::signOut)
                    is Screen.Links -> LinksScreen(onBack = { screen = Screen.Home })
                    is Screen.Stories -> StoryViewer(stories = s.items, onClose = { screen = Screen.Home })
                }
            }
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
