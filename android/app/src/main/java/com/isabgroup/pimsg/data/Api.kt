package com.isabgroup.pimsg.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : Exception(message) {
    // Motivo enviado pela API (por exemplo limit_messages ou limit_contacts)
    val reason: String? get() = try { org.json.JSONObject(message ?: "{}").optString("reason").ifEmpty { null } } catch (e: Exception) { null }
}

class Downloaded(val bytes: ByteArray, val mime: String)

object Api {
    fun hasInternet(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        // Não exigimos NET_CAPABILITY_VALIDATED: em algumas redes móveis o teste da Google falha
        // mesmo com ligação utilizável e o app ficava "offline" sem tentar sincronizar.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private const val BASE = "https://pimsg.tchilopop.com"

    private suspend fun token(forceRefresh: Boolean = false): String =
        FirebaseAuth.getInstance().currentUser?.getIdToken(forceRefresh)?.await()?.token
            ?: throw ApiException(401, "sem sessao")

    suspend fun get(path: String): JSONObject = call("GET", path, null)
    suspend fun post(path: String, body: JSONObject): JSONObject = call("POST", path, body)
    suspend fun put(path: String, body: JSONObject): JSONObject = call("PUT", path, body)
    suspend fun patch(path: String, body: JSONObject): JSONObject = call("PATCH", path, body)
    suspend fun delete(path: String): JSONObject = call("DELETE", path, null)
    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    // Envia um ficheiro para o R2 pela API. Devolve o id do ficheiro.
    suspend fun upload(open: () -> java.io.InputStream?, size: Long, mime: String): String =
        try {
            uploadOnce(open, size, mime, token())
        } catch (e: ApiException) {
            if (e.code == 401) uploadOnce(open, size, mime, token(forceRefresh = true)) else throw e
        }

    private suspend fun uploadOnce(open: () -> java.io.InputStream?, size: Long, mime: String, token: String): String {
        return withContext(Dispatchers.IO) {
            val c = URL("$BASE/v1/media").openConnection() as HttpURLConnection
            try {
                c.requestMethod = "PUT"
                c.doOutput = true
                c.setFixedLengthStreamingMode(size)
                c.connectTimeout = 15_000
                c.readTimeout = 120_000
                c.setRequestProperty("Authorization", "Bearer $token")
                c.setRequestProperty("Content-Type", mime)
                c.setRequestProperty("X-File-Size", size.toString())
                val input = open() ?: throw ApiException(400, "ficheiro ilegivel")
                input.use { i -> c.outputStream.use { o -> i.copyTo(o) } }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                if (code !in 200..299) throw ApiException(code, text)
                JSONObject(text).getString("id")
            } finally {
                c.disconnect()
            }
        }
    }

    // Descarrega um ficheiro apenas quando o utilizador toca nele.
    suspend fun download(path: String): Downloaded =
        try {
            downloadOnce(path, token())
        } catch (e: ApiException) {
            if (e.code == 401) downloadOnce(path, token(forceRefresh = true)) else throw e
        }

    private suspend fun downloadOnce(path: String, token: String): Downloaded {
        return withContext(Dispatchers.IO) {
            val c = URL(BASE + path).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 120_000
                c.setRequestProperty("Authorization", "Bearer $token")
                val code = c.responseCode
                if (code !in 200..299) throw ApiException(code, c.errorStream?.bufferedReader()?.use { it.readText() } ?: "{}")
                Downloaded(c.inputStream.use { it.readBytes() }, c.contentType ?: "application/octet-stream")
            } finally {
                c.disconnect()
            }
        }
    }

    private suspend fun call(method: String, path: String, body: JSONObject?): JSONObject =
        try {
            callOnce(method, path, body, token())
        } catch (e: ApiException) {
            // Token expirado ou revogado: pede um novo ao Firebase e repete uma vez.
            if (e.code == 401) callOnce(method, path, body, token(forceRefresh = true)) else throw e
        }

    private suspend fun callOnce(method: String, path: String, body: JSONObject?, token: String): JSONObject {
        return withContext(Dispatchers.IO) {
            val c = URL(BASE + path).openConnection() as HttpURLConnection
            try {
                if (method == "PATCH") {
                    c.requestMethod = "POST"
                    c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
                } else {
                    c.requestMethod = method
                }
                c.connectTimeout = 15_000
                c.readTimeout = 20_000
                c.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                if (code !in 200..299) throw ApiException(code, text)
                JSONObject(text)
            } finally {
                c.disconnect()
            }
        }
    }
}

fun JSONObject.strOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
