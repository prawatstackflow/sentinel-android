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
 * @property locale Optional BCP-47 locale hint. Reserved — not yet consumed by
 *   the runtime.
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
