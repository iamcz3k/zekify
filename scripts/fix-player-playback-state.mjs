import { readFile, writeFile } from "node:fs/promises";

const path = "android/app/src/main/java/com/v3x/zekify/PlayerActivity.java";
let source = await readFile(path, "utf8");

function replaceOnce(before, after, label) {
  const count = source.split(before).length - 1;
  if (count !== 1) throw new Error(label + ": expected exactly one anchor, found " + count);
  source = source.replace(before, after);
}

// A document finishing is not a request for the user to tap Play. Remove the
// stale provider prompt; real navigation/media errors remain visible.
replaceOnce(
  'status.setText("Tap the provider\'s play button to start");\n                status.setVisibility(fullscreenRequested ? View.GONE : View.VISIBLE);',
  'status.setVisibility(View.GONE);',
  "embedded page completion overlay"
);

// Buffering is a real loading state and must be visible. READY is the point at
// which the loading overlay must disappear.
replaceOnce(
  'status.setText("Connecting to " + currentSourceName(source) + "…");\n                    recoveryHandler.removeCallbacksAndMessages(null);',
  'status.setText("Connecting to " + currentSourceName(source) + "…");\n                    status.setVisibility(View.VISIBLE);\n                    recoveryHandler.removeCallbacksAndMessages(null);',
  "buffering overlay visibility"
);
replaceOnce(
  'recoveryHandler.removeCallbacksAndMessages(null);\n                    retryCount = 0;\n                }',
  'recoveryHandler.removeCallbacksAndMessages(null);\n                    retryCount = 0;\n                    status.setVisibility(View.GONE);\n                }',
  "ready overlay cleanup"
);

// Also clear the overlay on the actual playing transition. This covers streams
// that move from READY to PLAYING after a delayed renderer start.
replaceOnce(
  '            @Override\n            public void onPlayerError(PlaybackException error) {',
  '            @Override\n            public void onIsPlayingChanged(boolean isPlaying) {\n                if (isPlaying) {\n                    recoveryHandler.removeCallbacksAndMessages(null);\n                    status.setVisibility(View.GONE);\n                }\n            }\n\n            @Override\n            public void onPlayerError(PlaybackException error) {',
  "playing-state overlay cleanup"
);

// Playback failures and recovery messages must remain visible after a prior
// READY/PLAYING transition hid the loading overlay.
replaceOnce(
  'status.setText("Stream connection lost. Retrying (" + attempt + "/2)…");',
  'status.setText("Stream connection lost. Retrying (" + attempt + "/2)…");\n                    status.setVisibility(View.VISIBLE);',
  "retry error visibility"
);
replaceOnce(
  'status.setText("Stream failed to load. Tap Retry or choose another source.");',
  'status.setText("Stream failed to load. Tap Retry or choose another source.");\n                    status.setVisibility(View.VISIBLE);',
  "terminal error visibility"
);

// Reassert the app's full-fill mode whenever a native source is selected,
// instead of inheriting a previous provider's aspect/resize state.
replaceOnce(
  '        playerView.setVisibility(View.VISIBLE);\n\n        Map<String, String> headers',
  '        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);\n        playerView.setVisibility(View.VISIBLE);\n\n        Map<String, String> headers',
  "native source fill mode"
);

await writeFile(path, source);
console.log("Applied verified player-state and source-fill fixes.");
