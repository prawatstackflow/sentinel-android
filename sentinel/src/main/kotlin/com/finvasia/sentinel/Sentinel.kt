package com.finvasia.sentinel

import android.content.Context
import com.finvasia.sentinel.internal.SentinelRuntime
import com.finvasia.sentinel.internal.VerificationActivity

/**
 * Entry point for the Sentinel identity-verification flow.
 *
 * [launch] starts the flow and streams every status change to your
 * [SentinelListener]. The SDK **does not close itself** when the flow completes,
 * errors, or the user cancels — it only reports the event. Use the returned
 * [SentinelSession] to close when the host decides:
 *
 * ```kotlin
 * val session = Sentinel.launch(context, config) { event ->
 *     when (event) {
 *         is SentinelEvent.Completed -> { showResult(event.outcome); currentSession?.dismiss() }
 *         SentinelEvent.Cancelled    -> currentSession?.dismiss()
 *         is SentinelEvent.Error     -> { showError(event.message); currentSession?.dismiss() }
 *         is SentinelEvent.LoadFailed -> { showError(event.message); currentSession?.dismiss() }
 *         SentinelEvent.Ready        -> { /* runtime mounted */ }
 *     }
 * }
 * currentSession = session
 * ```
 *
 * The system-back gesture is the one exception: it emits [SentinelEvent.Cancelled]
 * and finishes, so the user is never trapped.
 */
object Sentinel {
    /**
     * Launches the verification flow described by [config] and reports status
     * updates to [listener]. Returns a [SentinelSession] the host uses to close
     * the flow. Call on the main thread.
     */
    fun launch(
        context: Context,
        config: SentinelConfig,
        listener: SentinelListener,
    ): SentinelSession {
        SentinelRuntime.setListener(listener)
        context.startActivity(VerificationActivity.intent(context, config))
        return object : SentinelSession {
            override fun dismiss() = SentinelRuntime.requestDismiss()
        }
    }
}
