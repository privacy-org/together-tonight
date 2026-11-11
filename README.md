# Together Tonight

Private couples activities app for Mahesh & Ekashmi — just the two of them, on his Android phone, offline, no login, no account.

Single native Activity hosting a WebView; the whole app is bundled as `app/src/main/assets/index.html` (no external dependencies, no CDN, no network).

Package: com.mahesh.togethertonight — versionCode 1, versionName 1.0, minSdk 24, targetSdk 34, compileSdk 34.

Debug signing is pinned to ~/workspace/.tooling/signing/debug.keystore (expected SHA-1 0A:1A:71:63:12:09:A8:1A:7B:69:F7:F0:C2:B6:2A:53:4A:A8:46:CE).

Build offline:
```
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
export JAVA_HOME=~/workspace/.tooling/jdk17
export PATH=$JAVA_HOME/bin:$PATH
export GRADLE_USER_HOME=~/workspace/.gradle-hindu
~/workspace/.tooling/gradle-8.9/bin/gradle :app:assembleDebug --offline
```
