# Keep WebView JavaScript interface
-keepclassmembers class com.forge.app.MainActivity$ForgeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
