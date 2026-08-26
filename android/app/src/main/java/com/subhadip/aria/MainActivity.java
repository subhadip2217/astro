package com.subhadip.aria;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int MEDIA_PERMISSION_REQUEST = 1002;
    private static final String ARIA_URL = "https://aigf-wheat.vercel.app/";
    private static final String AUTH_SCHEME = "aria";

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;
    private String pendingAccessToken;
    private String pendingRefreshToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.subhadip.aria.R.layout.activity_main);

        webView = findViewById(com.subhadip.aria.R.id.webview);
        configureWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }
        if (!handleAuthCallback(getIntent()) && savedInstanceState == null) {
            webView.loadUrl(ARIA_URL);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!handleAuthCallback(intent)) {
            webView.loadUrl(ARIA_URL);
        }
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

        if (pendingAccessToken == null || pendingRefreshToken == null) {
            return false;
        }

        // Load a clean HTTPS page. The tokens are handed directly to
        // supabase-js through the Android/WebView bridge after the page loads.
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
        } catch (Exception ignored) {
            // Retry on the next page load if the bridge is not available yet.
        }
    }

    private void configureWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setUserAgentString(
                webView.getSettings().getUserAgentString() + " AriaAndroid/1.0"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectPendingSession();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AUTH_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                    handleAuthCallback(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                }

                String host = uri.getHost();
                if (host != null && (host.equals("aigf-wheat.vercel.app") || host.endsWith(".vercel.app"))) {
                    view.loadUrl(uri.toString());
                    return true;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    // Ignore URLs for which Android has no external handler.
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                              ValueCallback<Uri[]> filePathCallback,
                                              FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean needsCamera = false;
                    boolean needsMic = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) needsCamera = true;
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) needsMic = true;
                    }

                    boolean cameraGranted = !needsCamera || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    boolean micGranted = !needsMic || ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

                    if (cameraGranted && micGranted) {
                        request.grant(request.getResources());
                    } else {
                        pendingPermissionRequest = request;
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                                MEDIA_PERMISSION_REQUEST);
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST && pendingPermissionRequest != null) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
            } else {
                pendingPermissionRequest.deny();
            }
            pendingPermissionRequest = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
