package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import android.widget.*;

public class FloatingPlayerService extends Service {
 private static volatile View pendingSurface;
 private static volatile Runnable closeAction;
 private static FloatingPlayerService instance;
 private WindowManager windowManager;
 private FrameLayout overlay;
 private WindowManager.LayoutParams params;
 private int startX,startY;
 private float touchX,touchY;

 public static void show(Context context,View surface,Runnable onClose){
  pendingSurface=surface;closeAction=onClose;
  Intent intent=new Intent(context,FloatingPlayerService.class);
  if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);
 }
 public static boolean isShowing(){return pendingSurface!=null;}
 public static void hideForReturn(){FloatingPlayerService current=instance;if(current!=null){current.removeOverlay(false);current.stopSelf();}}
 @Override public void onCreate(){
  super.onCreate();instance=this;windowManager=(WindowManager)getSystemService(WINDOW_SERVICE);
  if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel("zekify_floating_player","Zekify floating player",NotificationManager.IMPORTANCE_LOW);channel.setDescription("Keeps your match playing while you use other apps");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);}
  Intent open=new Intent(this,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pi=PendingIntent.getActivity(this,9031,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification notification;
  if(Build.VERSION.SDK_INT>=26)notification=new Notification.Builder(this,"zekify_floating_player").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Zekify is playing").setContentText("Tap to return to your match").setContentIntent(pi).setOngoing(true).build();
  else notification=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Zekify is playing").setContentText("Tap to return to your match").setContentIntent(pi).build();
  startForeground(9031,notification);
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){attachOverlay();return START_NOT_STICKY;}
 private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private void attachOverlay(){
  if(windowManager==null||pendingSurface==null||overlay!=null)return;
  overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.BLACK);
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(Color.rgb(5,5,5));overlay.addView(shell,new FrameLayout.LayoutParams(-1,-1));
  View accent=new View(this);accent.setBackgroundColor(Color.rgb(255,138,61));shell.addView(accent,new LinearLayout.LayoutParams(-1,dp(2)));
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(11),0,dp(8),0);bar.setBackgroundColor(Color.rgb(17,17,17));shell.addView(bar,new LinearLayout.LayoutParams(-1,dp(38)));
  TextView title=new TextView(this);title.setText("ZEKIFY  ·  PLAYING");title.setTextColor(Color.WHITE);title.setTextSize(11);title.setLetterSpacing(.08f);title.setTypeface(null,1);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
  TextView close=new TextView(this);close.setText("×");close.setTextColor(Color.rgb(255,138,61));close.setTextSize(23);close.setGravity(Gravity.CENTER);bar.addView(close,new LinearLayout.LayoutParams(dp(32),-1));close.setOnClickListener(v->{removeOverlay(true);stopSelf();});
  bar.setOnTouchListener((v,event)->{switch(event.getAction()){case MotionEvent.ACTION_DOWN:touchX=event.getRawX();touchY=event.getRawY();startX=params.x;startY=params.y;return true;case MotionEvent.ACTION_MOVE:params.x=startX+(int)(touchX-event.getRawX());params.y=startY+(int)(event.getRawY()-touchY);try{windowManager.updateViewLayout(overlay,params);}catch(Exception ignored){}return true;}return true;});
  View surface=pendingSurface;if(surface.getParent() instanceof ViewGroup)((ViewGroup)surface.getParent()).removeView(surface);
  shell.addView(surface,new LinearLayout.LayoutParams(-1,0,1));
  int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
  params=new WindowManager.LayoutParams(dp(286),dp(194),type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.TOP|Gravity.RIGHT;params.x=dp(12);params.y=dp(110);
  try{windowManager.addView(overlay,params);}catch(Exception error){overlay=null;stopSelf();}
 }
 private void removeOverlay(boolean stopPlayback){
  FrameLayout current=overlay;if(current==null)return;
  View surface=pendingSurface;
  if(surface!=null&&surface.getParent() instanceof ViewGroup)((ViewGroup)surface.getParent()).removeView(surface);
  try{windowManager.removeView(current);}catch(Exception ignored){}
  overlay=null;
  if(stopPlayback){Runnable callback=closeAction;closeAction=null;if(callback!=null)try{callback.run();}catch(Exception ignored){}}
 }
 @Override public void onDestroy(){removeOverlay(false);if(instance==this)instance=null;pendingSurface=null;closeAction=null;stopForeground(true);super.onDestroy();}
 @Override public android.os.IBinder onBind(Intent intent){return null;}
}
