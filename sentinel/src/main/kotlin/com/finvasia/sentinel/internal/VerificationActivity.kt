package com.finvasia.sentinel.internal

import android.Manifest
import android.app.Activity
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
import com.finvasia.sentinel.SentinelResult
import com.finvasia.sentinel.SentinelTheme
import org.json.JSONObject

/**
 * Internal host activity. Loads the hosted verification runtime in a WebView and
 * bridges its lifecycle/result messages back to a [SentinelResult]. Not part of
 * the public API — launched only via [com.finvasia.sentinel.Sentinel.Contract].
 */
internal class VerificationActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var config: SentinelConfig
    private var terminalDelivered = false

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

        val parsed = readConfig(intent)
        if (parsed == null) {
            finishWith(SentinelResult.Failed("Missing Sentinel configuration"))
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
            if (webView.canGoBack()) webView.goBack() else finishWith(SentinelResult.Cancelled)
        }
    }

    override fun onDestroy() {
        // Torn down without a terminal outcome (e.g. system kill) → treat as
        // cancelled so the host always gets a result.
        if (!terminalDelivered) {
            setResult(Activity.RESULT_OK, resultIntent(SentinelResult.Cancelled))
        }
        webView.destroy()
        super.onDestroy()
    }

    private fun finishWith(result: SentinelResult) {
        if (terminalDelivered) return
        terminalDelivered = true
        setResult(Activity.RESULT_OK, resultIntent(result))
        finish()
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // ---- WebView -> native bridge (named "SentinelBridge"; see mobile contract) ----
    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(json: String) {
            val message = runCatching { JSONObject(json) }.getOrNull() ?: return
            when (message.optString("type")) {
                "complete" -> {
                    val result = when (message.optString("outcome")) {
                        "approved" -> SentinelResult.Approved
                        "rejected" -> SentinelResult.Rejected
                        else -> SentinelResult.UnderReview
                    }
                    runOnUiThread { finishWith(result) }
                }

                "error" -> {
                    val msg = message.optString("message").ifBlank { "Verification error" }
                    runOnUiThread { finishWith(SentinelResult.Failed(msg)) }
                }
                // "ready" is informational — no action.
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
                finishWith(SentinelResult.Failed("Failed to load verification: ${error.description}"))
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

        private const val EXTRA_RESULT_KIND = "sentinel.result.kind"
        private const val EXTRA_RESULT_MESSAGE = "sentinel.result.message"

        fun intent(context: Context, config: SentinelConfig): Intent =
            Intent(context, VerificationActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, config.sessionId)
                putExtra(EXTRA_SESSION_TOKEN, config.sessionToken)
                putExtra(EXTRA_BASE_URL, config.hostedFlowBaseUrl)
                putExtra(EXTRA_THEME, config.theme.name)
                putExtra(EXTRA_LOCALE, config.locale)
            }

        fun parseResult(intent: Intent?): SentinelResult {
            if (intent == null) return SentinelResult.Cancelled
            return when (intent.getStringExtra(EXTRA_RESULT_KIND)) {
                "approved" -> SentinelResult.Approved
                "rejected" -> SentinelResult.Rejected
                "under_review" -> SentinelResult.UnderReview
                "failed" -> SentinelResult.Failed(
                    intent.getStringExtra(EXTRA_RESULT_MESSAGE) ?: "Verification error",
                )
                else -> SentinelResult.Cancelled
            }
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

        private fun resultIntent(result: SentinelResult): Intent {
            val intent = Intent()
            val (kind, message) = when (result) {
                SentinelResult.Approved -> "approved" to null
                SentinelResult.Rejected -> "rejected" to null
                SentinelResult.UnderReview -> "under_review" to null
                SentinelResult.Cancelled -> "cancelled" to null
                is SentinelResult.Failed -> "failed" to result.message
            }
            intent.putExtra(EXTRA_RESULT_KIND, kind)
            message?.let { intent.putExtra(EXTRA_RESULT_MESSAGE, it) }
            return intent
        }
    }
}
