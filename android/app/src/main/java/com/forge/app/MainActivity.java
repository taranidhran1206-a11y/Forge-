package com.forge.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String CHANNEL_ID = "forge_notifications";
    private static final int NOTIF_PERMISSION_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen, no action bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Dark status/nav bars
        getWindow().setStatusBarColor(0xFF0A0A0A);
        getWindow().setNavigationBarColor(0xFF0A0A0A);

        // Keep screen on during use
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Create notification channel
        createNotificationChannel();

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIF_PERMISSION_CODE);
            }
        }

        // Setup WebView
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0A0A0A);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Add native bridge for notifications and vibration
        webView.addJavascriptInterface(new ForgeBridge(), "ForgeBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Allow Gemini API calls to pass through
                if (url.contains("generativelanguage.googleapis.com")) {
                    return false;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // Load the app
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Forge Notifications";
            String description = "Focus tracking nudges and break reminders";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // JavaScript interface for native features
    public class ForgeBridge {

        @JavascriptInterface
        public void sendNotification(String title, String body) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(MainActivity.this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

            NotificationManagerCompat notifManager = NotificationManagerCompat.from(MainActivity.this);
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notifManager.notify((int) System.currentTimeMillis(), builder.build());
            }
        }

        @JavascriptInterface
        public void vibrate(long milliseconds) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(milliseconds);
                }
            }
        }

        @JavascriptInterface
        public void vibratePattern(long[] pattern) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    v.vibrate(pattern, -1);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.evaluateJavascript(
            "if(typeof document!=='undefined')document.dispatchEvent(new Event('visibilitychange'));",
            null
        );
    }
}
