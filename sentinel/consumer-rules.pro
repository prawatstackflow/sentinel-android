# The WebView <-> native bridge is called from JavaScript by name, so its
# @JavascriptInterface members must survive R8/ProGuard in consuming apps.
-keepclassmembers class com.finvasia.sentinel.internal.VerificationActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
