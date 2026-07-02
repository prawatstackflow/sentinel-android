package com.finvasia.sentinel.internal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.finvasia.sentinel.SentinelConfig
import com.finvasia.sentinel.SentinelEvent
import com.finvasia.sentinel.SentinelOutcome
import com.finvasia.sentinel.SentinelTheme
import org.json.JSONObject

/**
 * Internal host activity. Loads the hosted verification runtime in a WebView and
 * forwards its lifecycle/status messages to the host as [SentinelEvent]s (via
 * [SentinelRuntime]). Not part of the public API — launched only via
 * [com.finvasia.sentinel.Sentinel.launch].
 */
internal class VerificationActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var config: SentinelConfig

    // Whether a terminal-ish event (Completed/Error/Cancelled/LoadFailed) has
    // already been reported, so onDestroy doesn't emit a spurious Cancelled.
    private var terminalEmitted = false

    private var pendingCameraPermissionRequest: PermissionRequest? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingCameraPermissionRequest ?: return@registerForActivityResult
            pendingCameraPermissionRequest = null
            if (granted) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                request.deny()
            }
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            callback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw the WebView under the status / navigation bars so the hosted flow is
        // truly full-screen and Chromium reports the real system-bar insets to the
        // page as `env(safe-area-inset-*)` (the page opts in via viewport-fit=cover).
        // Without this, Android keeps the WebView within the bars and those insets
        // resolve to 0, making the web-side safe-area padding a no-op. (Mandatory
        // once targetSdk reaches 35 / Android 15, which forces edge-to-edge.)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        SentinelRuntime.bind(this)

        val parsed = readConfig(intent)
        if (parsed == null) {
            // Caller error — nothing to render. Report it and close: there is no
            // WebView to keep open for the host to inspect.
            emit(SentinelEvent.LoadFailed("Missing Sentinel configuration"))
            finish()
            return
        }
        config = parsed

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
            }
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            webViewClient = RuntimeWebViewClient(Uri.parse(config.hostedFlowBaseUrl))
            webChromeClient = RuntimeChromeClient()
        }
        setContentView(webView)
        webView.loadUrl(buildUrl(config))

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                // Escape hatch: report cancelled AND finish, so the user is never
                // trapped even if the host doesn't close on the event.
                emit(SentinelEvent.Cancelled)
                finish()
            }
        }
    }

    override fun onDestroy() {
        // Torn down while finishing without a terminal event (e.g. system kill) →
        // report cancelled so the host always hears about it. Guarded on
        // isFinishing so a config-change recreate (rotation) doesn't misfire.
        if (isFinishing && !terminalEmitted) {
            emit(SentinelEvent.Cancelled)
        }
        SentinelRuntime.unbind(this)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    /**
     * Forwards a status update to the host. Never finishes the activity — closing
     * is the host's decision (via [SentinelSession.dismiss]) or the system-back
     * escape hatch.
     */
    private fun emit(event: SentinelEvent) {
        if (event !is SentinelEvent.Ready) terminalEmitted = true
        SentinelRuntime.emit(event)
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // ---- WebView -> native bridge (named "SentinelBridge"; see mobile contract) ----
    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            val message = runCatching { JSONObject(json) }.getOrNull() ?: return
            when (message.optString("type")) {
                "ready" -> runOnUiThread { emit(SentinelEvent.Ready) }

                "complete" -> {
                    val outcome = when (message.optString("outcome")) {
                        "approved" -> SentinelOutcome.APPROVED
                        "rejected" -> SentinelOutcome.REJECTED
                        "completed" -> SentinelOutcome.COMPLETED
                        else -> SentinelOutcome.UNDER_REVIEW
                    }
                    runOnUiThread { emit(SentinelEvent.Completed(outcome)) }
                }

                "error" -> {
                    val msg = message.optString("message").ifBlank { "Verification error" }
                    runOnUiThread { emit(SentinelEvent.Error(msg)) }
                }

                "cancel" -> {
                    // User confirmed the in-flow "Exit Onboarding" dialog. Report it
                    // and let the host close — the SDK no longer finishes here.
                    runOnUiThread { emit(SentinelEvent.Cancelled) }
                }
            }
        }
    }

    // ---- Camera permission forwarding + file picker ----
    private inner class RuntimeChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            val wantsCamera =
                request.resources.any { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
            if (!wantsCamera) {
                request.deny()
                return
            }
            if (hasCameraPermission()) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                pendingCameraPermissionRequest = request
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            // Document upload via <input type=file>. Camera-capture for file
            // inputs (vs. the gallery/files picker below) is a known follow-up;
            // live liveness uses getUserMedia and is unaffected.
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            return try {
                fileChooserLauncher.launch(fileChooserParams.createIntent())
                true
            } catch (_: Exception) {
                fileChooserCallback = null
                false
            }
        }
    }

    // ---- Navigation + load-error handling ----
    private inner class RuntimeWebViewClient(private val base: Uri) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val url = request.url
            val sameOrigin = url.scheme == base.scheme && url.host == base.host
            if (sameOrigin) return false
            // Foreign links (terminal CTA, external policy pages) open in the
            // system browser instead of navigating the verification WebView.
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
            return true
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                emit(SentinelEvent.LoadFailed("Failed to load verification: ${error.description}"))
            }
        }
    }

    private fun buildUrl(config: SentinelConfig): String {
        val builder = Uri.parse(config.hostedFlowBaseUrl).buildUpon()
            .appendPath("verification")
            .appendPath(config.sessionId)
            .appendQueryParameter("token", config.sessionToken)
            .appendQueryParameter("platform", "native")
        resolveTheme(config.theme)?.let { builder.appendQueryParameter("theme", it) }
        config.locale?.let { builder.appendQueryParameter("locale", it) }
        return builder.build().toString()
    }

    private fun resolveTheme(theme: SentinelTheme): String? = when (theme) {
        SentinelTheme.LIGHT -> "light"
        SentinelTheme.DARK -> "dark"
        SentinelTheme.SYSTEM -> {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (night == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        }
    }

    companion object {
        private const val BRIDGE_NAME = "SentinelBridge"

        private const val EXTRA_SESSION_ID = "sentinel.sessionId"
        private const val EXTRA_SESSION_TOKEN = "sentinel.sessionToken"
        private const val EXTRA_BASE_URL = "sentinel.hostedFlowBaseUrl"
        private const val EXTRA_THEME = "sentinel.theme"
        private const val EXTRA_LOCALE = "sentinel.locale"

        fun intent(context: Context, config: SentinelConfig): Intent =
            Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, config.sessionId)
                putExtra(EXTRA_SESSION_TOKEN, config.sessionToken)
                putExtra(EXTRA_BASE_URL, config.hostedFlowBaseUrl)
                putExtra(EXTRA_THEME, config.theme.name)
                putExtra(EXTRA_LOCALE, config.locale)
            }

        private fun readConfig(intent: Intent): SentinelConfig? {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return null
            val sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN) ?: return null
            val baseUrl = intent.getStringExtra(EXTRA_BASE_URL)
                ?: SentinelConfig.DEFAULT_HOSTED_FLOW_BASE_URL
            val theme = intent.getStringExtra(EXTRA_THEME)
                ?.let { runCatching { SentinelTheme.valueOf(it) }.getOrNull() }
                ?: SentinelTheme.SYSTEM
            return SentinelConfig(
                sessionId = sessionId,
                sessionToken = sessionToken,
                hostedFlowBaseUrl = baseUrl,
                theme = theme,
                locale = intent.getStringExtra(EXTRA_LOCALE),
            )
        }
    }
}
