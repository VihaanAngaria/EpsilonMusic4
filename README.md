# Epsilon Music Android WebView — FIXED BUILD

This version fixes the GitHub Actions failure at `:app:checkDebugDuplicateClasses`.

## Root cause

The original project included AndroidX AppCompat even though the app does not
use AppCompat APIs. That dependency brought incompatible Kotlin standard-library
variants into the build, producing duplicate classes such as:

- `kotlin.text.jdk8.RegexExtensionsJDK8Kt`
- `kotlin.time.jdk8.DurationConversionsJDK8Kt`

The fixed project is a pure Android-framework Java WebView app and therefore has
**no external app dependency**.

## Playback architecture

The website remains the playback source:

`https://epsilonmusic.space-z.ai`

The website's YouTube IFrame player is not extracted, proxied, or replaced.

The wrapper keeps:
- mobile User-Agent
- no desktop-site mode
- hardware acceleration
- JavaScript
- DOM storage
- database storage
- media playback without user gesture
- WebView lifecycle preservation
- custom `onWindowVisibilityChanged()` background strategy
- no address bar / toolbar

## GitHub Actions

The workflow:
1. installs JDK 17
2. installs Gradle 8.7
3. generates the Gradle wrapper
4. prints the runtime dependency tree
5. performs `clean assembleDebug`
6. uploads `app-debug.apk`

The resulting artifact is named `epsilon-music-debug`.

## Important background-playback limitation

This wrapper is optimized for a website whose player is a YouTube IFrame.
Android/WebView/YouTube behavior can vary between devices and WebView versions.
The app does not bypass or extract YouTube media and cannot guarantee YouTube's
background-playback policy.
