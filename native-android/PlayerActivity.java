package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
    private boolean fillScreen = false;
    private View resizeButton;
    private JSONArray sources;
    private int sourceIndex = 0;
    private String matchTitle = "";
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private final Handler controlsHandler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;
    private final Runnable hideControlsTask = () -> setControlsVisible(false);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
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
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
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
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                toolbar.setVisibility(View.VISIBLE);
                toolbar.bringToFront();
                controlsHandler.removeCallbacks(hideControlsTask);
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

        View back = iconButton("back");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setMaxLines(2);
        status.setPadding(dp(10), 0, dp(8), 0);
        status.setText(matchTitle);
        toolbar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));

        View reload = iconButton("refresh");
        reload.setOnClickListener(v -> retrySource());
        toolbar.addView(reload);

        resizeButton = iconButton("fit");
        resizeButton.setContentDescription("Toggle fit or fill screen");
        resizeButton.setOnClickListener(v -> toggleResizeMode());
        toolbar.addView(resizeButton);
        View sourceButton = iconButton("sources");
        sourceButton.setContentDescription("Choose stream source");
        sourceButton.setOnClickListener(v -> chooseSource());
        toolbar.addView(sourceButton);

        FrameLayout.LayoutParams barParams =
            new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(toolbar, barParams);
        root.setOnClickListener(v -> toggleControls());
        setContentView(root);
        scheduleControlsHide();
    }

    private View iconButton(String name) {
        FrameLayout holder = new FrameLayout(this);
        holder.setBackgroundColor(Color.TRANSPARENT);
        holder.setMinimumWidth(dp(38));
        holder.setMinimumHeight(dp(40));
        IconGlyph glyph = new IconGlyph(this,name);
        holder.addView(glyph,new FrameLayout.LayoutParams(-1,-1));
        holder.setOnClickListener(v -> { if ("back".equals(name)) finish(); else retrySource(); });
        return holder;
    }

    private class GestureSurface implements View.OnTouchListener {
        private final boolean direct; private float downX,downY,lastX,lastY; private long downTime,lastTap,startPosition; private boolean moved;
        GestureSurface(boolean direct){this.direct=direct;}
        @Override public boolean onTouch(View view, MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;lastX=x;lastY=y;downTime=System.currentTimeMillis();startPosition=player==null?0:player.getCurrentPosition();moved=false;return direct;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=x-downX,dy=y-downY;if(Math.abs(dx)>dp(18)||Math.abs(dy)>dp(18))moved=true;
                if(moved&&Math.abs(dx)>Math.abs(dy)){ if(player!=null&&player.isCurrentMediaItemSeekable()){long target=Math.max(0,startPosition+(long)(dx*180));player.seekTo(target);} }
                else if(moved&&Math.abs(dy)>Math.abs(dx)){float delta=(lastY-y)/Math.max(1,view.getHeight());if(downX<view.getWidth()/2f){WindowManager.LayoutParams lp=getWindow().getAttributes();float b=lp.screenBrightness<0?0.5f:lp.screenBrightness;lp.screenBrightness=Math.max(0.02f,Math.min(1f,b+delta));getWindow().setAttributes(lp);}else{android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);int max=am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);int cur=am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,Math.max(0,Math.min(max,cur+(int)(delta*max))),0);} }
                lastX=x;lastY=y;return direct||moved;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                long now=System.currentTimeMillis();if(!moved){if(now-lastTap<320){if(x<view.getWidth()/2f)seekBy(-10000);else seekBy(10000);lastTap=0;if(!direct)return true;}else{lastTap=now;toggleControls();}}else if(Math.abs(x-downX)>dp(40)&&Math.abs(y-downY)<dp(35)&&player!=null&&!player.isCurrentMediaItemSeekable()){Toast.makeText(PlayerActivity.this,"Seeking is unavailable for this live stream.",Toast.LENGTH_SHORT).show();}
                return direct||moved;
            }return direct;
        }
    }
    private void seekBy(long delta){if(player==null)return;if(!player.isCurrentMediaItemSeekable()){Toast.makeText(this,"This live stream does not support rewind or forward.",Toast.LENGTH_SHORT).show();return;}player.seekTo(Math.max(0,player.getCurrentPosition()+delta));}
    private static class IconGlyph extends View {
        private final String kind; private final android.graphics.Paint p=new android.graphics.Paint(3);
        IconGlyph(Context c,String k){super(c);kind=k;p.setColor(Color.WHITE);p.setStrokeWidth(2.2f);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStyle(android.graphics.Paint.Style.STROKE);}
        @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);float s=Math.min(getWidth(),getHeight())*.42f;c.save();c.translate(getWidth()/2f,getHeight()/2f);c.scale(s/24f,s/24f);android.graphics.Path q=new android.graphics.Path();if("back".equals(kind)){q.moveTo(15,5);q.lineTo(8,12);q.lineTo(15,19);q.moveTo(8,12);q.lineTo(21,12);}else if("fit".equals(kind)){q.moveTo(4,9);q.lineTo(4,4);q.lineTo(9,4);q.moveTo(15,4);q.lineTo(20,4);q.lineTo(20,9);q.moveTo(20,15);q.lineTo(20,20);q.lineTo(15,20);q.moveTo(9,20);q.lineTo(4,20);q.lineTo(4,15);}else if("sources".equals(kind)){c.drawCircle(-5,-6,2,p);c.drawCircle(-5,0,2,p);c.drawCircle(-5,6,2,p);q.moveTo(-1,-6);q.lineTo(7,-6);q.moveTo(-1,0);q.lineTo(7,0);q.moveTo(-1,6);q.lineTo(7,6);}else{c.drawArc(-8,-8,8,8,-70,290,false,p);q.moveTo(3,-9);q.lineTo(9,-9);q.lineTo(9,-3);}c.drawPath(q,p);c.restore();}
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
        if (resizeButton != null) resizeButton.setContentDescription(fillScreen ? "Fit video to screen" : "Fill screen");
        if (playerView != null) playerView.setResizeMode(fillScreen ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        applyWebVideoSizing();
        scheduleControlsHide();
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        if (toolbar != null) toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) scheduleControlsHide();
    }

    private void scheduleControlsHide() {
        controlsHandler.removeCallbacks(hideControlsTask);
        // Keep playback controls available; do not auto-hide the toolbar.
    }

    private void toggleControls() {
        setControlsVisible(!controlsVisible);
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
        toolbar.bringToFront();
        controlsVisible = true;
        scheduleControlsHide();
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
        controlsHandler.removeCallbacks(hideControlsTask);
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