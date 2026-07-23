package com.finvasia.sentinel

import org.json.JSONObject

/**
 * A request from the hosted verification flow to open LiveChat **natively**.
 *
 * Inside the SDK WebView the hosted flow does NOT render LiveChat itself (a
 * fullscreen third-party widget can't reliably clear the status bar/notch there).
 * When the user taps "Chat with support" it instead posts a `live_chat` bridge
 * message, which the SDK delivers to the host's `onLiveChat` callback carrying the
 * same data the web widget would use. The host opens LiveChat with its own
 * LiveChat Android SDK (or support flow), where the system handles the insets.
 *
 * This is a **non-terminal** request: it does not change the flow's status and
 * never finishes the WebView — the verification flow stays live underneath.
 */
data class LiveChatRequest(
    /** LiveChat license id. A string to stay lossless for large ids. */
    val license: String,
    /** Resolved routing group; `0` is LiveChat's built-in General group. */
    val group: Int,
    /** Whether the tenant opted to forward the declared name/email to LiveChat. */
    val forwardPii: Boolean,
    /** Non-PII session context (LiveChat "session variables"). */
    val sessionVariables: Map<String, String>,
    /** Declared name — present only when [forwardPii] is true and known. */
    val customerName: String?,
    /** Declared email — present only when [forwardPii] is true and known. */
    val customerEmail: String?,
) {
    companion object {
        /**
         * Parse from the `live_chat` bridge message JSON. Returns null when the
         * required `license` is missing or blank.
         */
        fun fromJson(json: JSONObject): LiveChatRequest? {
            val license = json.optString("license").takeIf { it.isNotBlank() } ?: return null
            val vars = mutableMapOf<String, String>()
            json.optJSONObject("sessionVariables")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = obj.opt(key)
                    if (value is String) vars[key] = value
                }
            }
            return LiveChatRequest(
                license = license,
                group = json.optInt("group", 0),
                forwardPii = json.optBoolean("forwardPii", false),
                sessionVariables = vars,
                customerName = json.optString("customerName").takeIf { it.isNotBlank() },
                customerEmail = json.optString("customerEmail").takeIf { it.isNotBlank() },
            )
        }
    }
}
