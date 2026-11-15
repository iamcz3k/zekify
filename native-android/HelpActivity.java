package com.v3x.zekify;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.PictureDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import com.caverock.androidsvg.SVG;

public final class HelpActivity extends Activity {
    private static final int BG = Color.rgb(5, 5, 5);
    private static final int PANEL = Color.rgb(18, 18, 18);
    private static final int ORANGE = Color.rgb(255, 138, 61);
    private static final int MUTED = Color.rgb(155, 155, 155);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SecurityGuard.verify(this)) {
            SecurityGuard.showBlocked(this);
            finish();
            return;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView back = text("‹", 32, Color.WHITE, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(44)));
        back.setOnClickListener(v -> finish());
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1);
        hp.leftMargin = dp(8);
        header.addView(heading, hp);
        heading.addView(text("HELP & TIPS", 11, ORANGE, true));
        heading.addView(text("Your offline Zekify guide", 19, Color.WHITE, true));
        page.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout hero = panel();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(20), dp(22), dp(20), dp(20));
        ImageView ball = new ImageView(this);
        ball.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadSvg(ball, "help/ball.svg");
        hero.addView(ball, new LinearLayout.LayoutParams(dp(72), dp(72)));
        ObjectAnimator spin = ObjectAnimator.ofFloat(ball, View.ROTATION, 0f, 360f);
        spin.setDuration(18000L);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new AccelerateDecelerateInterpolator());
        spin.start();
        TextView welcome = text("Get more from every match.", 19, Color.WHITE, true);
        welcome.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-1, -2);
        wp.topMargin = dp(14);
        hero.addView(welcome, wp);
        TextView intro = text("A quick guide that stays on your phone. Match data and live streams still need an internet connection.", 12, MUTED, false);
        intro.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(7);
        hero.addView(intro, ip);
        content.addView(hero);

        addGuide(content, "help/matches.svg", "1", "Find a match",
            "Choose a sport, switch between Live and Upcoming, then narrow the list with league filters or search. Open a match to see its details and available streams.");
        addGuide(content, "help/stream.svg", "2", "Start watching",
            "Open a match and choose one of its available sources. Tap Play to start. If a source does not load, return to the source list and try another one.");
        addGuide(content, "help/player.svg", "3", "Use the player",
            "Tap the video to show or hide playback controls. Use the player buttons for playback, source selection, mute and display options. Lock controls when you want to avoid accidental taps.");
        addGuide(content, "help/reminders.svg", "4", "Never miss kickoff",
            "Save a reminder from a match and manage your saved fixtures in Your schedule. Allow notifications in Android settings so kickoff alerts can reach you.");
        addGuide(content, "help/network.svg", "5", "Keep streaming stable",
            "A steady connection matters more than peak speed. On limited mobile data, avoid repeatedly switching sources and close other apps using the connection.");
        addGuide(content, "help/fix.svg", "6", "Fix common problems",
            "If matches do not load, check your connection and refresh. If one stream fails, try another source. Clear temporary cache from Settings only when needed. Your saved reminders and preferences are kept.");

        LinearLayout note = panel();
        note.setPadding(dp(16), dp(15), dp(16), dp(15));
        TextView noteTitle = text("STILL STUCK?", 10, ORANGE, true);
        note.addView(noteTitle);
        TextView noteBody = text("Open Your space → Bug reports to view a saved error report and share it with support. Reports stay on this device until you choose to send one.", 12, MUTED, false);
        LinearLayout.LayoutParams nb = new LinearLayout.LayoutParams(-1, -2);
        nb.topMargin = dp(6);
        note.addView(noteBody, nb);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(8);
        content.addView(note, np);

        setContentView(page);
    }

    private void addGuide(LinearLayout parent, String asset, String number, String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(background(PANEL, 20));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(9);
        parent.addView(card, cp);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadSvg(image, asset);
        card.addView(image, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(0, -2, 1);
        w.leftMargin = dp(13);
        card.addView(words, w);
        TextView label = text(number + "  " + title, 14, Color.WHITE, true);
        words.addView(label);
        TextView description = text(body, 11, MUTED, false);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, -2);
        d.topMargin = dp(5);
        words.addView(description, d);
    }

    private void loadSvg(ImageView target, String asset) {
        try {
            SVG svg = SVG.getFromAsset(getAssets(), asset);
            target.setImageDrawable(new PictureDrawable(svg.renderToPicture()));
        } catch (Exception ignored) {
            target.setVisibility(View.GONE);
        }
    }

    private LinearLayout panel() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(background(PANEL, 22));
        return view;
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), 0x20ffffff);
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? 1 : 0));
        view.setIncludeFontPadding(true);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
