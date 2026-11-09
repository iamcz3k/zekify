# Zekify

<p align="center">
  <strong>Native Android football streaming and match discovery.</strong><br/>
  Built by <a href="https://github.com/iamcz3k">LaCzek</a>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/UI-AMOLED%20Dark-000000?style=flat-square" alt="AMOLED dark"/>
  <img src="https://img.shields.io/badge/Ads-Ad%20Free-orange?style=flat-square" alt="Ad free"/>
</p>

Zekify is a native Android app for discovering live and upcoming football matches and playing available streams. It has an AMOLED-friendly interface, device-local kickoff times, match details, and playback controls.

## Features

- Live and upcoming football fixtures
- Match search and league browsing
- Match details and available stream sources
- HLS playback and source selection when provided by the backend
- Mini-player and fullscreen playback
- Kickoff times shown in the device timezone
- Match notifications for user-selected fixtures where supported
- Ad-free app experience

## Backend and API

The native Android app uses its dedicated API deployment, independently of the LaCzek Stream website deployment.

Default API origin: `https://repo-seed-transplanter.lovable.app`

- Matches: `/api/public/football-streams?mode=matches&sport=football`
- Match details: `/api/public/football-streams?mode=detail&sport=football&id=<match-id>`
- Stream relay: `/api/public/fctv-hls` (stream URLs are returned by match details)

Relative playback URLs are resolved against the same API origin. Match and stream availability depend on the backend and upstream providers.

## Security

Zekify includes client-side protections intended to make unofficial or modified APKs harder to use and distribute, including integrity, signing-certificate, unofficial-build, and debugging/runtime checks where supported by the build.

These are defense-in-depth measures, not a guarantee that the app is impossible to reverse-engineer or modify. Client-side checks can potentially be bypassed. Server-side enforcement is a separate layer and must not be assumed unless it has been explicitly deployed.

## Download

- **Official releases:** [GitHub Releases](https://github.com/iamcz3k/zekify/releases)
- **Builds and test artifacts:** [GitHub Actions](https://github.com/iamcz3k/zekify/actions)

Use APKs published through the official repository. Builds from other sources may be modified or unsafe.

## Build and release

The `native-android-v2` branch contains the native Android project and its signed release workflow. The workflow checks the configured signing certificate before publishing, so updates remain compatible with existing installations. The signing keystore is supplied through repository Actions secrets and is not committed to the repository.

- Android application ID: `com.v3x.zekify`
- Developer: **Titus Laczek (LaCzek)**

## Disclaimer

Zekify is an independent project. Match listings and streams may be supplied by third-party services. The app does not guarantee the availability, legality, accuracy, or quality of third-party streams in every region. Users are responsible for complying with applicable laws and rights-holder terms.

---

<p align="center">Made by <strong>LaCzek</strong> 🧡</p>
