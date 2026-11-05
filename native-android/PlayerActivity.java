package com.v3x.zekify;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.net.Uri;
import org.json.*;

public class PlayerActivity extends Activity {
    private VideoView video;
    private JSONArray sources;
    private int sourceIndex=0;
    private LinearLayout controls;
    private TextView label;
    private String matchTitle="";
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility( immersiveFlags());
        try{sources=new JSONArray(getIntent().getStringExtra("sources"));}catch(Exception e){sources=new JSONArray();}
        matchTitle=getIntent().getStringExtra("match");
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(16,12,16,12);controls.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);frame.addView(controls,cp);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        Button back=btn("‹ Back");back.setOnClickListener(v->finish());row.addView(back);
        label=new TextView(this);label.setTextColor(Color.WHITE);label.setText(matchTitle);label.setPadding(12,0,12,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Button source=btn("Sources");source.setOnClickListener(v->chooseSource());row.addView(source);
        Button play=btn("Pause");play.setOnClickListener(v->{if(video.isPlaying()){video.pause();play.setText("Play");}else{video.start();play.setText("Pause");}});row.addView(play);
        controls.addView(row);
        TextView hint=new TextView(this);hint.setText("Native playback • Swipe system bars from edge if needed");hint.setTextColor(0xffaaaaaa);hint.setTextSize(11);hint.setPadding(8,4,0,0);controls.addView(hint);
        setContentView(frame);playSource();
    }
    private int immersiveFlags(){return View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackgroundColor(0xff242424);return b;}
    private void chooseSource(){
        if(sources==null||sources.length()==0)return;String[] names=new String[sources.length()];
        for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);names[i]=s==null?"Source "+(i+1):s.optString("source","Source "+(i+1))+(s.optBoolean("hd")?" · HD":"");}
        new AlertDialog.Builder(this).setTitle("Select stream source").setSingleChoiceItems(names,sourceIndex,(d,which)->{sourceIndex=which;d.dismiss();playSource();}).setNegativeButton("Cancel",null).show();
    }
    private void playSource(){
        if(sources==null||sources.length()==0){showError("No stream sources available.");return;}
        JSONObject src=sources.optJSONObject(sourceIndex);String url=src==null?"":src.optString("embedUrl","");
        if(url.startsWith("/"))url="https://laczekstream.qzz.io"+url;
        label.setText(matchTitle+"  •  "+(src==null?"Source":src.optString("source","Source "+(sourceIndex+1))));
        if(url.isEmpty()){showError("This source has no playable URL.");return;}
        video.setVideoURI(Uri.parse(url));
        video.setOnPreparedListener(mp->{mp.setLooping(false);video.start();});
        video.setOnErrorListener((mp,what,extra)->{showError("This source is not a direct video stream supported by native playback. Try another source.");return true;});
    }
    private void showError(String msg){new AlertDialog.Builder(this).setTitle("Playback unavailable").setMessage(msg).setPositiveButton("Choose source", (d,w)->chooseSource()).setNegativeButton("Back", (d,w)->finish()).show();}
    @Override protected void onPause(){super.onPause();if(video!=null)video.pause();}
    @Override protected void onResume(){super.onResume();getWindow().getDecorView().setSystemUiVisibility(immersiveFlags());}
    @Override public void onBackPressed(){finish();}
}
