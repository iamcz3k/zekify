package com.v3x.zekify;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.*;

public class PlayerActivity extends Activity {
 private ExoPlayer player; private PlayerView playerView; private JSONArray sources; private int sourceIndex=0; private TextView status; private String title="";
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);hideBars();try{sources=new JSONArray(getIntent().getStringExtra("sources"));}catch(Exception e){sources=new JSONArray();}sourceIndex=Math.max(0,getIntent().getIntExtra("sourceIndex",0));title=getIntent().getStringExtra("match");build();if(sources.length()>0)playSource();else error("No stream sources are available.");}
 private void hideBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
 private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundColor(0xdd171717);return b;}
 private void build(){FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);playerView=new PlayerView(this);playerView.setUseController(true);root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(10,5,10,5);bar.setBackgroundColor(0xaa090909);Button back=button("‹ Back");back.setOnClickListener(v->finish());bar.addView(back);status=new TextView(this);status.setTextColor(Color.WHITE);status.setText(title);status.setTextSize(13);status.setPadding(12,0,12,0);bar.addView(status,new LinearLayout.LayoutParams(0,-2,1));Button source=button("Sources");source.setOnClickListener(v->chooseSource());bar.addView(source);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(bar,bp);setContentView(root);}
 private void chooseSource(){if(sources.length()==0)return;String[] names=new String[sources.length()];for(int i=0;i<sources.length();i++){JSONObject s=sources.optJSONObject(i);names[i]=s==null?"Source "+(i+1):s.optString("source","Source "+(i+1))+(s.optBoolean("hd")?" · HD":"");}new AlertDialog.Builder(this).setTitle("Choose stream source").setSingleChoiceItems(names,sourceIndex,(d,w)->{sourceIndex=w;d.dismiss();playSource();}).setNegativeButton("Cancel",null).show();}
 private void playSource(){
  JSONObject s=sources.optJSONObject(sourceIndex);if(s==null){error("Invalid source information.");return;}
  String url=s.optString("embedUrl",s.optString("streamUrl",s.optString("url","")));
  if(url.startsWith("/"))url="https://laczekstream.qzz.io"+url;
  if(url.isEmpty()){error("This source has no stream URL.");return;}
  status.setText(title+"  •  "+s.optString("source","Source "+(sourceIndex+1)));
  if(player!=null){player.release();player=null;}
  JSONObject rawHeaders=s.optJSONObject("headers");java.util.Map<String,String> headers=new java.util.HashMap<>();
  if(rawHeaders!=null){java.util.Iterator<String> keys=rawHeaders.keys();while(keys.hasNext()){String key=keys.next();headers.put(key,rawHeaders.optString(key));}}
  String referer=s.optString("referer",s.optString("referrer",""));if(!referer.isEmpty())headers.put("Referer",referer);
  String origin=s.optString("origin","");if(!origin.isEmpty())headers.put("Origin",origin);
  String ua=s.optString("userAgent","");if(ua.isEmpty())ua="Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(ua).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
  DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
  player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
  player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){status.setText("Playback error ("+e.errorCodeName+"). Select another source.");Toast.makeText(PlayerActivity.this,"This stream could not be decoded or reached. Try another source.",Toast.LENGTH_LONG).show();}});
  String type=s.optString("type",s.optString("format","hls")).toLowerCase(java.util.Locale.ROOT);
  boolean hls=type.contains("hls")||url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8")||s.has("embedUrl");
  MediaItem item=new MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(hls?MimeTypes.APPLICATION_M3U8:null).build();
  if(hls)player.setMediaSource(new HlsMediaSource.Factory(data).createMediaSource(item));
  else player.setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(data).createMediaSource(item));
  player.prepare();player.play();
}
 private void error(String s){new AlertDialog.Builder(this).setTitle("Playback unavailable").setMessage(s).setPositiveButton("Choose source",(d,w)->chooseSource()).setNegativeButton("Back",(d,w)->finish()).show();}
 @Override protected void onResume(){super.onResume();hideBars();}
 @Override protected void onStop(){super.onStop();if(player!=null)player.pause();}
 @Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
 @Override public void onBackPressed(){finish();}
}