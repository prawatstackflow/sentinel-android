package com.finvasia.sentinel

/**
 * Handle to a running verification flow. Returned by [Sentinel.launch] so the
 * host can tear the flow down when *it* decides — the SDK no longer closes
 * itself on a web status event.
 */
interface SentinelSession {
    /** Closes the verification flow. No-op if it is already gone. */
    fun dismiss()
}
