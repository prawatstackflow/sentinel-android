package com.finvasia.sentinel.demo

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Mints a fresh verification session by calling the platform's
 * `POST {apiBaseUrl}/sessions` with a tenant `X-Api-Key` — the same call a
 * tenant backend makes. Powers the demo app's one-tap "Start Demo Session".
 *
 * DEMO ONLY: a real integration keeps the API key server-side and hands the
 * client only the short-lived session token. This ships the key in the app
 * purely so the example is self-contained.
 *
 * No HTTP dependency on purpose — a single [HttpURLConnection] on a background
 * thread keeps the demo dependency-free; the result is delivered on the main
 * thread.
 */
object DemoSessionService {

    data class Session(val sessionId: String, val sessionToken: String)

    private val mainHandler = Handler(Looper.getMainLooper())

    fun createSession(
        apiBaseUrl: String,
        apiKey: String,
        onSuccess: (Session) -> Unit,
        onError: (String) -> Unit,
    ) {
        thread(name = "demo-create-session") {
            val result = runCatching { requestSession(apiBaseUrl, apiKey) }
            mainHandler.post {
                result.fold(
                    onSuccess = onSuccess,
                    onFailure = { onError(it.message ?: "Failed to create session") },
                )
            }
        }
    }

    private fun requestSession(apiBaseUrl: String, apiKey: String): Session {
        val ts = System.currentTimeMillis() / 1000
        val body = JSONObject().apply {
            put("type", "kyc")
            put("subjectRef", "demo-$ts")
            put("subjectType", "individual")
            put("context", JSONObject().put("email", "demo-$ts@demo.example.com"))
        }.toString()

        val url = URL(apiBaseUrl.trimEnd('/') + "/sessions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Api-Key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw RuntimeException("HTTP $code — ${extractError(text)}")
            }
            val json = JSONObject(text)
            val sessionId = json.optString("sessionId")
            val token = json.optString("sessionToken")
            if (sessionId.isEmpty() || token.isEmpty()) {
                throw RuntimeException("Malformed response: $text")
            }
            return Session(sessionId, token)
        } finally {
            conn.disconnect()
        }
    }

    /** Pull a human message out of a Nest error body (`message` is a string or
     *  an array of validation strings); fall back to the raw text. */
    private fun extractError(text: String): String = runCatching {
        when (val message = JSONObject(text).opt("message")) {
            is JSONArray -> (0 until message.length()).joinToString("; ") { message.getString(it) }
            null -> JSONObject(text).optString("error", text)
            else -> message.toString()
        }
    }.getOrDefault(text).ifBlank { text }
}
