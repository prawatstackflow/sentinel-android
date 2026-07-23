package com.finvasia.sentinel

/**
 * Configuration for a single verification session.
 *
 * @property sessionId Identity session id from the tenant backend's
 *   `POST /sessions` call.
 * @property sessionToken Scoped session token (JWT) from the same call. Sent by
 *   the runtime as a bearer token; TTL is 24h (see the mobile SDK contract).
 * @property hostedFlowBaseUrl Origin of the hosted verification flow. Defaults
 *   to the Finvasia test environment; override per environment / self-host.
 * @property theme Color scheme; [SentinelTheme.SYSTEM] follows the device.
 * @property locale Optional BCP-47 language for the flow (e.g. "fr", "ar"). Sent
 *   as `?locale=` on the WebView URL; the runtime applies it to the session at
 *   bootstrap so the assistant AND all UI chrome render in this language. Omit to
 *   use the session's own language (context.language / tenant default / English).
 */
data class SentinelConfig(
    val sessionId: String,
    val sessionToken: String,
    val hostedFlowBaseUrl: String = DEFAULT_HOSTED_FLOW_BASE_URL,
    val theme: SentinelTheme = SentinelTheme.SYSTEM,
    val locale: String? = null,
) {
    companion object {
        const val DEFAULT_HOSTED_FLOW_BASE_URL: String = "https://test-identity.finvasia.com"
    }
}
