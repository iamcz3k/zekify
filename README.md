# Zekify

<p align="center">
  <strong>Free live football streaming and match discovery for Android.</strong><br/>
  Built by <a href="https://github.com/iamcz3k">LaCzek</a>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/UI-AMOLED%20Dark-000000?style=flat-square" alt="AMOLED dark"/>
  <img src="https://img.shields.io/badge/Ads-Ad%20Free-orange?style=flat-square" alt="Ad free"/>
</p>

Zekify is an Android football app focused on finding matches and watching available live streams in a clean, dark interface. It uses the LACZEK STREAM service for match and stream data.

> **Note:** Stream availability and playback quality depend on upstream providers and the LACZEK STREAM service. A successful APK build does not guarantee that every match or stream will work.

## Features

- Live and upcoming football matches
- Match search and league browsing
- Team badges and match information where available
- Kickoff times displayed in the device's local timezone
- Live match countdown / elapsed-time display
- HLS stream playback with multiple sources when provided
- Source selection and player controls
- Mini-player and fullscreen playback
- Match notifications for user-selected fixtures (where supported)
- AMOLED-friendly dark interface
- Ad-free app experience

## Security

Zekify includes client-side protections intended to make unofficial APK modifications harder to distribute and use, including:

- APK integrity / tampering checks
- Signing-certificate verification
- Detection for modified or unofficial builds
- Debugging and runtime-environment checks
- Additional anti-tampering checks where supported by the build

These protections are **defense in depth**, not a guarantee that the app cannot be reverse-engineered or modified. Local checks can potentially be bypassed by a determined attacker. Server-side enforcement is a separate layer and should not be assumed unless explicitly deployed.

## Download

- **Releases:** [GitHub Releases](https://github.com/iamcz3k/zekify/releases)
- **Builds and test artifacts:** [GitHub Actions](https://github.com/iamcz3k/zekify/actions)

Use a release APK from the official repository. APKs from other sources may be modified or unsafe.

## Development

Repository: [github.com/iamcz3k/zekify](https://github.com/iamcz3k/zekify)

- Android application ID: `com.v3x.zekify`
- Developer: **Titus Laczek (LaCzek)**

Build instructions depend on the current Android project/toolchain and workflow. For reproducible builds, use the repository's GitHub Actions workflow and inspect its configuration before building locally.

## Disclaimer

Zekify is an independent project. Match listings and streams may be supplied by third-party services. The app does not guarantee the availability, legality, accuracy, or quality of third-party streams in every region. Users are responsible for complying with applicable laws and rights-holder terms.

---

<p align="center">Made by <strong>LaCzek</strong> 🧡</p>
