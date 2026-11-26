package com.isabgroup.pimsg.ui

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Som de clique do sistema — só para ações importantes
 * (enviar, anexar, botões principais). Não usar em listas nem em cada ícone.
 */
fun Context.playClick() {
    try {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            am.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f)
        }
    } catch (_: Exception) {
        // alguns aparelhos bloqueiam o efeito de sistema
    }
}

/** Preferível em Compose: usa o View para respeitar as definições de som do utilizador. */
fun View.playClick() {
    try {
        playSoundEffect(SoundEffectConstants.CLICK)
    } catch (_: Exception) { }
}

@Composable
fun rememberClickSound(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.playClick() } }
}
