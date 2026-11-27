package com.isabgroup.pimsg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
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
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.auth.FirebaseAuth
import com.isabgroup.pimsg.auth.AuthScreen
import com.isabgroup.pimsg.chat.ChatRef
import com.isabgroup.pimsg.chat.ChatScreen
import com.isabgroup.pimsg.home.HomeScreen
import com.isabgroup.pimsg.home.StoryItem
import com.isabgroup.pimsg.home.StoryViewer
import com.isabgroup.pimsg.screens.LinksScreen
import com.isabgroup.pimsg.screens.PiBotScreen
import com.isabgroup.pimsg.screens.PlansScreen
import com.isabgroup.pimsg.screens.ProfileScreen
import com.isabgroup.pimsg.screens.SecurityScreen
import com.isabgroup.pimsg.screens.SettingsScreen
import com.isabgroup.pimsg.ui.PiI18n
import com.isabgroup.pimsg.ui.PiColors
import com.isabgroup.pimsg.ui.PiMsgTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import java.net.HttpURLConnection
import java.net.URL

data class PendingPiLink(val type: String, val value: String)

class MainActivity : ComponentActivity() {

    var pendingPiLink by mutableStateOf<PendingPiLink?>(null)
        private set

    private fun savePiLink(link: PendingPiLink) {
        pendingPiLink = link
        getSharedPreferences("pimsg_pilink", MODE_PRIVATE).edit()
            .putString("type", link.type)
            .putString("value", link.value)
            .apply()
    }

    fun clearPendingPiLink() {
        pendingPiLink = null
        getSharedPreferences("pimsg_pilink", MODE_PRIVATE).edit()
            .remove("type")
            .remove("value")
            .apply()
    }

    private fun parsePiLinkIntent(source: Intent?) {
        val uri = source?.data ?: return
        if (uri.scheme != "https" || uri.host != "pimsg.tchilopop.com") return
        getSharedPreferences("pimsg_pilink", MODE_PRIVATE).edit()
            .putBoolean("referrer_processed", true)
            .apply()
        val parts = uri.pathSegments
        if (parts.size != 2) return
        when (parts[0]) {
            "u" -> if (parts[1].matches(Regex("[a-z0-9]{6,16}"))) {
                savePiLink(PendingPiLink("contact_slug", parts[1]))
            }
            "g" -> if (parts[1].matches(Regex("[A-Za-z0-9]{4,12}"))) {
                savePiLink(PendingPiLink("group", parts[1]))
            }
        }
    }

