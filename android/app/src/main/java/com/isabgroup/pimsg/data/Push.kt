package com.isabgroup.pimsg.data

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

object Push {
    private fun deviceId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("pimsg", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    // Regista o aparelho e o token de notificacoes na API
    suspend fun register(ctx: Context) {
        val token = FirebaseMessaging.getInstance().token.await()
        Api.post("/v1/devices", JSONObject().put("device_hash", deviceId(ctx)).put("fcm_token", token))
    }
}
