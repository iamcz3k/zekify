# Zekify

Native Android football app.

- Live and upcoming matches are loaded directly from the dedicated `repo-seed-transplanter` API deployment.
- Match details and stream sources use the same backend, independent of the LaCzek Stream website deployment.
- Kickoff times use the device timezone.
- Android package ID: `com.v3x.zekify`.

## API

Default API origin: `https://repo-seed-transplanter.lovable.app`.

Zekify 3.0.2 routes main football fixtures, match details, stream source resolution, LiveScore fixtures and match centre requests through this backend. Relative playback URLs are resolved against the same API origin.

- Matches: `/api/public/football-streams?mode=matches&sport=football`
- Match details: `/api/public/football-streams?mode=detail&sport=football&id=<match-id>`
- Stream relay: `/api/public/fctv-hls` (stream URLs are returned by match details)

## Build and release

The `native-android-v2` branch runs the signed Android release workflow. It checks the configured signing certificate before publishing, so updates remain compatible with existing installations. The signing keystore is supplied through repository Actions secrets and is not committed to the repository.
