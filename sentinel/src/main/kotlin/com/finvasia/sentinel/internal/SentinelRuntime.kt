package com.finvasia.sentinel.internal

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.finvasia.sentinel.LiveChatRequest
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
    private const val TAG = "Sentinel"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var listener: SentinelListener? = null
    private var onLiveChat: ((LiveChatRequest) -> Unit)? = null
    private var activityRef: WeakReference<VerificationActivity>? = null

    /** Parks the host listener for the flow about to launch. */
    fun setListener(listener: SentinelListener) {
        this.listener = listener
    }

    /** Parks the host's optional LiveChat handler for the flow about to launch. */
    fun setLiveChatHandler(handler: ((LiveChatRequest) -> Unit)?) {
        this.onLiveChat = handler
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
            onLiveChat = null
        }
    }

    /** Forwards an event to the host listener on the main thread. */
    fun emit(event: SentinelEvent) {
        Log.i(TAG, "event: $event")
        val target = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.onEvent(event)
        } else {
            mainHandler.post { target.onEvent(event) }
        }
    }

    /**
     * Forwards a non-terminal LiveChat request to the host's [onLiveChat] handler on
     * the main thread. Not a [SentinelEvent] — never dismisses the flow.
     */
    fun emitLiveChat(request: LiveChatRequest) {
        // Debug log — non-PII only (never log customerName/customerEmail).
        Log.i(
            TAG,
            "live_chat request: license=${request.license} group=${request.group} " +
                "forwardPii=${request.forwardPii} vars=${request.sessionVariables.size} " +
                "handler=${onLiveChat != null}",
        )
        val target = onLiveChat ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target(request)
        } else {
            mainHandler.post { target(request) }
        }
    }

    /** Host asked to close the flow — finish the activity if still around. */
    fun requestDismiss() {
        val activity = activityRef?.get() ?: return
        mainHandler.post { activity.finish() }
    }
}
