package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.util.Rational;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackGroup;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;

import org.json.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class PlayerActivity extends Activity {
    static ExoPlayer transferredPlayer;
    static PlayerView transferredPlayerView;
    static WebView transferredWebView;
    static ExoPlayer returningPlayer;
    static PlayerView returningPlayerView;
    static WebView returningWebView;
    static int returningSourceIndex = -1;
    static boolean returnToMainActivity;
    private boolean handedOffFromMain;
    private boolean handedBackToMain;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private String activeUrl = "";
    private int retryCount = 0;
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private final Handler webLoadHandler = new Handler(Looper.getMainLooper());
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean webLoadFailed = false;
    // True once the provider's main document has rendered its first frame.
    // Some live providers never fire onPageFinished because of long-running requests.
    private boolean webPageCommitted = false;
    private PlayerView playerView;
    private WebView webView;
    private FrameLayout root;
    private LinearLayout toolbar;
    private TextView status;
    private final Runnable webLoadWatchdog = () -> {
        if (webView != null && webView.getVisibility() == View.VISIBLE && !webPageCommitted && !isFinishing()) {
            webLoadFailed = true;
            status.setText("Stream source timed out. Tap Retry or choose another source.");
            status.setVisibility(View.VISIBLE);
        }
    };
    private int resizeMode = 1; // 0=Fit, 1=Fill, 2=Zoom; default Fill
    private View resizeButton;
    private View stretchButton;
    private boolean stretchEnabled = true;
    private View gestureIndicator;
    private TextView gestureLabel;
    private ProgressBar gestureProgress;
    private final Handler gestureHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideGestureIndicator = () -> { if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE); };
    private JSONArray sources;
    private int sourceIndex = 0;
    private String matchTitle = "";
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean customViewTransition = false;
    private boolean fullscreenRequested = false;
    private boolean switchingSource = false;
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;
    private final Runnable hideControlsTask = () -> setControlsVisible(false);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams windowParams = getWindow().getAttributes();
            windowParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(windowParams);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
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
        handedOffFromMain = returnToMainActivity;
        fullscreenRequested = handedOffFromMain;
        returnToMainActivity = false;
        player = transferredPlayer;
        transferredPlayer = null;
        build();
        registerNetworkRecovery();
        activeUrl = getIntent().getStringExtra("activeUrl");
        if (activeUrl == null) activeUrl = "";
        if (player != null) {
            playerView.setPlayer(player);
            setControlsVisible(!handedOffFromMain);
        } else if (webView != null && webView.getVisibility() == View.VISIBLE) {
            webView.onResume();
            setControlsVisible(!handedOffFromMain);
        } else if (sources.length() > 0) playSource();
        else showError("No stream sources are available.");
    }

    private void hideBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                    | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
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
        // The status overlay is used by every playback path, including transferred WebViews.
        // It must exist before any WebView callback or onCreate branch calls setText().
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(9), dp(16), dp(9));
        status.setBackground(panel(0xdd151515, 22));
        status.setElevation(dp(8));
        status.setVisibility(View.GONE);

        FrameLayout.LayoutParams playerParams =
            new FrameLayout.LayoutParams(-1, -1);

        playerView = transferredPlayerView != null ? transferredPlayerView : new PlayerView(this);
        transferredPlayerView = null;
        if (playerView.getParent() instanceof ViewGroup) {
            ((ViewGroup) playerView.getParent()).removeView(playerView);
        }
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setOnTouchListener(new GestureSurface(true));
        root.addView(playerView, playerParams);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
        webView.setOnTouchListener(new GestureSurface(false));

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
                webLoadFailed = false;
                webPageCommitted = false;
                webLoadHandler.removeCallbacks(webLoadWatchdog);
                webLoadHandler.postDelayed(webLoadWatchdog, 25000L);
                status.setVisibility(View.GONE);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                // A provider may keep network requests open forever. Once its page is
                // visibly rendered, the app's loading overlay must no longer cover video.
                webPageCommitted = true;
                webLoadHandler.removeCallbacks(webLoadWatchdog);
                webLoadFailed = false;
                status.setVisibility(View.GONE);
                applyWebVideoSizing();
                if (fullscreenRequested) hideBars();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webPageCommitted = true;
                webLoadHandler.removeCallbacks(webLoadWatchdog);
                webLoadFailed = false;
                status.setVisibility(View.GONE);
                applyWebVideoSizing();
                if (fullscreenRequested) {
                    status.setVisibility(View.GONE);
                    webView.postDelayed(() -> { if (webView != null && fullscreenRequested && !isFinishing()) applyWebVideoSizing(); }, 350L);
                    webView.postDelayed(() -> { if (webView != null && fullscreenRequested && !isFinishing()) applyWebVideoSizing(); }, 1200L);
                    hideBars();
                }
                if (toolbar != null && controlsVisible) toolbar.bringToFront();
                scheduleControlsHide();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    webLoadHandler.removeCallbacks(webLoadWatchdog);
                    webLoadFailed = true;
                    status.setText("This source could not be reached. Tap Retry or choose another source.");
                    status.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, android.webkit.WebResourceResponse response) {
                if (response.getStatusCode() >= 400) {
                    String failedUrl = request.getUrl() == null ? "" : request.getUrl().toString().toLowerCase(Locale.ROOT);
                    boolean mediaRequest = failedUrl.matches("(?s).*\\.(m3u8|mpd|ts|m4s|mp4|aac|mp3)(?:[?#].*)?$");
                    if (request.isForMainFrame() || mediaRequest) {
                        webLoadFailed = true;
                        status.setText("Stream request failed (" + response.getStatusCode() + "). Tap Retry or choose another source.");
                        status.setVisibility(View.VISIBLE);
                    }
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
                if (customVideoView != null || customViewTransition) {
                    callback.onCustomViewHidden();
                    return;
                }
                customViewTransition = true;
                try {
                    android.view.ViewParent parent = view.getParent();
                    if (parent instanceof ViewGroup) {
                        ((ViewGroup) parent).removeView(view);
                    }
                    view.setOnTouchListener(new GestureSurface(false));
                    root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                    customVideoView = view;
                    customViewCallback = callback;
                    fullscreenRequested = true;
                    webView.setVisibility(View.GONE);
                    setControlsVisible(true);
                    scheduleControlsHide();
                    hideBars();
                } catch (RuntimeException error) {
                    customVideoView = null;
                    customViewCallback = null;
                    webView.setVisibility(View.VISIBLE);
                    try { callback.onCustomViewHidden(); } catch (RuntimeException ignored) {}
                    Toast.makeText(PlayerActivity.this,
                        "This provider could not open fullscreen. Playback is still available.",
                        Toast.LENGTH_LONG).show();
                } finally {
                    customViewTransition = false;
                }
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
        if (transferredWebView != null) {
            int transferredWebViewVisibility = transferredWebView.getVisibility();
            WebView placeholder = webView;
            if (placeholder.getParent() instanceof ViewGroup) {
                ((ViewGroup) placeholder.getParent()).removeView(placeholder);
            }
            placeholder.destroy();
            webView = transferredWebView;
            transferredWebView = null;
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.setOnTouchListener(new GestureSurface(false));
            webView.setWebChromeClient(new WebChromeClient() {
                @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                    if (customVideoView != null || customViewTransition) { callback.onCustomViewHidden(); return; }
                    customViewTransition = true;
                    try {
                        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                        view.setOnTouchListener(new GestureSurface(false));
                        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                        customVideoView = view;
                        customViewCallback = callback;
                        fullscreenRequested = true;
                        webView.setVisibility(View.GONE);
                        setControlsVisible(true);
                        scheduleControlsHide();
                        hideBars();
                    } catch (RuntimeException error) {
                        customVideoView = null;
                        customViewCallback = null;
                        try { callback.onCustomViewHidden(); } catch (RuntimeException ignored) {}
                    } finally { customViewTransition = false; }
                }
                @Override public void onHideCustomView() { hideCustomVideoView(); }
            });
            root.addView(webView, playerParams);
            webView.setVisibility(transferredWebViewVisibility);
            if (transferredWebViewVisibility == View.VISIBLE) {
                webView.postDelayed(this::applyWebVideoSizing, 500L);
            }
        }

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        statusParams.setMargins(dp(16), 0, dp(16), dp(24));
        root.addView(status, statusParams);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, 0, 0, 0);
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setElevation(0);
        View back = iconButton("back");
        back.setContentDescription("Back to match");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(46), dp(46)));
        HorizontalScrollView sourceStrip = new HorizontalScrollView(this);
        sourceStrip.setHorizontalScrollBarEnabled(false);
        sourceStrip.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout sourceItems = new LinearLayout(this);
        sourceItems.setOrientation(LinearLayout.HORIZONTAL);
        sourceItems.setGravity(Gravity.CENTER_VERTICAL);
        sourceStrip.addView(sourceItems, new HorizontalScrollView.LayoutParams(-2, dp(46)));
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        stripParams.leftMargin = dp(8);
        toolbar.addView(sourceStrip, stripParams);
        if (sources != null) {
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.optJSONObject(i);
                if (source == null) continue;
                final int index = i;
                TextView chip = new TextView(this);
                chip.setText(currentSourceNameFor(source, i) + (source.optBoolean("hd") ? " · HD" : ""));
                chip.setTextColor(i == sourceIndex ? Color.BLACK : Color.WHITE);
                chip.setTextSize(11);
                chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(13), 0, dp(13), 0);
                chip.setMinHeight(dp(38));
                chip.setBackground(panel(i == sourceIndex ? 0xffff8a3d : 0xe6242424, 22));
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(38));
                chipParams.rightMargin = dp(7);
                sourceItems.addView(chip, chipParams);
                chip.setOnClickListener(v -> {
                    if (index == sourceIndex) return;
                    sourceIndex = index;
                    playSource();
                    for (int j = 0; j < sourceItems.getChildCount(); j++) {
                        View child = sourceItems.getChildAt(j);
                        if (child instanceof TextView) {
                            ((TextView) child).setTextColor(j == sourceIndex ? Color.BLACK : Color.WHITE);
                            child.setBackground(panel(j == sourceIndex ? 0xffff8a3d : 0xe6242424, 22));
                        }
                    }
                    scheduleControlsHide();
                });
            }
        }
        FrameLayout.LayoutParams barParams =
            new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        barParams.setMargins(dp(12), dp(12), dp(12), 0);
        root.addView(toolbar, barParams);
        buildGestureIndicator();
        if (handedOffFromMain) {
            controlsVisible = false;
            toolbar.setVisibility(View.GONE);
        }
        root.setOnClickListener(v -> toggleControls());
        setContentView(root);
        scheduleControlsHide();
    }

    private View textControl(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setBackground(panel(0xe6242424, 18));
        button.setMinimumWidth(dp(48));
        button.setMinimumHeight(dp(42));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private View iconButton(String name) {
        FrameLayout holder = new FrameLayout(this);
        holder.setBackground(panel(0xe6242424, 20));
        holder.setElevation(dp(4));
        holder.setMinimumWidth(dp(46));
        holder.setMinimumHeight(dp(46));
        ImageView glyph = new ImageView(this);
        glyph.setImageResource(getResources().getIdentifier(
            "zekify_ic_" + name.replace("-", "_"), "drawable", getPackageName()));
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        holder.addView(glyph, glyphParams);
        holder.setClickable(true);
        holder.setFocusable(true);
        return holder;
    }

    private void adjustVolume(int direction) {
        android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) return;
        int current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
            Math.max(0, Math.min(max, current + direction)), 0);
    }

    private void buildGestureIndicator() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setBackground(panel(0xe6111111, 22));
        panel.setElevation(dp(8));
        gestureLabel = new TextView(this);
        gestureLabel.setTextColor(Color.WHITE);
        gestureLabel.setTextSize(12);
        gestureLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        gestureLabel.setGravity(Gravity.CENTER);
        panel.addView(gestureLabel, new LinearLayout.LayoutParams(-1, dp(22)));
        gestureProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        gestureProgress.setMax(100);
        gestureProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xffff8a3d));
        gestureProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff444444));
        panel.addView(gestureProgress, new LinearLayout.LayoutParams(-1, dp(5)));
        gestureIndicator = panel;
        gestureIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(132), dp(52), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        params.leftMargin = dp(24);
        root.addView(gestureIndicator, params);
    }

    private void showGestureLevel(boolean brightness, int percent) {
        if (gestureIndicator == null || gestureLabel == null || gestureProgress == null) return;
        int value = Math.max(0, Math.min(100, percent));
        gestureLabel.setText((brightness ? "☀  " : "♪  ") + value + "%");
        gestureProgress.setProgress(value);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) gestureIndicator.getLayoutParams();
        params.gravity = Gravity.CENTER_VERTICAL | (brightness ? Gravity.LEFT : Gravity.RIGHT);
        params.leftMargin = brightness ? dp(24) : 0;
        params.rightMargin = brightness ? 0 : dp(24);
        gestureIndicator.setLayoutParams(params);
        gestureIndicator.setVisibility(View.VISIBLE);
        gestureIndicator.bringToFront();
        gestureHandler.removeCallbacks(hideGestureIndicator);
        gestureHandler.postDelayed(hideGestureIndicator, 850L);
    }

    private class GestureSurface implements View.OnTouchListener {
        private final boolean direct;
        private float downX, downY, lastX, lastY, volumeAccumulator;
        private long lastTap, startPosition;
        private boolean moved;

        GestureSurface(boolean direct) { this.direct = direct; }

        @Override public boolean onTouch(View view, MotionEvent e) {
            float x = e.getX(), y = e.getY();
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x; downY = y; lastX = x; lastY = y;
                startPosition = player == null ? 0 : player.getCurrentPosition();
                volumeAccumulator = 0f;
                moved = false;
                return direct;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = x - downX, dy = y - downY;
                if (Math.abs(dx) > dp(18) || Math.abs(dy) > dp(18)) moved = true;
                if (moved && Math.abs(dx) > Math.abs(dy)) {
                    if (player != null && player.isCurrentMediaItemSeekable()) {
                        long target = Math.max(0, startPosition + (long) (dx * 180));
                        player.seekTo(target);
                    }
                } else if (moved && Math.abs(dy) > Math.abs(dx)) {
                    float delta = (lastY - y) / Math.max(1, view.getHeight());
                    if (downX < view.getWidth() / 2f) {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        float current = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                        float next = Math.max(0.02f, Math.min(1f, current + delta));
                        lp.screenBrightness = next;
                        getWindow().setAttributes(lp);
                        showGestureLevel(true, Math.round(next * 100f));
                    } else {
                        android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                        if (audio != null) {
                            int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                            int current = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                            volumeAccumulator += delta * Math.max(1, max);
                            int steps = Math.round(volumeAccumulator);
                            if (steps != 0) {
                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                                    Math.max(0, Math.min(max, current + steps)), 0);
                                volumeAccumulator -= steps;
                            }
                            int actual = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                            showGestureLevel(false, max <= 0 ? 0 : Math.round(actual * 100f / max));
                        }
                    }
                }
                lastX = x; lastY = y;
                return direct || moved;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                long now = System.currentTimeMillis();
                if (!moved) {
                    if (now - lastTap < 320) {
                        if (x < view.getWidth() / 2f) seekBy(-10000); else seekBy(10000);
                        lastTap = 0;
                        if (!direct) return true;
                    } else {
                        lastTap = now;
                        toggleControls();
                    }
                } else if (Math.abs(x - downX) > dp(40) && Math.abs(y - downY) < dp(35)
                    && player != null && !player.isCurrentMediaItemSeekable()) {
                    Toast.makeText(PlayerActivity.this, "Seeking is unavailable for this live stream.", Toast.LENGTH_SHORT).show();
                }
                return direct || moved;
            }
            if (e.getAction() == MotionEvent.ACTION_CANCEL) {
                gestureHandler.removeCallbacks(hideGestureIndicator);
                gestureHandler.postDelayed(hideGestureIndicator, 250L);
            }
            return direct;
        }
    }
    private void seekBy(long delta){if(player==null)return;if(!player.isCurrentMediaItemSeekable()){Toast.makeText(this,"This live stream does not support rewind or forward.",Toast.LENGTH_SHORT).show();return;}player.seekTo(Math.max(0,player.getCurrentPosition()+delta));}

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

    private boolean isFctvHlsRelay(String url) {
        try {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            return path != null && path.equals("/api/public/fctv-hls")
                && !uri.getQueryParameter("slot").isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDirectMedia(JSONObject source, String url) {
        String type = firstNonEmpty(source, "type", "format", "mimeType").toLowerCase(Locale.ROOT);
        String lower = url.toLowerCase(Locale.ROOT);
        if (isFctvHlsRelay(url)) return true;
        if (type.contains("hls") || type.contains("mpegurl") || type.contains("dash")
            || type.contains("mp4") || type.contains("video/")) return true;
        return lower.matches("(?s).*\\.(m3u8|mpd|mp4|m4v|webm|mov|mp3)(?:[?#].*)?$");
    }

    private boolean isHls(JSONObject source, String url) {
        String type = firstNonEmpty(source, "type", "format", "mimeType").toLowerCase(Locale.ROOT);
        return isFctvHlsRelay(url) || type.contains("hls") || type.contains("mpegurl")
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

    private void registerNetworkRecovery() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                recoveryHandler.postDelayed(() -> {
                    if (isFinishing() || activeUrl.isEmpty()) return;
                    if ((webView != null && webView.getVisibility() == View.VISIBLE && webLoadFailed)
                        || (player != null && player.getPlaybackState() == Player.STATE_IDLE)) retrySource();
                }, 1000L);
            }
        };
        try { connectivityManager.registerDefaultNetworkCallback(networkCallback); }
        catch (RuntimeException ignored) { networkCallback = null; }
    }

    private void stopPlayback() {
        recoveryHandler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        trackSelector = null;
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
        if (url.startsWith("/")) url = "https://repo-seed-transplanter.lovable.app" + url;
        if (url.isEmpty()) {
            showError("This source does not include a playback link.");
            return;
        }

        // Keep the current fullscreen state when switching; an already-visible WebView
        // counts too, not only a native PlayerView or provider custom view.
        boolean keepFullscreen = fullscreenRequested || customVideoView != null
            || (playerView != null && playerView.getVisibility() == View.VISIBLE && player != null)
            || (webView != null && webView.getVisibility() == View.VISIBLE);
        switchingSource = keepFullscreen;
        fullscreenRequested = keepFullscreen;
        if (customVideoView != null) hideCustomVideoView(true);
        hideBars();
        status.setVisibility(View.GONE);
        recoveryHandler.removeCallbacksAndMessages(null);
        activeUrl = url;
        retryCount = 0;

        // Keep the WebView alive for embed-to-embed switches and the ExoPlayer/PlayerView
        // alive for direct-to-direct switches. Only change engines when source types differ.
        boolean direct = !isProviderEmbed(url) && isDirectMedia(source, url);
        if (direct) {
            if (webView != null) {
                webView.stopLoading();
                webView.setVisibility(View.GONE);
            }
            if (playerView != null) playerView.setVisibility(View.VISIBLE);
        } else {
            recoveryHandler.removeCallbacksAndMessages(null);
            if (player != null) {
                player.stop();
                player.release();
                player = null;
                trackSelector = null;
            }
            if (playerView != null) {
                playerView.setPlayer(null);
                playerView.setVisibility(View.GONE);
            }
        }

        if (direct) playDirectMedia(source, url);
        else playEmbeddedPage(url);
    }

    private void toggleResizeMode() {
        resizeMode = (resizeMode + 1) % 3;
        String nextLabel = resizeMode == 0 ? "Fill" : (resizeMode == 1 ? "Zoom" : "Fit");
        if (resizeButton instanceof TextView) {
            ((TextView) resizeButton).setText(nextLabel);
            resizeButton.setContentDescription("Switch video to " + nextLabel);
        }
        if (playerView != null) {
            int mode = resizeMode == 0 ? AspectRatioFrameLayout.RESIZE_MODE_FIT
                : (resizeMode == 1 ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
            playerView.setResizeMode(mode);
        }
        applyWebVideoSizing();
        scheduleControlsHide();
    }

    private void toggleStretch() {
        stretchEnabled = !stretchEnabled;
        resizeMode = stretchEnabled ? 1 : 0;
        if (stretchButton instanceof ViewGroup && ((ViewGroup) stretchButton).getChildCount() > 0
            && ((ViewGroup) stretchButton).getChildAt(0) instanceof ImageView) {
            ImageView glyph = (ImageView) ((ViewGroup) stretchButton).getChildAt(0);
            int icon = getResources().getIdentifier(
                stretchEnabled ? "zekify_ic_stretch" : "zekify_ic_fit", "drawable", getPackageName());
            if (icon != 0) glyph.setImageResource(icon);
        }
        if (stretchButton != null) stretchButton.setContentDescription(stretchEnabled
            ? "Disable edge-to-edge stretch" : "Stretch video to all screen edges");
        if (playerView != null) {
            playerView.setResizeMode(stretchEnabled
                ? AspectRatioFrameLayout.RESIZE_MODE_FILL
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            playerView.requestLayout();
            playerView.invalidate();
        }
        applyWebVideoSizing();
        scheduleControlsHide();
    }

    private void chooseQuality() {
        if (player == null || trackSelector == null) {
            Toast.makeText(this, "Quality selection is controlled by this stream provider.", Toast.LENGTH_LONG).show();
            return;
        }
        androidx.media3.exoplayer.trackselection.MappingTrackSelector.MappedTrackInfo info =
            trackSelector.getCurrentMappedTrackInfo();
        if (info == null) {
            Toast.makeText(this, "This source has not exposed selectable quality levels.", Toast.LENGTH_LONG).show();
            return;
        }
        java.util.ArrayList<TrackSelectionOverride> overrides = new java.util.ArrayList<>();
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        labels.add("Auto");
        for (int r = 0; r < info.getRendererCount(); r++) {
            if (info.getRendererType(r) != androidx.media3.common.C.TRACK_TYPE_VIDEO) continue;
            androidx.media3.exoplayer.source.TrackGroupArray groups = info.getTrackGroups(r);
            for (int g = 0; g < groups.length; g++) {
                TrackGroup group = groups.get(g);
                java.util.HashSet<Integer> seen = new java.util.HashSet<>();
                for (int t = 0; t < group.length; t++) {
                    androidx.media3.common.Format format = group.getFormat(t);
                    if (format.height > 0 && seen.add(format.height)) {
                        labels.add(format.height + "p");
                        overrides.add(new TrackSelectionOverride(group, t));
                    }
                }
            }
        }
        if (labels.size() == 1) {
            Toast.makeText(this, "Only one quality is available for this stream.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] options = labels.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("Stream quality")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                        .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO).build());
                } else {
                    TrackSelectionOverride override = overrides.get(which - 1);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setOverrideForType(override).build());
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (toolbar != null) {
            toolbar.animate().cancel();
            if (visible) {
                toolbar.setVisibility(View.VISIBLE);
                toolbar.setAlpha(0f);
                toolbar.bringToFront();
                toolbar.animate().alpha(1f).setDuration(150L).start();
            } else {
                toolbar.animate().alpha(0f).setDuration(110L).withEndAction(() -> {
                    if (!controlsVisible && toolbar != null) toolbar.setVisibility(View.GONE);
                }).start();
            }
        }
        if (visible) scheduleControlsHide();
        else controlsHandler.removeCallbacks(hideControlsTask);
    }

    private void scheduleControlsHide() {
        controlsHandler.removeCallbacks(hideControlsTask);
        if (controlsVisible && toolbar != null && toolbar.getVisibility() == View.VISIBLE) {
            controlsHandler.postDelayed(hideControlsTask, 5000L);
        }
    }

    private void toggleControls() {
        // Every tap on the player toggles controls; visible controls can be dismissed immediately.
        setControlsVisible(!controlsVisible);
    }

    private void applyWebVideoSizing() {
        if (webView == null) return;
        String fit = stretchEnabled ? "fill" : "contain";
        String js = "(function(){"
            + "function size(){var s=document.getElementById('zekify-video-sizing');"
            + "if(!s){s=document.createElement('style');s.id='zekify-video-sizing';"
            + "(document.head||document.documentElement).appendChild(s);}"
            + "s.textContent='html,body{position:fixed!important;inset:0!important;width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;}'"
            + "+'iframe,video,#player,.player,.jwplayer,.video-js,.plyr{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;aspect-ratio:auto!important;object-fit:"
            + fit + "!important;margin:0!important;}';"
            + "document.querySelectorAll('iframe,video,#player,.player,.jwplayer,.video-js,.plyr').forEach(function(v){"
            + "v.style.setProperty('position','fixed','important');v.style.setProperty('inset','0','important');"
            + "v.style.setProperty('width','100vw','important');v.style.setProperty('height','100vh','important');"
            + "v.style.setProperty('max-width','none','important');v.style.setProperty('max-height','none','important');"
            + "v.style.setProperty('aspect-ratio','auto','important');v.style.setProperty('object-fit','" + fit + "','important');});}"
            + "size();if(!window.zekifySizingObserver){window.zekifySizingObserver=new MutationObserver(size);"
            + "window.zekifySizingObserver.observe(document.documentElement,{childList:true,subtree:true});}"
            + "})();";
        webView.evaluateJavascript(js, null);
    }

    private void playEmbeddedPage(String url) {
        if (webView == null) return;
        playerView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.onResume();
        webLoadHandler.removeCallbacks(webLoadWatchdog);
        webLoadFailed = false;
        webPageCommitted = false;
        webView.stopLoading();
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (fullscreenRequested) {
            status.setVisibility(View.GONE);
            webView.bringToFront();
            if (controlsVisible && toolbar != null) toolbar.bringToFront();
            hideBars();
            scheduleControlsHide();
        }
        webView.loadUrl(url);
    }

    private void playDirectMedia(JSONObject source, String url) {
        webView.setVisibility(View.GONE);
        playerView.setResizeMode(stretchEnabled
            ? AspectRatioFrameLayout.RESIZE_MODE_FILL
            : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playerView.setVisibility(View.VISIBLE);
        playerView.requestLayout();

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
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data = new DefaultDataSource.Factory(this, http);

        if (trackSelector == null) trackSelector = new DefaultTrackSelector(this);
        if (player == null) {
            player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
            player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    status.setVisibility(View.GONE);
                    recoveryHandler.removeCallbacksAndMessages(null);
                    recoveryHandler.postDelayed(() -> {
                        if (player != null && !isFinishing()
                            && player.getPlaybackState() == Player.STATE_BUFFERING) {
                            status.setText("Stream is still buffering. Retry or switch source if it does not recover.");
                            status.setVisibility(View.VISIBLE);
                        }
                    }, 15000L);
                } else if (playbackState == Player.STATE_READY) {
                    recoveryHandler.removeCallbacksAndMessages(null);
                    retryCount = 0;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (retryCount < 2 && !isFinishing()) {
                    final int attempt = ++retryCount;
                    status.setText("Stream connection lost. Retrying (" + attempt + "/2)…");
                    recoveryHandler.postDelayed(() -> {
                        if (player != null && !isFinishing()) {
                            player.prepare();
                            player.play();
                        }
                    }, attempt * 1500L);
                } else {
                    status.setText("Stream failed to load. Tap Retry or choose another source.");
                    Toast.makeText(PlayerActivity.this,
                        "Stream connection failed. Retry or switch source.",
                        Toast.LENGTH_LONG).show();
                }
            }
            });
        }
        playerView.setPlayer(player);

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
        retryCount = 0;
        recoveryHandler.removeCallbacksAndMessages(null);
        if (webView != null && webView.getVisibility() == View.VISIBLE) {
            webLoadFailed = false;
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.postDelayed(() -> {
                if (webView != null && !isFinishing() && !activeUrl.isEmpty()) {
                    status.setText("Retrying stream…");
                    status.setVisibility(View.VISIBLE);
                    webView.loadUrl(activeUrl);
                }
            }, 250);
        } else {
            playSource();
        }
    }

    private void hideCustomVideoView() { hideCustomVideoView(false); }

    private void hideCustomVideoView(boolean preserveFullscreen) {
        if (customVideoView == null || customViewTransition) return;
        customViewTransition = true;
        View videoView = customVideoView;
        WebChromeClient.CustomViewCallback callback = customViewCallback;
        customVideoView = null;
        customViewCallback = null;
        try {
            android.view.ViewParent parent = videoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(videoView);
            }
            if (webView != null) webView.setVisibility(View.VISIBLE);
            if (preserveFullscreen || fullscreenRequested) {
                fullscreenRequested = true;
                status.setVisibility(View.GONE);
                setControlsVisible(false);
            } else {
                if (toolbar != null) toolbar.bringToFront();
                setControlsVisible(true);
                scheduleControlsHide();
            }
            hideBars();
        } finally {
            customViewTransition = false;
        }
        // When replacing a source, do not tell the old provider that the user
        // exited fullscreen. That callback collapses the WebChrome custom view
        // and makes the replacement source fall back to its embedded layout.
        // The old page is immediately unloaded by playSource(), so discard the
        // callback and keep the activity's fullscreen session alive.
        if (callback != null && !preserveFullscreen) {
            try { callback.onCustomViewHidden(); } catch (RuntimeException ignored) {}
        }
        if (preserveFullscreen) {
            switchingSource = false;
            if (webView != null) webView.postDelayed(() -> { if (webView != null && !isFinishing()) { status.setVisibility(View.GONE); hideBars(); } }, 300L);
        } else if (!switchingSource) fullscreenRequested = false;
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

    private void returnPlaybackToMain() {
        if (!handedOffFromMain || handedBackToMain) return;
        if (playerView != null) {
            if (playerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) playerView.getParent()).removeView(playerView);
            }
            playerView.setOnTouchListener(null);
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
            playerView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            playerView.requestLayout();
            returningPlayerView = playerView;
            playerView = null;
        }
        returningPlayer = player;
        player = null;
        if (webView != null) {
            webView.setWebChromeClient(new WebChromeClient());
            webView.setOnTouchListener(null);
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            returningWebView = webView;
            webView = null;
        }
        returningSourceIndex = sourceIndex;
        handedBackToMain = true;
    }


    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT < 26
            || !getSharedPreferences("zekify_cache", MODE_PRIVATE).getBoolean("setting_floating_player", false)) return;
        boolean playing = player != null && player.isPlaying();
        boolean webPlaying = webView != null && webView.getVisibility() == View.VISIBLE && !webLoadFailed;
        if (!playing && !webPlaying) return;
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
            enterPictureInPictureMode(params);
        } catch (RuntimeException ignored) { }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (inPictureInPictureMode) {
            setControlsVisible(false);
            if (toolbar != null) toolbar.setVisibility(View.GONE);
        } else if (!isFinishing()) {
            if (toolbar != null) toolbar.setVisibility(View.VISIBLE);
            setControlsVisible(true);
            scheduleControlsHide();
        }
    }

    @Override
    protected void onPause() {
        boolean inPictureInPicture = Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
        if (webView != null && !inPictureInPicture) webView.onPause();
        if (player != null && !inPictureInPicture) player.pause();
        if (isFinishing()) returnPlaybackToMain();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webLoadHandler.removeCallbacksAndMessages(null);
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (RuntimeException ignored) {}
            networkCallback = null;
        }
        controlsHandler.removeCallbacks(hideControlsTask);
        recoveryHandler.removeCallbacksAndMessages(null);
        if (customVideoView != null) hideCustomVideoView();
        if (handedOffFromMain && !handedBackToMain) returnPlaybackToMain();
        if (!handedBackToMain) {
            stopPlayback();
            if (webView != null) {
                webView.destroy();
                webView = null;
            }
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