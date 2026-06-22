package com.finvasia.sentinel

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.finvasia.sentinel.internal.VerificationActivity

/**
 * Entry point for the Sentinel identity-verification flow.
 *
 * Register [Contract] with any `ActivityResultCaller` (Activity/Fragment) and
 * launch it with a [SentinelConfig]:
 *
 * ```kotlin
 * private val verify = registerForActivityResult(Sentinel.Contract()) { result ->
 *     when (result) {
 *         SentinelResult.Approved    -> /* ... */
 *         SentinelResult.Rejected    -> /* ... */
 *         SentinelResult.UnderReview -> /* ... */
 *         SentinelResult.Cancelled   -> /* ... */
 *         is SentinelResult.Failed   -> showError(result.message)
 *     }
 * }
 *
 * verify.launch(SentinelConfig(sessionId = "...", sessionToken = "..."))
 * ```
 */
object Sentinel {
    /** [ActivityResultContract] that runs the flow and returns a [SentinelResult]. */
    class Contract : ActivityResultContract<SentinelConfig, SentinelResult>() {
        override fun createIntent(context: Context, input: SentinelConfig): Intent =
            VerificationActivity.intent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): SentinelResult =
            VerificationActivity.parseResult(intent)
    }
}
