# Zekify

Android-first football app built with React, Vite and Capacitor.

- Live and upcoming match views backed by the LACZEK STREAM public football API.
- Kickoff times formatted in each viewer's device timezone.
- HLS playback support through hls.js.
- Android package ID: `com.v3x.zekify`.
- GitHub Actions workflow builds a debug APK artifact.

## Build
```sh
npm install
npm run build
npx cap add android
npx cap sync android
```

Default API origin: `https://laczekstream.qzz.io`. Override with `VITE_STREAM_API_BASE` if needed.

Run **Actions → Build Zekify APK → Run workflow**, then download the `zekify-debug-apk` artifact after success.

A successful APK build does not by itself verify live stream availability; playback still depends on the upstream provider and LACZEK STREAM relay.
