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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private static final String HOME_URL = "https://epsilonmusic.vercel.app";
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
        Log.i(TAG, "[nativeGoogleSignIn] called; scheduling Credential Manager launch on the UI thread");
        runOnUiThread(() -> {
            Log.i(TAG, "[nativeGoogleSignIn] Credential Manager launch starting on UI thread");
            logWebViewState("nativeGoogleSignIn");
            try {
                String serverClientId = getString(R.string.default_web_client_id);
                boolean clientIdPresent = serverClientId != null && !serverClientId.isEmpty();
                boolean clientIdSeemsValid = isPlausibleGoogleClientId(serverClientId);
                Log.i(TAG, "[nativeGoogleSignIn] serverClientId present=" + clientIdPresent
                        + " plausibleFormat=" + clientIdSeemsValid
                        + " (client ID value never logged)");

                // Explicit "Sign in with Google" button flow.
                // googleid:1.1.1 requires the server client ID in the Builder constructor.
                GetSignInWithGoogleOption googleOption =
                        new GetSignInWithGoogleOption.Builder(serverClientId)
                                .build();

                GetCredentialRequest request = new GetCredentialRequest.Builder()
                        .addCredentialOption(googleOption)
                        .build();

                Log.i(TAG, "[nativeGoogleSignIn] launching explicit Google Sign-In flow");
                credentialManager.getCredentialAsync(
                        this,
                        request,
                        null,
                        command -> runOnUiThread(command),
                        new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                            @Override
                            public void onResult(@NonNull GetCredentialResponse result) {
                                Log.i(TAG, "[getCredentialAsync] onResult() -> request succeeded");
                                handleGoogleCredential(result);
                            }

                            @Override
                            public void onError(@NonNull GetCredentialException e) {
                                Log.i(TAG, "[getCredentialAsync] onError() invoked");
                                handleGoogleError(e);
                            }
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "[nativeGoogleSignIn] exception while launching Credential Manager request. "
                        + "class=" + e.getClass().getName());
                showGoogleErrorToast(e.getClass().getSimpleName());
                dispatchGoogleError("google-sign-in-failed", null);
            }
        });
    }

    private void handleGoogleCredential(@NonNull GetCredentialResponse result) {
        Log.i(TAG, "[handleGoogleCredential] getCredential request SUCCEEDED");
        Credential credential = result.getCredential();
        String credentialType = credential.getType();

        if (!(credential instanceof CustomCredential)) {
            Log.e(TAG, "[handleGoogleCredential] unexpected credential type: " + credentialType);
            dispatchGoogleError("unexpected-credential-type",
                    "Google did not return a Google ID credential.");
            return;
        }

        CustomCredential custom = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(custom.getType())) {
            Log.e(TAG, "[handleGoogleCredential] credential type is not a Google ID token: "
                    + custom.getType());
            dispatchGoogleError("unexpected-credential-type",
                    "Google did not return a Google ID credential.");
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(custom.getData());
            String idToken = googleCredential.getIdToken();
            if (idToken == null || idToken.isEmpty()) {
                dispatchGoogleError("empty-id-token", "Google did not return a valid ID token.");
                return;
            }
            dispatchGoogleSuccess(idToken);
        } catch (Exception e) {
            Log.e(TAG, "[handleGoogleCredential] failed to parse Google ID credential. class="
                    + e.getClass().getName());
            showGoogleErrorToast(e.getClass().getSimpleName());
            dispatchGoogleError("invalid-id-credential", null);
        }
    }

    private void handleGoogleError(@NonNull GetCredentialException e) {
        if (e instanceof GetCredentialCancellationException) {
            dispatchGoogleError("auth/cancelled", null);
            return;
        }
        Log.e(TAG, "[handleGoogleError] getCredential failed. class=" + e.getClass().getName());
        showGoogleErrorToast(e.getClass().getSimpleName());
        dispatchGoogleError("google-sign-in-failed", null);
    }

    private void dispatchGoogleSuccess(String idToken) {
        runOnUiThread(() -> {
            WebView w = webViewRef.get();
            if (w != null && !webViewIsDestroyed(w)) {
                w.evaluateJavascript(
                        "window.__epsilonGoogleNativeSuccess && " +
                        "window.__epsilonGoogleNativeSuccess(" + quote(idToken) + ");",
                        null
                );
            } else {
                Log.w(TAG, "[dispatchGoogleSuccess] WebView unavailable or destroyed");
            }
        });
    }

    private void dispatchGoogleError(String errorCode, String userMessage) {
        runOnUiThread(() -> {
            WebView w = webViewRef.get();
            if (w != null && !webViewIsDestroyed(w)) {
                w.evaluateJavascript(
                        "window.__epsilonGoogleNativeError && " +
                        "window.__epsilonGoogleNativeError(" + quote(errorCode) + ");",
                        null
                );
            }
            if (userMessage != null) {
                Toast.makeText(MainActivity.this, userMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showGoogleErrorToast(String exceptionClassName) {
        runOnUiThread(() -> Toast.makeText(
                MainActivity.this,
                "Google Sign-In error: " + exceptionClassName,
                Toast.LENGTH_LONG
        ).show());
    }

    private void logWebViewState(String stage) {
        WebView w = webViewRef.get();
        if (w != null) {
            Log.i(TAG, "[webViewState] stage=" + stage
                    + " webViewAttached=" + (w.getParent() != null)
                    + " webViewDestroyed=" + webViewIsDestroyed(w));
        }
    }

    private static boolean webViewIsDestroyed(WebView w) {
        if (w == null) return true;
        try {
            Method isDestroyed = WebView.class.getMethod("isDestroyed");
            return (Boolean) isDestroyed.invoke(w);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isPlausibleGoogleClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) return false;
        return clientId.endsWith(".apps.googleusercontent.com")
                && clientId.matches("[A-Za-z0-9._-]+");
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
            Log.i(TAG, "[signInWithGoogle] JavaScript invoked EpsilonAndroid.signInWithGoogle()");
            activity.nativeGoogleSignIn();
            return true;
        }

        @JavascriptInterface
        public void googleSignIn() {
            Log.i(TAG, "[googleSignIn] JavaScript invoked legacy EpsilonAndroid.googleSignIn() alias");
            activity.nativeGoogleSignIn();
        }

        @JavascriptInterface
        public void media(String title, String artist, String album, String artwork,
                          double duration, double position, boolean playing) {
            PlaybackService.updateMetadata(activity, title, artist, album, artwork,
                    duration, position, playing);
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
