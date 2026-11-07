import { readFile, writeFile } from "node:fs/promises";

const path = "android/app/src/main/java/com/v3x/zekify/PlayerActivity.java";
let source = await readFile(path, "utf8");

function replaceOnce(before, after, label) {
  const count = source.split(before).length - 1;
  if (count !== 1) throw new Error(label + ": expected exactly one anchor, found " + count);
  source = source.replace(before, after);
}

// The native template now hides the overlay at first visible page commit and
// again at page finish. Fail the build if the stale prompt ever returns.
if (source.includes("Tap the provider's play button to start")) {
  throw new Error("Stale embedded-page prompt is still present");
}
if (!source.includes("onPageCommitVisible") || !source.includes("status.setVisibility(View.GONE);")) {
  throw new Error("Embedded page commit overlay handling is missing");
}

// Buffering is a real loading state and must be visible. READY is the point at
// which the loading overlay must disappear.
replaceOnce(
  'status.setVisibility(View.GONE);\n                    recoveryHandler.removeCallbacksAndMessages(null);',
  'status.setVisibility(View.GONE);\n                    recoveryHandler.removeCallbacksAndMessages(null);',
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

// The native PlayerActivity template already applies resize mode and MATCH_PARENT
// layout before each direct source starts. Verify that implementation instead of
// injecting a duplicate block through an obsolete anchor.
if (!source.includes('playerView.setResizeMode(stretchEnabled') ||
    !source.includes('playerView.setLayoutParams(new FrameLayout.LayoutParams(') ||
    !source.includes('playerView.requestLayout();')) {
  throw new Error("Native source fill sizing is missing from PlayerActivity");
}

await writeFile(path, source);
console.log("Applied verified player-state and source-fill fixes.");

// v3.1.3 release: source-name loading overlay is suppressed.

// [release v3.1.3] Reuse the mini-player's real video surface in fullscreen.

// [release v3.1.3] Preserve playback engine and stop destructive buffering retries.
