package com.finvasia.sentinel.demo

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.finvasia.sentinel.Sentinel
import com.finvasia.sentinel.SentinelConfig
import com.finvasia.sentinel.SentinelEvent
import com.finvasia.sentinel.SentinelOutcome
import com.finvasia.sentinel.SentinelSession

/**
 * Minimal harness for testing the Sentinel SDK on a device/emulator. Two ways
 * to start a flow:
 *
 *  1. "Start Demo Session" — mints a fresh session against the configured
 *     sandbox API (DEMO_API_KEY / DEMO_API_BASE_URL from local.properties),
 *     then launches the SDK. One tap, no manual token wrangling.
 *  2. Manual — paste a sessionId + sessionToken (minted out-of-band by your
 *     backend's POST /sessions) and launch.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var resultView: TextView

    // Held so the demo can close the flow when *it* decides — the SDK no longer
    // self-closes on a status event.
    private var session: SentinelSession? = null

    private fun launch(config: SentinelConfig) {
        session = Sentinel.launch(this, config) { event ->
            when (event) {
                SentinelEvent.Ready -> resultView.text = "Status: ready"
                is SentinelEvent.Completed -> {
                    // Show the outcome but DO NOT dismiss — the SDK keeps the WebView
                    // open on the outcome screen so the user can read it. The host
                    // dismisses only when the user taps "Done" (Closed), below.
                    resultView.text = when (event.outcome) {
                        SentinelOutcome.APPROVED -> "Result: APPROVED"
                        SentinelOutcome.REJECTED -> "Result: REJECTED"
                        SentinelOutcome.UNDER_REVIEW -> "Result: UNDER REVIEW"
                        SentinelOutcome.COMPLETED -> "Result: COMPLETED"
                    }
                }
                SentinelEvent.Closed -> {
                    // User tapped "Done" on the outcome screen — now close.
                    session?.dismiss()
                }
                SentinelEvent.Cancelled -> {
                    resultView.text = "Result: cancelled"
                    session?.dismiss()
                }
                is SentinelEvent.Error -> {
                    resultView.text = "Result: error — ${event.message}"
                    session?.dismiss()
                }
                is SentinelEvent.LoadFailed -> {
                    resultView.text = "Result: load failed — ${event.message}"
                    session?.dismiss()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (resources.displayMetrics.density * 16).toInt()
        val gap = (resources.displayMetrics.density * 8).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        resultView = TextView(this).apply { text = "Result: —" }

        // --- 1. One-tap demo session --------------------------------------
        val demoStatus = TextView(this)
        val demoButton = Button(this).apply {
            text = "Start Demo Session"
            setOnClickListener {
                if (BuildConfig.DEMO_API_KEY.isBlank()) {
                    demoStatus.text = "Set DEMO_API_KEY in local.properties to use this."
                    return@setOnClickListener
                }
                isEnabled = false
                demoStatus.text = "Creating session…"
                DemoSessionService.createSession(
                    apiBaseUrl = BuildConfig.DEMO_API_BASE_URL,
                    apiKey = BuildConfig.DEMO_API_KEY,
                    onSuccess = { session ->
                        isEnabled = true
                        demoStatus.text = "Session ${session.sessionId.take(8)}… created"
                        launch(
                            SentinelConfig(
                                sessionId = session.sessionId,
                                sessionToken = session.sessionToken,
                                hostedFlowBaseUrl = BuildConfig.DEMO_HOSTED_FLOW_BASE_URL,
                            ),
                        )
                    },
                    onError = { message ->
                        isEnabled = true
                        demoStatus.text = "Error: $message"
                    },
                )
            }
        }
        if (BuildConfig.DEMO_API_KEY.isBlank()) {
            demoStatus.text = "DEMO_API_KEY not set — add it to local.properties."
        }

        // --- 2. Manual session entry --------------------------------------
        val manualLabel = TextView(this).apply { text = "— or enter a session manually —" }
        val sessionIdInput = EditText(this).apply { hint = "sessionId" }
        val tokenInput = EditText(this).apply {
            hint = "sessionToken"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val baseUrlInput = EditText(this).apply {
            hint = "hostedFlowBaseUrl (optional)"
            setText(BuildConfig.DEMO_HOSTED_FLOW_BASE_URL)
        }
        val startButton = Button(this).apply {
            text = "Start verification"
            setOnClickListener {
                val sessionId = sessionIdInput.text.toString().trim()
                val token = tokenInput.text.toString().trim()
                if (sessionId.isEmpty() || token.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "sessionId and sessionToken are required",
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@setOnClickListener
                }
                val baseUrl = baseUrlInput.text.toString().trim()
                    .ifEmpty { SentinelConfig.DEFAULT_HOSTED_FLOW_BASE_URL }
                launch(
                    SentinelConfig(
                        sessionId = sessionId,
                        sessionToken = token,
                        hostedFlowBaseUrl = baseUrl,
                    ),
                )
            }
        }

        val wrap = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = gap }
        root.addView(demoButton, wrap)
        root.addView(demoStatus, wrap)
        root.addView(manualLabel, wrap)
        root.addView(sessionIdInput, wrap)
        root.addView(tokenInput, wrap)
        root.addView(baseUrlInput, wrap)
        root.addView(startButton, wrap)
        root.addView(resultView, wrap)

        setContentView(root)
    }
}
