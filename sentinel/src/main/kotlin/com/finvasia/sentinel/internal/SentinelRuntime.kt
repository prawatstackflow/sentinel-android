package com.finvasia.sentinel.internal

import android.os.Handler
import android.os.Looper
import com.finvasia.sentinel.SentinelEvent
import com.finvasia.sentinel.SentinelListener
import java.lang.ref.WeakReference

/**
 * Process-wide bridge between the host's [SentinelListener] and the running
 * [VerificationActivity]. An Activity can't hold the caller's lambda across the
 * Intent boundary, so [com.finvasia.sentinel.Sentinel.launch] parks the listener
 * here and the activity forwards events through it.
 *
 * Only one verification runs at a time (the flow is a full-screen modal), so a
 * single slot is sufficient. The listener and the activity ref are cleared on
 * teardown to avoid leaks.
 */
internal object SentinelRuntime {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: SentinelListener? = null
    private var activityRef: WeakReference<VerificationActivity>? = null

    /** Parks the host listener for the flow about to launch. */
    fun setListener(listener: SentinelListener) {
        this.listener = listener
    }

    /** Activity registers itself so [requestDismiss] can finish it. */
    fun bind(activity: VerificationActivity) {
        activityRef = WeakReference(activity)
    }

    /** Activity clears its registration on teardown. */
    fun unbind(activity: VerificationActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            listener = null
        }
    }

    /** Forwards an event to the host listener on the main thread. */
    fun emit(event: SentinelEvent) {
        val target = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.onEvent(event)
        } else {
            mainHandler.post { target.onEvent(event) }
        }
    }

    /** Host asked to close the flow — finish the activity if still around. */
    fun requestDismiss() {
        val activity = activityRef?.get() ?: return
        mainHandler.post { activity.finish() }
    }
}
