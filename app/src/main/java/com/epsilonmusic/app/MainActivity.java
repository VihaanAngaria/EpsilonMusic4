package com.epsilonmusic.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialException;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://epsilonmusic.space-z.ai";
    private EpsilonWebView webView;
    private CredentialManager credentialManager;
    private static WeakReference<WebView> webViewRef = new WeakReference<>(null);

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        credentialManager = CredentialManager.create(this);

        webView = new EpsilonWebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        configureWebView(webView);

        webView.addJavascriptInterface(new AndroidBridge(this), "EpsilonAndroid");

        webViewRef = new WeakReference<>(webView);
        PlaybackService.setWebView(webView);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        if (savedInstanceState == null) webView.loadUrl(HOME_URL);
        else webView.restoreState(savedInstanceState);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView view) {
        WebSettings s = view.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/116.0.0.0 Mobile Safari/537.36"
        );

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(view, true);

        view.setBackgroundColor(Color.BLACK);
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient());
    }

    private void nativeGoogleSignIn() {
        runOnUiThread(() -> {
        try {
                GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .setAutoSelectEnabled(false)
                        .build();
    
                GetCredentialRequest request = new GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build();
    
                credentialManager.getCredentialAsync(
                        this,
                        request,
                        null,
                        command -> runOnUiThread(command),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override
                            public void onResult(@NonNull GetCredentialResponse result) {
                                Credential credential = result.getCredential();
    
                                if (credential instanceof CustomCredential) {
                                    CustomCredential custom = (CustomCredential) credential;
    
                                    if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                            .equals(custom.getType())) {
                                        try {
                                            GoogleIdTokenCredential googleCredential =
                                                    GoogleIdTokenCredential.createFrom(custom.getData());
    
                                            String token = googleCredential.getIdToken();
                                            WebView w = webViewRef.get();
                                            if (w != null) {
                                                w.evaluateJavascript(
                                                        "window.__epsilonGoogleNativeSuccess && " +
                                                        "window.__epsilonGoogleNativeSuccess(" +
                                                        quote(token) + ");",
                                                        null
                                                );
                                            }
                                            return;
                                        } catch (Exception e) {
                                            sendGoogleError(e.getMessage());
                                            return;
                                        }
                                    }
                                }
    
                                sendGoogleError("Google did not return a Google ID credential");
                            }
    
                            @Override
                            public void onError(@NonNull GetCredentialException e) {
                                sendGoogleError(e.getMessage());
                            }
                        }
                );
            } catch (Exception e) {
                sendGoogleError(e.getMessage());
            }
        });
    }

    private void sendGoogleError(String message) {
        runOnUiThread(() -> {
            WebView w = webViewRef.get();
            if (w != null) {
                w.evaluateJavascript(
                    "window.__epsilonGoogleNativeError && " +
                    "window.__epsilonGoogleNativeError(" + quote(message) + ");",
                    null
                );
            }
        });
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026") + "\"";
    }

    public static WebView getWebView() {
        return webViewRef.get();
    }

    @Override
    protected void onPause() {
        // Keep the proven background-playback behavior.
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        PlaybackService.setWebView(null);
        webViewRef.clear();
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static final class AndroidBridge {
        private final MainActivity activity;
        AndroidBridge(MainActivity activity) { this.activity = activity; }

        @JavascriptInterface
        public void googleSignIn() {
            activity.nativeGoogleSignIn();
        }

        @JavascriptInterface
        public void media(
                String title,
                String artist,
                String album,
                String artwork,
                double duration,
                double position,
                boolean playing
        ) {
            PlaybackService.updateMetadata(
                    activity,
                    title,
                    artist,
                    album,
                    artwork,
                    duration,
                    position,
                    playing
            );
        }

        @JavascriptInterface
        public void mediaStopped() {
            PlaybackService.stopPlayback(activity);
        }
    }

    private static final class EpsilonWebView extends WebView {
        EpsilonWebView(Context context) { super(context); }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            if (visibility == View.GONE) return;
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