    private fun readInstallReferrer() {
        val prefs = getSharedPreferences("pimsg_pilink", MODE_PRIVATE)
        if (pendingPiLink != null || prefs.getBoolean("referrer_processed", false)) return
        val client = InstallReferrerClient.newBuilder(this).build()
        try {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            prefs.edit().putBoolean("referrer_processed", true).apply()
                        }
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK && pendingPiLink == null) {
                            val raw = client.installReferrer.installReferrer
                            val query = Uri.parse("https://referrer.invalid/?$raw")
                            val type = query.getQueryParameter("pimsg_type")
                            val code = query.getQueryParameter("pimsg_code")
                            if (!code.isNullOrBlank() && code.matches(Regex("[A-Za-z0-9]{4,16}"))) {
                                val route = when (type) {
                                    "contact" -> PendingPiLink("contact_code", code.uppercase())
                                    "group" -> PendingPiLink("group", code.uppercase())
                                    else -> null
                                }
                                route?.let { runOnUiThread { if (pendingPiLink == null) savePiLink(it) } }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { client.endConnection() }
                    }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        } catch (_: Exception) {
            runCatching { client.endConnection() }
        }
    }

    /** Conteúdo recebido via share (texto/ficheiros de outras apps). */
    var pendingShare by mutableStateOf<ShareIncoming?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        PiI18n.init(this)
        // Evita zoom de sistema na interface
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        pendingShare = intent.parseShare()
        pendingPiLink = getSharedPreferences("pimsg_pilink", MODE_PRIVATE).let { prefs ->
            val type = prefs.getString("type", null)
            val value = prefs.getString("value", null)
            if (type != null && value != null) PendingPiLink(type, value) else null
        }
        parsePiLinkIntent(intent)
        readInstallReferrer()
        // Mantém permissão de leitura nos URIs partilhados enquanto a Activity existir
        intent?.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: SecurityException) {
                        // Alguns providers só dão permissão temporária — ok
                    }
                }
            }
        }
        intent?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)?.let { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
        }
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        setContent { PiMsgTheme { AppRoot(activity = this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingShare = intent.parseShare()
        parsePiLinkIntent(intent)
    }

    fun clearShare() {
        pendingShare = null
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Chat(val ref: ChatRef) : Screen
    data object Profile : Screen
    data object Settings : Screen
    data object Security : Screen
    data object Links : Screen
    data object PiBot : Screen
    data object Plans : Screen
    data class Stories(val items: List<StoryItem>) : Screen
}

@Composable
private fun AppRoot(activity: MainActivity) {
    var signedIn by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var splash by remember { mutableStateOf(true) }
    var invalidClock by remember { mutableStateOf(false) }
    var checkingClock by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun verifyClock() {
        checkingClock = true
        invalidClock = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://www.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                }
                connection.connect()
                val serverDate = connection.getHeaderFieldDate("Date", -1L)
                serverDate > 0L && kotlin.math.abs(System.currentTimeMillis() - serverDate) > 5 * 60 * 1000L
            } catch (_: Exception) {
                false
            } finally {
                connection?.disconnect()
            }
        }
        checkingClock = false
    }

    LaunchedEffect(Unit) { delay(1600); splash = false }
    LaunchedEffect(splash) {
        if (!splash) verifyClock()
    }
    LaunchedEffect(activity.pendingPiLink, signedIn) {
        if (activity.pendingPiLink != null && signedIn) screen = Screen.Home
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        signedIn = false
        screen = Screen.Home
        activity.clearShare()
    }

    Crossfade(targetState = splash, label = "splash") { showing ->
        when {
            showing -> SplashScreen()
            checkingClock -> Box(Modifier.fillMaxSize().background(PiColors.Background))
            invalidClock -> TelaDataIncorreta(
                onVerificarNovamente = { scope.launch { verifyClock() } }
            )
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
                                "bot" -> screen = Screen.PiBot
                                "plans" -> screen = Screen.Plans
                            }
                        },
                        initialContactCode = activity.pendingPiLink?.takeIf { it.type == "contact_code" }?.value,
                        initialContactSlug = activity.pendingPiLink?.takeIf { it.type == "contact_slug" }?.value,
                        initialGroupCode = activity.pendingPiLink?.takeIf { it.type == "group" }?.value,
                        onPiLinkConsumed = activity::clearPendingPiLink,
                    )
                    is Screen.Chat -> ChatScreen(
                        ref = s.ref,
                        onBack = {
                            screen = Screen.Home
                            activity.clearShare()
                        },
                        pendingShare = activity.pendingShare,
                        onShareConsumed = { activity.clearShare() },
                        onOpenBots = { screen = Screen.PiBot },
                    )
                    is Screen.Profile -> ProfileScreen(onBack = { screen = Screen.Home })
                    is Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home }, onSignOut = ::signOut, onSecurity = { screen = Screen.Security })
                    is Screen.Security -> SecurityScreen(onBack = { screen = Screen.Settings })
                    is Screen.Links -> LinksScreen(onBack = { screen = Screen.Home })
                    is Screen.PiBot -> PiBotScreen(onBack = { screen = Screen.Home })
                    is Screen.Plans -> PlansScreen(onBack = { screen = Screen.Home })
                    is Screen.Stories -> StoryViewer(stories = s.items, onClose = { screen = Screen.Home })
                }
            }
        }
    }
}

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
