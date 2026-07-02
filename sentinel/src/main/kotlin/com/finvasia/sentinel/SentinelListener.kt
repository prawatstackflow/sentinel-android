package com.finvasia.sentinel

/** Receives [SentinelEvent]s from a running verification flow. */
fun interface SentinelListener {
    fun onEvent(event: SentinelEvent)
}
