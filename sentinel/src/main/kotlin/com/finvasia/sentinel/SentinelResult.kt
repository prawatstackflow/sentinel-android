package com.finvasia.sentinel

/** Terminal result of a verification flow. */
sealed interface SentinelResult {
    /** Verification approved (web outcome `approved`). */
    data object Approved : SentinelResult

    /** Verification rejected (web outcome `rejected`). */
    data object Rejected : SentinelResult

    /** Submitted, pending manual review (web outcome `under_review`). */
    data object UnderReview : SentinelResult

    /** User dismissed the flow before reaching a terminal outcome. */
    data object Cancelled : SentinelResult

    /** Unrecoverable error (page load / transport / runtime stream). */
    data class Failed(val message: String) : SentinelResult
}
