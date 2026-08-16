package com.epsilonmusic.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://epsilonmusic.space-z.ai";
    private static final String TAG = "EpsilonGoogleAuth";
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


        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(view, true);

        view.setBackgroundColor(Color.BLACK);
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient());
    }

    private void nativeGoogleSignIn() {
        runOnUiThread(() -> {
            Log.i(TAG, "Credential Manager launched");
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
                                handleGoogleCredential(result);
                            }

                            @Override
                            public void onError(@NonNull GetCredentialException e) {
                                handleGoogleError(e);
                            }
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "Credential Manager request failed", e);
                dispatchGoogleError("google-sign-in-failed", "Google sign-in could not be started.");
            }
        });
    }

    private void handleGoogleCredential(@NonNull GetCredentialResponse result) {
        Log.i(TAG, "Credential received");
        Credential credential = result.getCredential();

        if (!(credential instanceof CustomCredential)) {
            Log.e(TAG, "Unexpected credential type: " + credential.getType());
            dispatchGoogleError(
                    "unexpected-credential-type",
                    "Google did not return a Google ID credential.");
            return;
        }

        CustomCredential custom = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(custom.getType())) {
            Log.e(TAG, "Credential type: " + custom.getType());
            dispatchGoogleError(
                    "unexpected-credential-type",
                    "Google did not return a Google ID credential.");
            return;
        }
        Log.i(TAG, "Credential type: " + GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL);

        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(custom.getData());

            String idToken = googleCredential.getIdToken();
            if (idToken == null || idToken.isEmpty()) {
                Log.e(TAG, "Google ID token is empty");
                dispatchGoogleError("empty-id-token", "Google did not return a valid ID token.");
                return;
            }

            Log.i(TAG, "Google ID token received (token value never logged)");
            dispatchGoogleSuccess(idToken);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse Google ID credential", e);
            dispatchGoogleError("invalid-id-credential", "Google returned an invalid credential.");
        }
    }

    private void handleGoogleError(@NonNull GetCredentialException e) {
        if (e instanceof GetCredentialCancellationException) {
            Log.i(TAG, "Google sign-in cancelled by user");
            dispatchGoogleError("auth/cancelled", null);
            return;
        }
        Log.e(TAG, "Google sign-in failed: " + e.getClass().getSimpleName());
        dispatchGoogleError("google-sign-in-failed", "Google sign-in failed. Please try again.");
    }

    private void dispatchGoogleSuccess(String idToken) {
        // Must be delivered with evaluateJavascript() on the UI thread.
        runOnUiThread(() -> {
            WebView w = webViewRef.get();
            if (w != null) {
                w.evaluateJavascript(
                        "window.__epsilonGoogleNativeSuccess && " +
                        "window.__epsilonGoogleNativeSuccess(" +
                        quote(idToken) + ");",
                        null
                );
                Log.i(TAG, "JavaScript success callback dispatched");
            } else {
                Log.w(TAG, "WebView unavailable; cannot dispatch Google ID token");
            }
        });
    }

    private void dispatchGoogleError(String errorCode, String userMessage) {
        runOnUiThread(() -> {
            WebView w = webViewRef.get();
            if (w != null) {
                w.evaluateJavascript(
                        "window.__epsilonGoogleNativeError && " +
                        "window.__epsilonGoogleNativeError(" + quote(errorCode) + ");",
                        null
                );
            } else {
                Log.w(TAG, "WebView unavailable; could not dispatch native sign-in error");
            }
            if (userMessage != null) {
                Toast.makeText(MainActivity.this, userMessage, Toast.LENGTH_LONG).show();
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
                .replace("&", "\\u0026")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029") + "\"";
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
        public boolean signInWithGoogle() {
            // The Epsilon Music web app checks `typeof EpsilonAndroid.signInWithGoogle`
            // and only skips its own Firebase popup/redirect when this returns true.
            activity.nativeGoogleSignIn();
            return true;
        }

        @JavascriptInterface
        public void googleSignIn() {
            // Compat alias for older cached web pages.
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
