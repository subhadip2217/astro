package com.subhadip.aria;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int MEDIA_PERMISSION_REQUEST = 1002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1003;
    private static final String ARIA_URL = "https://aigf-wheat.vercel.app/";
    private static final String FCM_REGISTER_URL = "https://aigf-wheat.vercel.app/api/push/fcm";
    private static final String AUTH_SCHEME = "aria";
    private static final String NOTIFICATION_CHANNEL_ID = "aria_messages_v3";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;
    private String pendingAccessToken;
    private String pendingRefreshToken;
    private NotificationManager notificationManager;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.subhadip.aria.R.layout.activity_main);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        requestNotificationPermission();

        webView = findViewById(com.subhadip.aria.R.id.webview);
        configureWebView();
        webView.addJavascriptInterface(new AriaNotificationBridge(), "AriaAndroidNotifications");

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        if (!handleAuthCallback(getIntent()) && savedInstanceState == null) webView.loadUrl(ARIA_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack(); else finish();
            }
        });

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) return;
                    getSharedPreferences("aria_push", MODE_PRIVATE)
                            .edit().putString("fcm_token", task.getResult()).apply();
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Aria messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Messages and proactive notifications from Aria");
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audio = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, audio);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String notificationPermissionState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "granted";
        return notificationsGranted() ? "granted" : "default";
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void showAriaNotification(String title, String body, String id) {
        if (!notificationsGranted() || notificationManager == null) return;
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                id == null ? 0 : id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String safeBody = body == null ? "You have a new message from Aria." : body;
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(com.subhadip.aria.R.drawable.ic_stat_aria)
                .setContentTitle(title == null || title.isEmpty() ? "Aria 💕" : title)
                .setContentText(safeBody)
                .setStyle(new Notification.BigTextStyle().bigText(safeBody))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setVibrate(new long[]{0, 200, 100, 200});
        int notificationId = id == null ? (int) System.currentTimeMillis() : id.hashCode();
        notificationManager.notify(notificationId, builder.build());
    }

    private void registerFcmTokenWithBackend(String accessToken) {
        if (accessToken == null || accessToken.isEmpty()) return;
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            final String fcmToken = task.getResult();
            getSharedPreferences("aria_push", MODE_PRIVATE).edit().putString("fcm_token", fcmToken).apply();
            networkExecutor.execute(() -> {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(FCM_REGISTER_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    JSONObject body = new JSONObject();
                    body.put("token", fcmToken);
                    body.put("platform", "android");
                    byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
                    conn.getResponseCode();
                } catch (Exception ignored) {
                } finally { if (conn != null) conn.disconnect(); }
            });
        });
    }

    public final class AriaNotificationBridge {
        @JavascriptInterface public String getNotificationPermission() { return notificationPermissionState(); }
        @JavascriptInterface public String requestNotificationPermission() {
            MainActivity.this.requestNotificationPermission();
            return notificationPermissionState();
        }
        @JavascriptInterface public void showNotification(String title, String body, String id) {
            runOnUiThread(() -> showAriaNotification(title, body, id));
        }
        @JavascriptInterface public void registerPushToken(String accessToken) {
            if (accessToken == null || accessToken.isEmpty()) return;
            registerFcmTokenWithBackend(accessToken);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleAuthCallback(intent)) webView.loadUrl(ARIA_URL);
    }

    private boolean handleAuthCallback(Intent intent) {
        if (intent == null || intent.getData() == null) return false;
        Uri uri = intent.getData();
        if (!AUTH_SCHEME.equalsIgnoreCase(uri.getScheme())) return false;
        String fragment = uri.getFragment();
        if (fragment == null || fragment.isEmpty()) return false;
        Uri tokenUri = Uri.parse("https://aria.invalid/?" + fragment);
        pendingAccessToken = tokenUri.getQueryParameter("access_token");
        pendingRefreshToken = tokenUri.getQueryParameter("refresh_token");
        if (pendingAccessToken == null || pendingRefreshToken == null) return false;
        registerFcmTokenWithBackend(pendingAccessToken);
        webView.loadUrl(ARIA_URL);
        return true;
    }

    private void injectPendingSession() {
        if (pendingAccessToken == null || pendingRefreshToken == null) return;
        try {
            String accessJson = JSONObject.quote(pendingAccessToken);
            String refreshJson = JSONObject.quote(pendingRefreshToken);
            String js = "(async()=>{if(window.__ARIA_SET_AUTH){return await window.__ARIA_SET_AUTH(" + accessJson + "," + refreshJson + ");}return false;})()";
            webView.evaluateJavascript(js, value -> {
                pendingAccessToken = null;
                pendingRefreshToken = null;
            });
        } catch (Exception ignored) {}
    }

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(false);
        webView.getSettings().setBuiltInZoomControls(false);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setTextZoom(100);
        webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " AriaAndroid/1.2");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectPendingSession();
                view.evaluateJavascript(
                        "(async()=>{try{if(window.__ARIA_GET_ACCESS_TOKEN){const t=await window.__ARIA_GET_ACCESS_TOKEN();if(t&&window.AriaAndroidNotifications){window.AriaAndroidNotifications.registerPushToken(t);}}}catch(e){}})()",
                        null
                );
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AUTH_SCHEME.equalsIgnoreCase(uri.getScheme())) { handleAuthCallback(new Intent(Intent.ACTION_VIEW, uri)); return true; }
                String host = uri.getHost();
                if (host != null && (host.equals("aigf-wheat.vercel.app") || host.endsWith(".vercel.app"))) { view.loadUrl(uri.toString()); return true; }
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) MainActivity.this.filePathCallback.onReceiveValue(null);
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try { startActivityForResult(intent, FILE_CHOOSER_REQUEST); }
                catch (Exception e) { MainActivity.this.filePathCallback = null; return false; }
                return true;
            }
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean needsCamera = false, needsMic = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) needsCamera = true;
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) needsMic = true;
                    }
                    boolean cameraGranted = !needsCamera || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    boolean micGranted = !needsMic || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    if (cameraGranted && micGranted) request.grant(request.getResources());
                    else {
                        pendingPermissionRequest = request;
                        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, MEDIA_PERMISSION_REQUEST);
                    }
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle outState) { webView.saveState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { if (webView != null) webView.destroy(); networkExecutor.shutdownNow(); super.onDestroy(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            boolean allGranted = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) allGranted = false;
            if (allGranted) pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            else pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
    }
}
