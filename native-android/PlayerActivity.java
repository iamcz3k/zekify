package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;

import org.json.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webView;
    private FrameLayout root;
    private LinearLayout toolbar;
    private TextView status;
    private boolean fillScreen = true;
    private Button resizeButton;
    private JSONArray sources;
    private int sourceIndex = 0;
    private String matchTitle = "";
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        hideBars();
        try {
            sources = new JSONArray(getIntent().getStringExtra("sources"));
        } catch (Exception ignored) {
            sources = new JSONArray();
        }
        sourceIndex = Math.max(0, getIntent().getIntExtra("sourceIndex", 0));
        if (sourceIndex >= sources.length()) sourceIndex = 0;
        matchTitle = getIntent().getStringExtra("match");
        if (matchTitle == null) matchTitle = "";
        build();
        if (sources.length() > 0) playSource();
        else showError("No stream sources are available.");
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable panel(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(panel(0xdd191919, 12));
        b.setMinHeight(dp(42));
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout.LayoutParams playerParams =
            new FrameLayout.LayoutParams(-1, -1);

        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        root.addView(playerView, playerParams);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                status.setText("Connecting to stream source…");
                status.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                status.setText("Tap the provider's play button to start");
                status.setVisibility(View.VISIBLE);
                applyWebVideoSizing();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("This source could not be reached. Choose another source or retry.");
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    status.setText("Source server returned " + response.getStatusCode() + ". Choose another source.");
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                webView.setVisibility(View.GONE);
                toolbar.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                hideBars();
            }

            @Override
            public void onHideCustomView() {
                hideCustomVideoView();
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                return true;
            }
        });

        root.addView(webView, playerParams);
        webView.setVisibility(View.GONE);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(9), dp(5), dp(9), dp(5));
        toolbar.setBackgroundColor(0xaa090909);

        Button back = button("‹ Back");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setMaxLines(2);
        status.setPadding(dp(10), 0, dp(8), 0);
        status.setText(matchTitle);
        toolbar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));

        Button reload = button("Reload");
        reload.setOnClickListener(v -> retrySource());
        toolbar.addView(reload);

        resizeButton = button("Fill");
        resizeButton.setOnClickListener(v -> toggleResizeMode());
        LinearLayout.LayoutParams resizeParams = new LinearLayout.LayoutParams(-2, -2);
        resizeParams.leftMargin = dp(6);
        toolbar.addView(resizeButton, resizeParams);

        Button source = button("Sources");
        source.setOnClickListener(v -> chooseSource());
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(-2, -2);
        sourceParams.leftMargin = dp(6);
        toolbar.addView(source, sourceParams);

        FrameLayout.LayoutParams barParams =
            new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(toolbar, barParams);
        setContentView(root);
    }

    private JSONObject currentSource() {
        return sources == null ? null : sources.optJSONObject(sourceIndex);
    }

    private String currentSourceName(JSONObject source) {
        return source == null ? "Source " + (sourceIndex + 1)
            : source.optString("source", "Source " + (sourceIndex + 1));
    }

    private void chooseSource() {
        if (sources == null || sources.length() == 0) return;
        String[] names = new String[sources.length()];
        for (int i = 0; i < sources.length(); i++) {
            JSONObject s = sources.optJSONObject(i);
            names[i] = currentSourceNameFor(s, i) + (s != null && s.optBoolean("hd") ? " · HD" : "");
        }
        new AlertDialog.Builder(this)
            .setTitle("Choose stream source")
            .setSingleChoiceItems(names, sourceIndex, (dialog, which) -> {
                sourceIndex = which;
                dialog.dismiss();
                playSource();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private String currentSourceNameFor(JSONObject source, int index) {
        return source == null ? "Source " + (index + 1)
            : source.optString("source", "Source " + (index + 1));
    }

    private String firstNonEmpty(JSONObject source, String... keys) {
        for (String key : keys) {
            String value = source.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private boolean isDirectMedia(JSONObject source, String url) {
        String type = firstNonEmpty(source, "type", "format", "mimeType").toLowerCase(Locale.ROOT);
        String lower = url.toLowerCase(Locale.ROOT);
        if (type.contains("hls") || type.contains("mpegurl") || type.contains("dash")
            || type.contains("mp4") || type.contains("video/")) return true;
        return lower.matches("(?s).*\\.(m3u8|mpd|mp4|m4v|webm|mov|mp3)(?:[?#].*)?$");
    }

    private boolean isHls(JSONObject source, String url) {
        String type = firstNonEmpty(source, "type", "format", "mimeType").toLowerCase(Locale.ROOT);
        return type.contains("hls") || type.contains("mpegurl")
            || url.toLowerCase(Locale.ROOT).matches("(?s).*\\.m3u8(?:[?#].*)?$");
    }

    private boolean isProviderEmbed(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            return host.endsWith("cdnlivetv.tv") && path.contains("/api/v1/channels/player");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void stopPlayback() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.setVisibility(View.GONE);
        }
        if (playerView != null) playerView.setVisibility(View.GONE);
    }

    private void playSource() {
        JSONObject source = currentSource();
        if (source == null) {
            showError("Invalid stream source information.");
            return;
        }

        String url = firstNonEmpty(source, "streamUrl", "mediaUrl", "url", "embedUrl");
        if (url.startsWith("/")) url = "https://laczekstream.qzz.io" + url;
        if (url.isEmpty()) {
            showError("This source does not include a playback link.");
            return;
        }

        stopPlayback();
        String label = currentSourceName(source);
        status.setText("Loading " + label + "…");
        status.setVisibility(View.VISIBLE);

        // CDN Live TV supplies an HTML5 player page intended for iframe embedding.
        // It is not a media file and must never be sent to ExoPlayer as HLS.
        if (isProviderEmbed(url) || !isDirectMedia(source, url)) {
            playEmbeddedPage(url);
            return;
        }
        playDirectMedia(source, url);
    }

    private void toggleResizeMode() {
        fillScreen = !fillScreen;
        if (resizeButton != null) resizeButton.setText(fillScreen ? "Fill" : "Fit");
        if (playerView != null) playerView.setResizeMode(fillScreen ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        applyWebVideoSizing();
    }

    private void applyWebVideoSizing() {
        if (webView == null) return;
        String fit = fillScreen ? "cover" : "contain";
        String js = "(function(){var s=document.getElementById('zekify-video-sizing');if(!s){s=document.createElement('style');s.id='zekify-video-sizing';document.head.appendChild(s);}s.textContent='html,body{width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;}video{width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;object-fit:" + fit + "!important;}';document.querySelectorAll('video').forEach(function(v){v.style.setProperty('object-fit','" + fit + "','important');v.style.setProperty('width','100vw','important');v.style.setProperty('height','100vh','important');});})();";
        webView.evaluateJavascript(js, null);
    }

    private void playEmbeddedPage(String url) {
        if (webView == null) return;
        playerView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.onResume();
        webView.loadUrl(url);
    }

    private void playDirectMedia(JSONObject source, String url) {
        webView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);

        Map<String, String> headers = new HashMap<>();
        JSONObject rawHeaders = source.optJSONObject("headers");
        if (rawHeaders != null) {
            Iterator<String> keys = rawHeaders.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, rawHeaders.optString(key));
            }
        }
        String referer = firstNonEmpty(source, "referer", "referrer");
        if (!referer.isEmpty()) headers.put("Referer", referer);
        String origin = firstNonEmpty(source, "origin");
        if (!origin.isEmpty()) headers.put("Origin", origin);

        String userAgent = firstNonEmpty(source, "userAgent");
        if (userAgent.isEmpty()) {
            userAgent = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, http);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    status.setText("Connecting to " + currentSourceName(source) + "…");
                } else if (playbackState == Player.STATE_READY) {
                    status.setText(matchTitle);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                status.setText("Direct stream failed. Choose another source.");
                Toast.makeText(PlayerActivity.this,
                    "This direct stream could not be played. Try another source.",
                    Toast.LENGTH_LONG).show();
            }
        });

        MediaItem item = new MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(isHls(source, url) ? MimeTypes.APPLICATION_M3U8 : null)
            .build();

        if (isHls(source, url)) {
            player.setMediaSource(new HlsMediaSource.Factory(data).createMediaSource(item));
        } else {
            player.setMediaSource(
                new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(data)
                    .createMediaSource(item)
            );
        }
        player.prepare();
        player.play();
    }

    private void retrySource() {
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            webView.reload();
        } else {
            playSource();
        }
    }

    private void hideCustomVideoView() {
        if (customVideoView == null) return;
        root.removeView(customVideoView);
        customVideoView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        webView.setVisibility(View.VISIBLE);
        toolbar.setVisibility(View.VISIBLE);
        hideBars();
    }

    private void showError(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (webView != null) webView.onResume();
        if (player != null) player.play();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (customVideoView != null) hideCustomVideoView();
        stopPlayback();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (customVideoView != null) {
            hideCustomVideoView();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        finish();
    }
}