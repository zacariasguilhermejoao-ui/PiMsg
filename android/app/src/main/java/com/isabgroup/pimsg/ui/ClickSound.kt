package com.isabgroup.pimsg.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Preferências de resposta tátil e sonora do PiMsg. */
object PiFeedback {
    private const val PREFS = "pimsg_preferences"
    private const val SOUND = "sound_enabled"
    private const val HAPTIC = "haptic_enabled"

    fun soundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(SOUND, true)

    fun hapticEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HAPTIC, true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(SOUND, enabled).apply()
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(HAPTIC, enabled).apply()
    }
}

private object ClickTone {
    private val generator = ToneGenerator(AudioManager.STREAM_SYSTEM, 48)
    @Synchronized fun play() {
        runCatching { generator.startTone(ToneGenerator.TONE_PROP_BEEP, 45) }
    }
}

/** Feedback curto e discreto. Respeita as opções escolhidas nas Definições. */
fun View.playClick() {
    val ctx = context
    if (PiFeedback.soundEnabled(ctx)) {
        runCatching {
            val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) ClickTone.play()
        }
    }
    if (PiFeedback.hapticEnabled(ctx)) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(22, 70))
        }
    }
}

@Composable
fun rememberClickSound(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.playClick() } }
}
