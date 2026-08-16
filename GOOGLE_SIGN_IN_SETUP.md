# Firebase Google Sign-In setup for the Android WebView

The native Android login uses Android Credential Manager to show Google accounts
available on the phone. It returns a Google ID token to the website, where the
existing Firebase Web SDK calls `signInWithCredential()`.

## Required one-time configuration

Replace this resource in:

`app/src/main/res/values/strings.xml`

```xml
<string name="default_web_client_id">REPLACE_WITH_FIREBASE_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

with the **Web application OAuth 2.0 client ID** belonging to the same Firebase
project (`epsilon-music-web`).

Find it in:

Firebase Console / Google Cloud Console -> Project `epsilon-music-web` ->
Google provider / OAuth 2.0 Client IDs -> Web client.

Do NOT use the Android client ID here. This value must be the web/server client
ID used as the `serverClientId` for Google ID-token sign-in.

## Android OAuth configuration

For the Android application package:

`com.epsilonmusic.app`

add the app's SHA-1/SHA-256 fingerprints to the same Firebase project if
Firebase asks for them. For local debug builds, the debug keystore fingerprint
is different from a release/upload-key fingerprint.

## Website patch

Replace these website files with the files in the companion patch ZIP:

- `src/hooks/use-auth.ts`
- `src/lib/store/media-session.ts`
- `src/components/player/player-bar.tsx`

The website keeps its normal Firebase Web SDK on desktop. Inside the Android
wrapper it detects `window.EpsilonAndroid.googleSignIn()`, receives the Google
ID token from Credential Manager, and signs into the existing Firebase project
with `GoogleAuthProvider.credential(idToken)`.

## Media controls

The Android wrapper exposes:

`window.EpsilonAndroid.media(...)`

and the website sends current YouTube track metadata/playback state to the native
MediaSession foreground service.

Native lock-screen controls call:

`window.__epsilonNativeMediaCommand(action, value?)`

which updates the same Zustand player state that already controls the YouTube
IFrame. This means the existing YouTube IFrame remains the playback engine.

## Background playback

The existing WebView `onWindowVisibilityChanged(View.GONE)` strategy is retained.
Do not add `webView.onPause()` or `pauseTimers()` to Activity.onPause().
