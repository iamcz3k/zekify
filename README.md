# Zekify

<p align="center">
  <strong>Live sports, match discovery and streaming for Android.</strong><br/>
  Developed by <a href="https://github.com/iamcz3k">Titus Laczek (LaCzek)</a>.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-native%20UI-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android native UI"/>
  <img src="https://img.shields.io/badge/Latest%20release-v3.5.0-orange?style=flat-square" alt="Latest release v3.5.0"/>
  <img src="https://img.shields.io/badge/Ads-ad%20free-black?style=flat-square" alt="Ad free"/>
</p>

Zekify is an Android sports app for browsing live and upcoming events, opening match centres, and watching streams supplied by its backend and third-party providers. The app's main screens and playback experience are implemented with Android UI components. Direct media streams use AndroidX Media3/ExoPlayer; provider pages can use an embedded WebView player.

**Current published release:** [Zekify v3.5.0](https://github.com/iamcz3k/zekify/releases/tag/v3.5.0)  
**Application ID:** `com.v3x.zekify`

## Features

### Sports and match discovery

The home screen currently offers 15 sport categories:

- Football
- Motorsport
- Rugby
- American football
- Australian rules football
- Cricket
- Hockey
- Badminton
- Baseball
- Volleyball
- Fighting sports
- Cycling
- Handball
- Basketball
- Tennis

For supported categories, users can browse live and upcoming events, filter by league, and search teams, matches, or competitions. Match cards can show team badges, event status, kickoff information, and live elapsed-time or upcoming countdown indicators. Match data and badge images are cached locally where available, with network-recovery refresh and retry handling.

### Match details and streaming

- Match detail view with available sources from the backend
- Inline preview player with source selection
- Fullscreen playback with source switching
- AndroidX Media3/ExoPlayer for direct HLS/media URLs
- Embedded WebView playback for provider pages
- Playback retry and network-recovery handling
- Player controls auto-hide after inactivity
- Brightness gestures on one side and volume gestures on the other, with on-screen percentage feedback
- Video sizing options (Fit, Fill and Zoom) plus edge-to-edge stretch
- Seek controls when the stream supports seeking
- Quality selection when the direct stream exposes selectable video tracks; provider-controlled streams may not expose quality choices
- Mini-player to fullscreen handoff that transfers the active player where possible

Stream availability, selectable quality, seek support and playback reliability depend on the individual source and upstream provider.

### LiveScore and match centre

The separate LiveScore screen provides a date-based fixtures feed with previous/next day and Today navigation, search, and filters for All, Live, Upcoming, Finished and Favourites. It displays live-match counts and refreshes the feed periodically.

The match centre exposes these sections when the feed provides the data:

- Overview
- Events / match timeline
- Statistics
- Lineups
- League table
- Head-to-head (H2H)

Favourite competitions are saved locally on the device.

### Reminders and background playback

- Schedule an upcoming match from its match card
- Local notifications for 15 minutes before kickoff and at kickoff when enabled and permitted
- Restore scheduled reminders after device reboot or app replacement
- Optional floating overlay player for continuing playback over other apps (requires Android's display-over-other-apps permission)
- Android picture-in-picture support for supported playback/activity states

Reminder delivery depends on Android notification and alarm permissions/settings. Floating playback requires the user to grant overlay permission.

### Utilities, settings and support

- Network speed test measuring ping, jitter, download and upload using Cloudflare's public speed-test service
- Bug report screen with locally saved error codes and diagnostic details; reports are shared only when the user chooses to send them
- Share Zekify using the latest official release APK
- Social/contact links in the app menu
- Settings for compact match cards, team badges, haptics, keep-awake, stream auto-reconnect, exit confirmation, reduced motion, badge-memory saving, kickoff alerts and refresh-on-network-return
- Clear temporary cache without clearing saved settings, reminders or bug logs

### Updates

The app checks GitHub Releases for a newer version. An update is offered only when the release includes both an APK and its SHA-256 checksum. The updater downloads the APK, verifies the checksum and package identity/version, then hands installation to Android. Android's package installer performs the final signing-certificate compatibility check. Installing updates may require the user to allow Zekify to install apps from that source.

## Security

The v3.5.0 build includes client-side defence-in-depth checks for:

- Expected Android package/application identity
- Official signing certificate
- Embedded integrity marker
- Debuggable builds and active debugger attachment
- Common root indicators and test-key system builds
- Known runtime-hook indicators, including common Xposed/LSPosed and Frida traces, files, threads and mapped libraries
- Process tracer detection, including native verification
- Security verification at the app, LiveScore, fullscreen-player and floating-player entry points

The release workflow also verifies the signing certificate of the APK it builds before publishing it.

These checks raise the effort required to tamper with the client; they do **not** make reverse engineering or modification impossible. Root and instrumentation detection can have false positives or be bypassed. Client-side checks are not a substitute for server-side authorization, and no server-side enforcement is claimed here.

## Backend

The native app currently uses this API origin:

```text
https://repo-seed-transplanter.lovable.app
```

Main match and stream requests use these routes:

- Match list: `/api/public/football-streams?mode=matches&sport=<sport>`
- Match detail and sources: `/api/public/football-streams?mode=detail&sport=<sport>&id=<match-id>`
- HLS relay: `/api/public/fctv-hls` (where returned by match details)

The LiveScore screen uses the date-based FotMob feed route:

- Fixtures: `/api/public/fotmob?mode=matches&date=<yyyyMMdd>`

The API deployment and upstream providers are external dependencies. If they are unavailable or change their responses, some match, score or stream features may stop working until the service is restored or updated.

## Download

- [Latest official release and APK](https://github.com/iamcz3k/zekify/releases/latest)
- [All releases](https://github.com/iamcz3k/zekify/releases)
- [GitHub Actions builds and verification](https://github.com/iamcz3k/zekify/actions)

For safety and update compatibility, install releases published by this repository.

## Build and release

The `native-android-v2` branch contains the Android Java implementation, assets, native patching scripts and GitHub Actions build workflow. The workflow:

1. Builds the web asset bundle used by the Android packaging pipeline.
2. Generates and synchronizes the Android project.
3. Applies the native Android UI, player, security and manifest configuration.
4. Builds a minified signed release APK.
5. Verifies the APK signing certificate against the expected release certificate.
6. Publishes the APK and SHA-256 checksum as a GitHub Release asset.

Signing credentials are supplied through GitHub Actions secrets and are not committed to the repository.

To build through the configured workflow, open [GitHub Actions](https://github.com/iamcz3k/zekify/actions) and run **Build and release Zekify APK** on `native-android-v2`.

## Disclaimer

Zekify is an independent project. Sports data and streams may be supplied by external services and third-party providers. Zekify does not guarantee that every event, score, stream, or quality option will be available, accurate, lawful, or accessible in every region. Users are responsible for complying with applicable laws and rights-holder terms.

---

<p align="center">Made by <strong>LaCzek</strong> 🧡</p>
