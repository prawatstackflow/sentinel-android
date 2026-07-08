package com.finvasia.sentinel

/**
 * Terminal outcome reported by the hosted flow on a [SentinelEvent.Completed].
 *
 * `APPROVED | REJECTED | UNDER_REVIEW` are the scored outcomes a main
 * verification flow resolves to; `COMPLETED` is the neutral "done" state a
 * data-collection (named) flow reports, which makes no decision.
 */
enum class SentinelOutcome {
    APPROVED,
    REJECTED,
    UNDER_REVIEW,
    COMPLETED,
}

/**
 * A status update reported by the verification flow.
 *
 * The SDK forwards these to the host's [SentinelListener] and **never closes
 * itself** in response. The host decides when to tear down by calling
 * [SentinelSession.dismiss]. The only self-teardown is the system-back gesture,
 * which emits [Cancelled] alongside finishing, so the user is never trapped.
 */
sealed interface SentinelEvent {
    /** The hosted runtime mounted. */
    data object Ready : SentinelEvent

    /** The flow reached a terminal outcome (see [SentinelOutcome]). */
    data class Completed(val outcome: SentinelOutcome) : SentinelEvent

    /**
     * The user confirmed the in-flow "Exit Onboarding" dialog, or dismissed via
     * the system-back gesture.
     */
    data object Cancelled : SentinelEvent

    /**
     * The user tapped "Done" on the terminal outcome screen after the flow
     * finished. Distinct from [Cancelled] (which means the user abandoned the
     * flow): here the flow completed and the user acknowledged the outcome. The
     * host should dismiss on this — the SDK keeps the WebView open on `complete`
     * so the user can read the outcome, and closes only when they choose to.
     */
    data object Closed : SentinelEvent

    /** The web runtime reported an unrecoverable error. */
    data class Error(val message: String) : SentinelEvent

    /**
     * The WebView failed to load (page-load / transport failure). SDK-level, not
     * a web outcome.
     */
    data class LoadFailed(val message: String) : SentinelEvent
}
