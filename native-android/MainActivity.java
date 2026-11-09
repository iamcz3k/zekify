package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.content.res.Configuration;
import android.util.Rational;
import android.net.ConnectivityManager;
import android.net.Network;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.text.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.hls.*;
import androidx.media3.ui.*;
import androidx.core.content.FileProvider;
import org.json.*;
import com.caverock.androidsvg.SVG;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final String API="https://repo-seed-transplanter.lovable.app";
 private final ExecutorService apiIo=Executors.newFixedThreadPool(2);
 private final Handler inlineNetworkHandler=new Handler(Looper.getMainLooper());
 private ConnectivityManager connectivityManager;
 private ConnectivityManager.NetworkCallback networkCallback;
 private final java.util.concurrent.atomic.AtomicBoolean matchesLoading=new java.util.concurrent.atomic.AtomicBoolean(false);
 private int inlineLoadGeneration=0; private int inlineWebGeneration=0; private Runnable inlineWebTimeout;
 private int inlineRetryCount=0;
 private final ExecutorService imageIo=Executors.newFixedThreadPool(2);
 private final ExecutorService shareIo=Executors.newSingleThreadExecutor();
 private final LruCache<String,Bitmap> badgeCache=new LruCache<>(64);
 private final LruCache<String,Bitmap> sportIconCache=new LruCache<>(24);
 private android.content.SharedPreferences appPrefs;
 private static final String REMINDER_IDS="scheduled_match_ids";
 private String pendingMatchId="";
 private final Set<String> badgeRequests=Collections.synchronizedSet(new HashSet<>());
 private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<FrameLayout>>> badgeWaiters=new java.util.concurrent.ConcurrentHashMap<>();
 private final ArrayList<JSONObject> matches=new ArrayList<>();
 private LinearLayout root,content,leagueRow;
 private ScrollView matchesScroll;
 private EditText search;
 private String tab="live",league="All",query="",selectedSport="football";
 private TextView sportTitle; private LinearLayout sportRow; private int matchesGeneration=0;
 private JSONObject selected,detail; private LinearLayout sourceList; private WebView matchPreview; private int selectedSourceIndex=0; private JSONArray selectedSources; private FrameLayout inlineFrame; private PlayerView inlinePlayerView; private ExoPlayer inlinePlayer; private WebView inlineWebView; private TextView inlineStatus; private LinearLayout inlineSources; private boolean inlineLoading=false; private Dialog startupDialog; private boolean mainPipActive=false;
 private final int ORANGE=Color.rgb(255,138,61),BG=Color.rgb(5,5,5),PANEL=Color.rgb(17,17,17),MUTED=Color.rgb(145,145,145);
 @Override public void onCreate(Bundle b){super.onCreate(b);appPrefs=getSharedPreferences("zekify_cache",MODE_PRIVATE);installCrashRecorder();pendingMatchId=getIntent().getStringExtra("matchId")==null?"":getIntent().getStringExtra("matchId");getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR^View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);createReminderChannel();home();applySettings();showStartupSplash();registerNetworkRecovery();load();}
 private void installCrashRecorder(){Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));recordBug("APP_CRASH",error,trace.toString());appPrefs.edit().putString("last_uncaught_error",trace.toString()).putLong("last_uncaught_error_at",System.currentTimeMillis()).apply();}catch(Exception ignored){}if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}});}
 private void showStartupSplash(){
  startupDialog=new Dialog(this);startupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout splash=new FrameLayout(this);splash.setBackgroundColor(Color.BLACK);
  LinearLayout center=col();center.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);splash.addView(center,cp);
  center.addView(new Ball(this),new LinearLayout.LayoutParams(dp(78),dp(78)));TextView brand=txt("ZEKIFY",20,Color.WHITE,true);brand.setLetterSpacing(.18f);brand.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(18);center.addView(brand,bp);
  ProgressBar progress=new ProgressBar(this);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ORANGE));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(42),dp(42));pp.topMargin=dp(24);center.addView(progress,pp);
  startupDialog.setContentView(splash);startupDialog.setCancelable(false);startupDialog.show();Window w=startupDialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
 }
 private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}}
 private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(Math.max(r,14)));d.setStroke(dp(1),Color.argb(c==Color.TRANSPARENT?12:28,255,255,255));return d;}
 private TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(true);t.setTextIsSelectable(false);return t;}
 private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
 private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);return x;}
 private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(primary?Color.BLACK:Color.WHITE);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setBackground(shape(primary?ORANGE:0xff171717,16));b.setMinHeight(dp(46));b.setPadding(dp(16),0,dp(16),0);b.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(75).start();else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(new android.view.animation.OvershootInterpolator(1.15f)).start();return false;});return b;}
 private void home(){
  root=col();root.setBackgroundColor(BG);LinearLayout header=row();pad(header,18,13,16,12);LinearLayout brand=row();brand.addView(new Ball(this),new LinearLayout.LayoutParams(dp(32),dp(32)));
  TextView name=txt("ZEKIFY",18,Color.WHITE,true);name.setLetterSpacing(.16f);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.leftMargin=dp(9);brand.addView(name,np);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout right=row();right.setGravity(Gravity.CENTER_VERTICAL);sportTitle=txt(sportLabel().toUpperCase(Locale.ROOT),9,ORANGE,true);sportTitle.setLetterSpacing(.1f);right.addView(sportTitle);
  NativeIcon menu=new NativeIcon(this,"menu",Color.WHITE);menu.setContentDescription("Open menu");LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(42),dp(42));mp.leftMargin=dp(8);right.addView(menu,mp);menu.setOnClickListener(v->menu());header.addView(right);root.addView(header);
  View divider=new View(this);divider.setBackgroundColor(0x20ffffff);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));

  HorizontalScrollView sportsScroll=new HorizontalScrollView(this);sportsScroll.setHorizontalScrollBarEnabled(false);sportsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);sportRow=row();pad(sportRow,18,0,18,12);sportsScroll.addView(sportRow);root.addView(sportsScroll,new LinearLayout.LayoutParams(-1,dp(49)));renderSports();
  LinearLayout searchBox=row();searchBox.setBackground(shape(0xff151515,18));pad(searchBox,16,0,14,0);searchBox.addView(new NativeIcon(this,"search",MUTED),new LinearLayout.LayoutParams(dp(19),dp(19)));
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);search.setTextColor(Color.WHITE);search.setHintTextColor(0xff777777);search.setHint("Search teams, matches or leagues");search.setBackgroundColor(Color.TRANSPARENT);search.setTextIsSelectable(false);search.setLongClickable(false);pad(search,10,0,0,0);search.setText(query);searchBox.addView(search,new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(52));searchP.setMargins(dp(18),dp(2),dp(18),dp(12));root.addView(searchBox,searchP);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
  LinearLayout tabs=row();tabs.setBackground(shape(0xff151515,18));pad(tabs,5,5,5,5);TextView live=txt("LIVE NOW",11,tab.equals("live")?Color.WHITE:MUTED,true);live.setGravity(Gravity.CENTER);live.setBackground(shape(tab.equals("live")?0xff3a2518:Color.TRANSPARENT,14));
  TextView upcoming=txt("UPCOMING",11,tab.equals("upcoming")?Color.WHITE:MUTED,true);upcoming.setGravity(Gravity.CENTER);upcoming.setBackground(shape(tab.equals("upcoming")?0xff3a2518:Color.TRANSPARENT,14));
  live.setTextSize(12);upcoming.setTextSize(12);tabs.addView(live,new LinearLayout.LayoutParams(0,dp(42),1));tabs.addView(upcoming,new LinearLayout.LayoutParams(0,dp(42),1));live.setOnClickListener(v->{if(!tab.equals("live")){tab="live";home();render();}});upcoming.setOnClickListener(v->{if(!tab.equals("upcoming")){tab="upcoming";home();render();}});
  LinearLayout.LayoutParams tabsP=new LinearLayout.LayoutParams(-1,dp(47));tabsP.setMargins(dp(18),0,dp(18),dp(10));root.addView(tabs,tabsP);
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setOverScrollMode(View.OVER_SCROLL_NEVER);leagueRow=row();pad(leagueRow,18,0,18,11);hs.addView(leagueRow);root.addView(hs);
  ScrollView sc=new ScrollView(this);matchesScroll=sc;sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(false);content=col();pad(content,18,3,18,24);sc.addView(content);sc.setOnScrollChangeListener((View v,int sx,int sy,int oldSx,int oldSy)->loadVisibleBadges());root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);if(!setting("reduce_motion",false)){root.setAlpha(0f);root.animate().alpha(1f).setDuration(180L).start();}renderLeagues();
 }
 private void menu(){
  Dialog dialog=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff0b0b0b,28));pad(panel,20,20,20,22);
  LinearLayout top=row();LinearLayout brand=row();brand.addView(new Ball(this),new LinearLayout.LayoutParams(dp(32),dp(32)));TextView brandName=txt("ZEKIFY",18,Color.WHITE,true);brandName.setLetterSpacing(.16f);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.leftMargin=dp(9);brand.addView(brandName,bp);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  NativeIcon close=new NativeIcon(this,"close",Color.WHITE);top.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));close.setOnClickListener(v->dialog.dismiss());panel.addView(top);View menuAccent=new View(this);menuAccent.setBackgroundColor(ORANGE);LinearLayout.LayoutParams menuAccentP=new LinearLayout.LayoutParams(-1,dp(2));menuAccentP.topMargin=dp(12);panel.addView(menuAccent,menuAccentP);TextView menuCaption=txt("YOUR SPORTS. YOUR CONTROL.",9,ORANGE,true);menuCaption.setLetterSpacing(.1f);LinearLayout.LayoutParams menuCaptionP=new LinearLayout.LayoutParams(-1,-2);menuCaptionP.topMargin=dp(5);panel.addView(menuCaption,menuCaptionP);
  LinearLayout scheduled=row();scheduled.setBackground(shape(0xff171717,15));pad(scheduled,14,13,14,13);LinearLayout.LayoutParams scheduledP=new LinearLayout.LayoutParams(-1,dp(54));scheduledP.topMargin=dp(12);panel.addView(scheduled,scheduledP);NativeIcon scheduledIcon=new NativeIcon(this,"calendar",ORANGE);scheduled.addView(scheduledIcon,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView scheduledLabel=txt("Scheduled Matches",13,Color.WHITE,true);LinearLayout.LayoutParams scheduledTextP=new LinearLayout.LayoutParams(0,-2,1);scheduledTextP.leftMargin=dp(12);scheduled.addView(scheduledLabel,scheduledTextP);scheduled.addView(txt(String.valueOf(getScheduledIds().size()),11,ORANGE,true));scheduled.setOnClickListener(v->{dialog.dismiss();showScheduledMatches();});
  LinearLayout floating=row();floating.setBackground(shape(0xff171717,15));pad(floating,14,10,12,10);LinearLayout.LayoutParams floatingP=new LinearLayout.LayoutParams(-1,dp(60));floatingP.topMargin=dp(8);panel.addView(floating,floatingP);LinearLayout floatingWords=col();floating.addView(floatingWords,new LinearLayout.LayoutParams(0,-2,1));floatingWords.addView(txt("Floating player",13,Color.WHITE,true));floatingWords.addView(txt("Continue watching in a small PiP window when you leave Zekify",9,MUTED,false));Switch floatingToggle=new Switch(this);tintSwitch(floatingToggle);floatingToggle.setChecked(setting("floating_player",false));floating.addView(floatingToggle);floatingToggle.setOnCheckedChangeListener((button,enabled)->{if(enabled&&Build.VERSION.SDK_INT>=23&&!android.provider.Settings.canDrawOverlays(this)){appPrefs.edit().putBoolean("setting_floating_player",false).apply();button.setChecked(false);Toast.makeText(this,"Allow display over other apps to use the floating player.",Toast.LENGTH_LONG).show();try{Intent permission=new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));startActivity(permission);}catch(Exception ignored){}return;}appPrefs.edit().putBoolean("setting_floating_player",enabled).apply();Toast.makeText(this,enabled?"Floating overlay enabled. Start a stream, then leave Zekify.":"Floating player disabled.",Toast.LENGTH_SHORT).show();});
  panel.addView(menuActionRow("LiveScore","Fixtures, live scores, favourites and match centre","calendar",()->{dialog.dismiss();startActivity(new Intent(this,LiveScoreActivity.class));}));
  panel.addView(menuActionRow("Speed test","Measure ping, jitter, download and upload speed","activity",()->{dialog.dismiss();showSpeedTest();}));
  panel.addView(menuActionRow("Bug reports","View saved error codes and report problems","bug",()->{dialog.dismiss();showBugReports();}));
  panel.addView(menuActionRow("Settings","Playback, appearance, data and app controls","settings",()->{dialog.dismiss();showSettings();}));
  TextView socials=txt("CONNECT",10,ORANGE,true);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(27);slp.bottomMargin=dp(10);panel.addView(socials,slp);
  panel.addView(socialRow("x","X / Twitter","@iam_czek","https://x.com/iam_czek",dialog));
  panel.addView(socialRow("tiktok","TikTok","@iamczek","https://www.tiktok.com/@iamczek",dialog));
  panel.addView(socialRow("instagram","Instagram","@iamczek","https://www.instagram.com/iamczek",dialog));
  panel.addView(socialRow("mail","Email","laczekrsa@proton.me","mailto:laczekrsa@proton.me",dialog));
  LinearLayout share=row();share.setBackground(shape(0xff21170f,14));pad(share,14,14,14,14);LinearLayout.LayoutParams shareP=new LinearLayout.LayoutParams(-1,dp(54));shareP.topMargin=dp(12);panel.addView(share,shareP);NativeIcon shareIcon=new NativeIcon(this,"share",ORANGE);share.addView(shareIcon,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView shareText=txt("Share Zekify",13,Color.WHITE,true);LinearLayout.LayoutParams shareTextP=new LinearLayout.LayoutParams(0,-2,1);shareTextP.leftMargin=dp(12);share.addView(shareText,shareTextP);share.setOnClickListener(v->shareInstalledApk());
  View spacer=new View(this);panel.addView(spacer,new LinearLayout.LayoutParams(1,0,1));View divider=new View(this);divider.setBackgroundColor(0x22ffffff);LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,dp(1));vlp.topMargin=dp(18);vlp.bottomMargin=dp(14);panel.addView(divider,vlp);TextView foot=txt("ZEKIFY  ·  ALL SPORTS",9,MUTED,true);foot.setGravity(Gravity.CENTER);panel.addView(foot);
  dialog.setContentView(panel);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.width=(int)(getResources().getDisplayMetrics().widthPixels*.88f);a.height=-1;a.gravity=Gravity.RIGHT;a.dimAmount=.72f;w.setAttributes(a);}
 }
 
 private View menuActionRow(String title,String subtitle,String icon,Runnable action){LinearLayout item=col();item.setBackground(shape(0xff171717,18));pad(item,16,15,15,15);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);item.setLayoutParams(p);LinearLayout top=row();NativeIcon glyph=new NativeIcon(this,icon,ORANGE);top.addView(glyph,new LinearLayout.LayoutParams(dp(22),dp(22)));TextView name=txt(title,13,Color.WHITE,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(12);top.addView(name,np);top.addView(new NativeIcon(this,"next",MUTED),new LinearLayout.LayoutParams(dp(18),dp(18)));item.addView(top);TextView desc=txt(subtitle,10,MUTED,false);LinearLayout.LayoutParams dp0=new LinearLayout.LayoutParams(-1,-2);dp0.topMargin=dp(5);dp0.leftMargin=dp(34);item.addView(desc,dp0);item.setOnClickListener(v->action.run());return item;}
  private void recordBug(String source,Throwable error,String suppliedTrace){try{if(appPrefs==null)return;StringWriter sw=new StringWriter();if(suppliedTrace!=null)sw.append(suppliedTrace);else if(error!=null)error.printStackTrace(new PrintWriter(sw));String type=error==null?"Error":error.getClass().getSimpleName();String message=error==null?"Unknown error":String.valueOf(error.getMessage());String code="ZEK-"+Long.toString(System.currentTimeMillis(),36).toUpperCase(Locale.ROOT)+"-"+Integer.toHexString((source+type+message).hashCode()).toUpperCase(Locale.ROOT);JSONArray logs=new JSONArray();try{logs=new JSONArray(appPrefs.getString("bug_logs","[]"));}catch(Exception ignored){}JSONObject entry=new JSONObject();entry.put("code",code);entry.put("source",source);entry.put("type",type);entry.put("message",message);entry.put("time",System.currentTimeMillis());entry.put("details",sw.toString());logs.put(entry);while(logs.length()>50){JSONArray trimmed=new JSONArray();for(int i=1;i<logs.length();i++)trimmed.put(logs.get(i));logs=trimmed;}appPrefs.edit().putString("bug_logs",logs.toString()).apply();}catch(Exception ignored){}}
  private JSONArray getBugLogs(){try{return new JSONArray(appPrefs.getString("bug_logs","[]"));}catch(Exception ignored){return new JSONArray();}}
  private String bugText(JSONObject item){return "ZEKIFY BUG REPORT\\nCode: "+item.optString("code")+"\\nArea: "+item.optString("source")+"\\nType: "+item.optString("type")+"\\nMessage: "+item.optString("message")+"\\nTime: "+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new java.util.Date(item.optLong("time")))+"\\n\\n"+item.optString("details");}
  private void reportBug(JSONObject item){String body=bugText(item)+"\\n\\nPlease describe what you were doing when this happened:";try{Intent email=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:laczekrsa@proton.me"));email.putExtra(Intent.EXTRA_SUBJECT,"Zekify bug report · "+item.optString("code"));email.putExtra(Intent.EXTRA_TEXT,body);startActivity(Intent.createChooser(email,"Send bug report"));}catch(Exception e){Toast.makeText(this,"No email app is available. Copy the report instead.",Toast.LENGTH_LONG).show();copyBug(bugText(item));}}
  private void copyBug(String value){android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null){cm.setPrimaryClip(ClipData.newPlainText("Zekify bug report",value));Toast.makeText(this,"Bug report copied.",Toast.LENGTH_SHORT).show();}}
  private void showBugReports(){Dialog d=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff101010,20));pad(panel,18,16,18,16);LinearLayout head=row();head.addView(txt("BUG REPORTS",15,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));NativeIcon close=new NativeIcon(this,"close",Color.WHITE);head.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));close.setOnClickListener(v->d.dismiss());panel.addView(head);TextView note=txt("Saved locally on this device. Reports are only sent when you choose Report.",10,MUTED,false);LinearLayout.LayoutParams noteP=new LinearLayout.LayoutParams(-1,-2);noteP.topMargin=dp(5);panel.addView(note,noteP);ScrollView scroll=new ScrollView(this);LinearLayout list=col();LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(12);panel.addView(scroll,sp);scroll.addView(list);JSONArray logs=getBugLogs();for(int i=logs.length()-1;i>=0;i--){JSONObject entry=logs.optJSONObject(i);if(entry==null)continue;LinearLayout card=col();card.setBackground(shape(PANEL,13));pad(card,12,12,12,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(8);list.addView(card,cp);card.addView(txt(entry.optString("code"),12,ORANGE,true));card.addView(txt(entry.optString("source")+" · "+entry.optString("type"),10,Color.WHITE,true));TextView message=txt(entry.optString("message"),11,MUTED,false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(4);card.addView(message,mp);LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(9);card.addView(actions,ap);Button copy=btn("Copy code",false),full=btn("Copy log",false),send=btn("Report",true);actions.addView(copy,new LinearLayout.LayoutParams(0,dp(38),1));actions.addView(full,new LinearLayout.LayoutParams(0,dp(38),1));actions.addView(send,new LinearLayout.LayoutParams(0,dp(38),1));copy.setOnClickListener(v->copyBug(entry.optString("code")));full.setOnClickListener(v->copyBug(bugText(entry)));send.setOnClickListener(v->reportBug(entry));}if(logs.length()==0){TextView empty=txt("No errors have been logged. If something breaks, Zekify will save its error code here.",12,MUTED,false);pad(empty,8,24,8,24);list.addView(empty);}dialogWindow(d,panel,.92f,.78f);}
  private void dialogWindow(Dialog d,View contentView,float width,float height){d.setContentView(contentView);d.show();Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*width),(int)(getResources().getDisplayMetrics().heightPixels*height));}}
  private void clearTemporaryCache(){try{File dir=getCacheDir();File[] files=dir.listFiles();if(files!=null)for(File f:files)deleteRecursively(f);synchronized(badgeCache){badgeCache.evictAll();}appPrefs.edit().remove("matches_json").remove("matches_cached_at").apply();Toast.makeText(this,"Temporary cache cleared. Saved reminders and settings were kept.",Toast.LENGTH_LONG).show();if(matches.isEmpty())load();}catch(Exception e){recordBug("CACHE_CLEAR",e,null);Toast.makeText(this,"Could not clear all cache files.",Toast.LENGTH_LONG).show();}}
  private void deleteRecursively(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteRecursively(child);}if(!f.delete())f.deleteOnExit();}
  private void showSettings(){Dialog d=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff0b0b0b,28));pad(panel,20,20,20,20);LinearLayout head=row();TextView settingsTitle=txt("SETTINGS",15,ORANGE,true);settingsTitle.setLetterSpacing(.12f);head.addView(settingsTitle,new LinearLayout.LayoutParams(0,-2,1));NativeIcon close=new NativeIcon(this,"close",Color.WHITE);head.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));close.setOnClickListener(v->d.dismiss());panel.addView(head);TextView intro=txt("PERSONALISE YOUR ZEKIFY EXPERIENCE",9,MUTED,true);intro.setLetterSpacing(.08f);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(5);panel.addView(intro,ip);ScrollView scroll=new ScrollView(this);LinearLayout list=col();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=dp(10);panel.addView(scroll,lp);scroll.addView(list);String[][] settings={{"compact_cards","Compact match cards","Use tighter match cards"},{"show_badges","Show team badges","Display club badges on match cards"},{"haptics","Haptic feedback","Vibrate lightly when tapping match controls"},{"keep_awake","Keep screen awake","Prevent screen timeout while Zekify is open"},{"auto_reconnect","Auto-reconnect streams","Retry a stream after network returns"},{"confirm_exit","Confirm exit","Ask before leaving the app"},{"reduce_motion","Reduce animations","Use minimal screen transition effects"},{"data_saver","Reduce badge memory","Keep cached team badges lightweight"},{"kickoff_alert","Kickoff alerts","Include a kickoff-time notification with reminders"},{"auto_refresh","Refresh on network return","Reload matches when internet reconnects"}};for(String[] setting:settings){LinearLayout item=row();item.setBackground(shape(0xff151515,14));pad(item,14,11,12,11);LinearLayout.LayoutParams itemP=new LinearLayout.LayoutParams(-1,-2);itemP.bottomMargin=dp(7);list.addView(item,itemP);LinearLayout words=col();item.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(txt(setting[1],12,Color.WHITE,true));words.addView(txt(setting[2],9,MUTED,false));Switch toggle=new Switch(this);tintSwitch(toggle);toggle.setChecked(appPrefs.getBoolean("setting_"+setting[0],setting[0].equals("show_badges")||setting[0].equals("haptics")||setting[0].equals("keep_awake")||setting[0].equals("auto_reconnect")||setting[0].equals("auto_refresh")||setting[0].equals("kickoff_alert")));item.addView(toggle);toggle.setOnCheckedChangeListener((button,checked)->{appPrefs.edit().putBoolean("setting_"+setting[0],checked).apply();applySettings();});}View cache=menuActionRow("Clear cache","Remove temporary files, cached badges and match data","close",()->new AlertDialog.Builder(this).setTitle("Clear temporary cache?").setMessage("Saved reminders, settings and bug logs will not be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Clear cache",(x,y)->clearTemporaryCache()).show());LinearLayout.LayoutParams cacheP=new LinearLayout.LayoutParams(-1,-2);cacheP.topMargin=dp(8);panel.addView(cache,cacheP);View settingsAccent=new View(this);settingsAccent.setBackgroundColor(ORANGE);LinearLayout.LayoutParams settingsAccentP=new LinearLayout.LayoutParams(-1,dp(2));settingsAccentP.topMargin=dp(11);panel.addView(settingsAccent,1,settingsAccentP);dialogWindow(d,panel,.92f,.84f);}
  private void tintSwitch(Switch toggle){int[][] states=new int[][]{new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};int[] thumb=new int[]{ORANGE,0xff777777};int[] track=new int[]{0x99ff8a3d,0x66777777};android.content.res.ColorStateList thumbColors=new android.content.res.ColorStateList(states,thumb);android.content.res.ColorStateList trackColors=new android.content.res.ColorStateList(states,track);toggle.setThumbTintList(thumbColors);toggle.setTrackTintList(trackColors);}
  private boolean setting(String key,boolean fallback){return appPrefs.getBoolean("setting_"+key,fallback);}
  private void applySettings(){if(setting("keep_awake",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(root!=null)root.setHapticFeedbackEnabled(setting("haptics",true));}
  private void shareInstalledApk(){
  Toast.makeText(this,"Preparing the latest Zekify APK…",Toast.LENGTH_SHORT).show();
  shareIo.execute(()->{
   HttpURLConnection connection=null;
   try{
    File dir=new File(getCacheDir(),"shared");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create APK share folder");
    File apk=new File(dir,"Zekify-latest.apk");File temporary=new File(dir,"Zekify-latest.apk.part");boolean downloaded=false;
    try{
     connection=(HttpURLConnection)new URL("https://api.github.com/repos/iamcz3k/zekify/releases/latest").openConnection();
     connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","Zekify-Android");
     if(connection.getResponseCode()!=200)throw new IOException("Latest release lookup failed");
     JSONObject release;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);release=new JSONObject(out.toString("UTF-8"));}
     String version=release.optString("tag_name","latest").replaceAll("[^A-Za-z0-9._-]","");JSONArray assets=release.optJSONArray("assets");String downloadUrl="";
     if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject asset=assets.optJSONObject(i);if(asset==null)continue;if(asset.optString("name","").toLowerCase(Locale.ROOT).endsWith(".apk")){downloadUrl=asset.optString("browser_download_url","");if(!downloadUrl.isEmpty())break;}}
     if(downloadUrl.isEmpty())throw new IOException("Latest release has no APK asset");
     connection.disconnect();connection=null;connection=(HttpURLConnection)new URL(downloadUrl).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setInstanceFollowRedirects(true);connection.setRequestProperty("User-Agent","Zekify-Android");
     if(connection.getResponseCode()!=200)throw new IOException("Latest APK download failed");
     try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temporary)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
     if(!temporary.isFile()||temporary.length()<1024*1024)throw new IOException("Downloaded APK is incomplete");
     if(apk.exists())apk.delete();if(!temporary.renameTo(apk))throw new IOException("Could not finalize downloaded APK");apk.setReadable(true,false);downloaded=true;appPrefs.edit().putString("last_shared_apk_version",version).apply();
    }catch(Exception releaseError){
     if(temporary.exists())temporary.delete();File installed=new File(getApplicationInfo().sourceDir);
     if(!installed.isFile()||installed.length()<1024*1024)throw releaseError;
     if(apk.exists())apk.delete();try(InputStream in=new FileInputStream(installed);OutputStream out=new FileOutputStream(apk)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}apk.setReadable(true,false);
    }finally{if(connection!=null)connection.disconnect();}
    if(!apk.isFile()||apk.length()<1024*1024)throw new IOException("APK file is empty");
    Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
    Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/vnd.android.package-archive");send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newUri(getContentResolver(),"Zekify APK",uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    final String label=downloaded?"Latest release APK ready to share.":"Latest release unavailable. Shared the installed APK instead.";
    runOnUiThread(()->{Toast.makeText(this,label,Toast.LENGTH_SHORT).show();try{startActivity(Intent.createChooser(send,"Share Zekify APK"));}catch(Exception e){Toast.makeText(this,"No app can share APK files on this device.",Toast.LENGTH_LONG).show();}});
   }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Could not prepare the APK: "+(e.getMessage()==null?"unknown error":e.getMessage()),Toast.LENGTH_LONG).show());}
  });
 }
 private View socialRow(String icon,String label,String value,String url,Dialog dialog){
  LinearLayout item=row();item.setBackground(shape(0xff191919,14));pad(item,13,11,13,11);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(62));p.bottomMargin=dp(8);item.setLayoutParams(p);
  NativeIcon mark=new NativeIcon(this,icon,Color.WHITE);item.addView(mark,new LinearLayout.LayoutParams(dp(25),dp(25)));LinearLayout words=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(13);item.addView(words,wp);words.addView(txt(label,13,Color.WHITE,true));TextView val=txt(value,10,MUTED,false);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(3);words.addView(val,vp);NativeIcon arrow=new NativeIcon(this,"next",ORANGE);item.addView(arrow,new LinearLayout.LayoutParams(dp(19),dp(19)));
  item.setOnClickListener(v->{try{Intent i=url.startsWith("mailto:")?new Intent(Intent.ACTION_SENDTO,Uri.parse(url)):new Intent(Intent.ACTION_VIEW,Uri.parse(url));startActivity(i);}catch(Exception ignored){} });return item;
 }
 private JSONObject request(String path)throws Exception{
  Exception last=null;
  for(int attempt=0;attempt<2;attempt++){
   HttpURLConnection connection=null;
   try{
    connection=(HttpURLConnection)new URL(API+path).openConnection();
    connection.setConnectTimeout(8000);connection.setReadTimeout(12000);connection.setUseCaches(false);
    connection.setRequestProperty("Accept","application/json");
    int code=connection.getResponseCode();
    InputStream stream=code>=400?connection.getErrorStream():connection.getInputStream();
    ByteArrayOutputStream output=new ByteArrayOutputStream();
    if(stream!=null){try(InputStream in=stream){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)output.write(buffer,0,count);}}
    if(code>=500||code==429){last=new IOException("Temporary service error "+code);if(attempt==0){try{Thread.sleep(350);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}continue;}throw last;}
    if(code>=400)throw new IOException("Service error "+code);
    return new JSONObject(output.toString("UTF-8"));
   }catch(IOException e){last=e;if(attempt==0&&!(e.getMessage()!=null&&e.getMessage().startsWith("Service error "))){try{Thread.sleep(350);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}}else throw e;}
   finally{if(connection!=null)connection.disconnect();}
  }
  throw last==null?new IOException("Request failed"):last;
 }
 private void load(){
  final int generation=++matchesGeneration;final String sport=selectedSport;matchesLoading.set(true);
  apiIo.execute(()->{
   try{
    String cached=appPrefs.getString("matches_json_"+sport,"");
    if(!cached.isEmpty()){try{JSONArray cachedArray=new JSONArray(cached);ArrayList<JSONObject> cachedMatches=new ArrayList<>();for(int i=0;i<cachedArray.length();i++)cachedMatches.add(cachedArray.getJSONObject(i));runOnUiThread(()->{if(generation==matchesGeneration&&matches.isEmpty()){matches.clear();matches.addAll(cachedMatches);selectDefaultTabForMatches();renderLeagues();render();}});}catch(Exception ignored){}}
    JSONObject p=request("/api/public/football-streams?mode=matches&sport="+URLEncoder.encode(sport,"UTF-8"));
    JSONArray a=p.optJSONArray("data");if(!p.optBoolean("success")||a==null)throw new Exception("Unexpected match response");
    ArrayList<JSONObject> fresh=new ArrayList<>();for(int i=0;i<a.length();i++)fresh.add(a.getJSONObject(i));
    appPrefs.edit().putString("matches_json_"+sport,a.toString()).putLong("matches_cached_at_"+sport,System.currentTimeMillis()).apply();
    runOnUiThread(()->{if(generation!=matchesGeneration)return;matchesLoading.set(false);matches.clear();matches.addAll(fresh);selectDefaultTabForMatches();renderLeagues();render();openPendingMatchIfAvailable();hideStartupSplash();});
   }catch(Exception e){
    recordBug("MATCH_LOAD_"+sport,e,null);String cached=appPrefs.getString("matches_json_"+sport,"");ArrayList<JSONObject> fallback=new ArrayList<>();
    try{JSONArray a=new JSONArray(cached);for(int i=0;i<a.length();i++)fallback.add(a.getJSONObject(i));}catch(Exception ignored){}
    runOnUiThread(()->{if(generation!=matchesGeneration)return;matchesLoading.set(false);hideStartupSplash();if(!fallback.isEmpty()){matches.clear();matches.addAll(fallback);selectDefaultTabForMatches();renderLeagues();render();openPendingMatchIfAvailable();Toast.makeText(this,"Showing saved "+sport+" matches. Refresh when connected.",Toast.LENGTH_SHORT).show();}else if(content!=null){content.removeAllViews();TextView err=txt("Unable to load "+sport+" matches. Tap to retry.",14,Color.WHITE,false);pad(err,12,25,12,25);err.setOnClickListener(v->load());content.addView(err);}});
   }
  });
 }
 private static final String[][] SPORTS={
  {"football","Football"},{"motor-sports","Motorsport"},{"rugby","Rugby"},{"american-football","Am. Football"},
  {"australian-football","Aussie Rules"},{"cricket","Cricket"},{"hockey","Hockey"},{"badminton","Badminton"},
  {"baseball","Baseball"},{"volleyball","Volleyball"},{"fight","Fighting"},{"cycling","Cycling"},
  {"handball","Handball"},{"basketball","Basketball"},{"tennis","Tennis"}
 };
 private String sportLabel(){for(String[] sport:SPORTS)if(sport[0].equals(selectedSport))return sport[1].toUpperCase(Locale.ROOT);return "SPORTS";}
 private String sportAsset(String key){switch(key){
  case "football":return "football";case "motor-sports":return "motor-sports";case "rugby":return "rugby";
  case "american-football":return "american-football";case "australian-football":return "australian-football";
  case "cricket":return "cricket";case "hockey":return "hockey";case "badminton":return "badminton";
  case "baseball":return "baseball";case "volleyball":return "volleyball";case "fight":return "fight";
  case "cycling":return "cycling";case "handball":return "handball";case "basketball":return "basketball";
  case "tennis":return "tennis";default:return "football";
 }}
 private void applySportIcon(ImageView target,Bitmap bitmap,String key){if(target==null||bitmap==null||!key.equals(target.getTag()))return;target.setImageBitmap(bitmap);if(target.getParent() instanceof ViewGroup){ViewGroup parent=(ViewGroup)target.getParent();if(parent.getChildCount()>1)parent.getChildAt(0).setVisibility(View.GONE);}}
 private void loadSportIcon(ImageView target,String key){
  if(target==null)return;target.setTag(key);Bitmap cached;synchronized(sportIconCache){cached=sportIconCache.get(key);}
  if(cached!=null){applySportIcon(target,cached,key);return;}
  imageIo.execute(()->{Bitmap bitmap=null;try(InputStream input=getAssets().open("sports/"+sportAsset(key)+".svg")){
    SVG svg=SVG.getFromInputStream(input);svg.setDocumentWidth(96f);svg.setDocumentHeight(96f);
    bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);svg.renderToCanvas(canvas);
    synchronized(sportIconCache){sportIconCache.put(key,bitmap);}
  }catch(Exception error){android.util.Log.w("Zekify","Sport icon unavailable: "+key,error);}
  final Bitmap result=bitmap;if(result!=null&&!isFinishing())runOnUiThread(()->{if(key.equals(target.getTag()))applySportIcon(target,result,key);});
  });
 }
 private String sportGlyph(String key){switch(key){
  case "football":return "⚽";case "basketball":return "🏀";case "tennis":return "🎾";case "baseball":return "⚾";
  case "cricket":return "🏏";case "motor-sports":return "🏎";case "rugby":return "🏉";case "american-football":return "🏈";
  case "australian-football":return "🏉";case "hockey":return "🏒";case "badminton":return "🏸";case "volleyball":return "🏐";
  case "fight":return "🥊";case "cycling":return "🚴";case "handball":return "🤾";default:return "🏆";
 }}
 private void renderSports(){
  if(sportRow==null)return;sportRow.removeAllViews();
  for(String[] sport:SPORTS){final String key=sport[0],label=sport[1];boolean active=key.equals(selectedSport);
   LinearLayout chip=row();chip.setGravity(Gravity.CENTER);chip.setBackground(shape(active?0xff332014:0xff121212,18));pad(chip,12,0,14,0);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(8);sportRow.addView(chip,p);
   FrameLayout iconFrame=new FrameLayout(this);LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(28),dp(28));chip.addView(iconFrame,iconP);
   TextView glyph=txt(sportGlyph(key),18,active?ORANGE:Color.WHITE,true);glyph.setGravity(Gravity.CENTER);iconFrame.addView(glyph,new FrameLayout.LayoutParams(-1,-1));
   ImageView sportImage=new ImageView(this);sportImage.setScaleType(ImageView.ScaleType.FIT_CENTER);iconFrame.addView(sportImage,new FrameLayout.LayoutParams(-1,-1));loadSportIcon(sportImage,key);
   TextView title=txt(label,11,active?Color.WHITE:0xffb9b9b9,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.leftMargin=dp(7);chip.addView(title,tp);
   chip.setContentDescription(label);chip.setOnClickListener(v->{v.animate().scaleX(.94f).scaleY(.94f).setDuration(65).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(130).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start()).start();selectSport(key);});
  }
 }
 private void selectSport(String sport){
  if(sport==null||sport.equals(selectedSport))return;
  selectedSport=sport;matchesGeneration++;matchesLoading.set(false);league="All";selected=null;detail=null;selectedSources=null;
  if(sportTitle!=null)sportTitle.setText(sportLabel());matches.clear();renderSports();renderLeagues();render();
  if(!setting("reduce_motion",false)&&content!=null){content.setAlpha(.65f);content.animate().alpha(1f).setDuration(150).start();}
  load();
 }
 private String firstNonEmpty(JSONObject source,String... keys){if(source==null)return "";for(String key:keys){String value=source.optString(key,"").trim();if(!value.isEmpty()&&!"null".equalsIgnoreCase(value))return value;}return "";}
 private String team(JSONObject m,String side){if(m==null)return "";JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);String name=t==null?"":t.optString("name","").trim();return name.equalsIgnoreCase("null")?"":name;}
 private boolean hasTeamPair(JSONObject m){return !team(m,"home").isEmpty()&&!team(m,"away").isEmpty();}
 private boolean eventStyle(JSONObject m){String category=m==null?"":m.optString("category","");return category.equals("cycling")||category.equals("motor-sports")||category.equals("golf")||category.equals("darts")||!hasTeamPair(m);}
 private String matchTitle(JSONObject m){String title=firstNonEmpty(m,"eventName","title","name");if(!title.isEmpty())return title;String h=team(m,"home"),a=team(m,"away");return !h.isEmpty()&&!a.isEmpty()?h+" vs "+a:leagueName(m);}
 private String eventBadgeUrl(JSONObject m){String u=firstNonEmpty(m,"eventBadge","leagueLogo","leagueBadge","poster");if(u.startsWith("/"))u=API+u;return u;}
 private String leagueName(JSONObject m){String s=m.optString("league","");return s.trim().isEmpty()?"Other competitions":s.trim();}
 private void renderLeagues(){
  if(leagueRow==null)return;leagueRow.removeAllViews();LinkedHashSet<String> ls=new LinkedHashSet<>();ls.add("All");for(JSONObject m:matches)ls.add(leagueName(m));
  for(String value:ls){final String item=value;boolean active=item.equals(league);TextView chip=txt(item,10,active?Color.WHITE:0xffb7b7b7,true);chip.setGravity(Gravity.CENTER);chip.setBackground(shape(active?0xff302016:0xff121212,18));pad(chip,13,0,13,0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(34));p.rightMargin=dp(7);leagueRow.addView(chip,p);chip.setOnClickListener(v->{league=item;renderLeagues();render();});}
 }
  private void render(){
   try{renderContent();}
   catch(RuntimeException error){
    if(appPrefs!=null){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));appPrefs.edit().putString("last_render_error",trace.toString()).putLong("last_render_error_at",System.currentTimeMillis()).apply();recordBug("MATCH_RENDER",error,trace.toString());}
    if(content!=null){content.removeAllViews();TextView failed=txt("Some match data could not be displayed. Tap to reload.",13,MUTED,false);pad(failed,12,28,12,28);failed.setOnClickListener(v->load());content.addView(failed);}
    android.util.Log.e("Zekify","Match rendering failed",error);
   }
  }
  private boolean isLiveMatch(JSONObject match){String s=match==null?"":match.optString("status","").trim().toLowerCase(Locale.ROOT);return s.equals("live")||s.equals("in play")||s.equals("live now")||s.equals("1h")||s.equals("2h")||s.equals("ht");}
  private void selectDefaultTabForMatches(){boolean hasLive=false;for(JSONObject match:matches){if(isLiveMatch(match)){hasLive=true;break;}}String preferred=hasLive?"live":"upcoming";if(!preferred.equals(tab)){tab=preferred;if(root!=null&&selected==null)home();}}
  private void renderContent(){
   if(selected!=null){renderMatchScreen();return;}
   pruneStartedScheduledMatches();
   if(content==null)return;content.removeAllViews();int count=0;
   for(JSONObject match:matches){if(match==null)continue;try{String status=match.optString("status","upcoming").trim().toLowerCase(Locale.ROOT);if(status.equals("not started")||status.equals("scheduled")||status.equals("ns")||status.equals("tbd"))status="upcoming";if(status.equals("in play")||status.equals("1h")||status.equals("2h")||status.equals("live now"))status="live";if(!status.equals(tab)||(!league.equals("All")&&!league.equals(leagueName(match))))continue;String hay=(match.optString("title")+" "+matchTitle(match)+" "+leagueName(match)+" "+team(match,"home")+" "+team(match,"away")).toLowerCase(Locale.ROOT);if(!hay.contains(query))continue;if(count>=80)continue;content.addView(matchCard(match));count++;}catch(RuntimeException cardError){android.util.Log.w("Zekify","Skipped malformed match card",cardError);}}
   if(count==0){TextView empty=txt("No "+tab+" matches match your filters.",14,MUTED,false);pad(empty,12,36,12,36);content.addView(empty);}if(matchesScroll!=null)matchesScroll.postDelayed(this::loadVisibleBadges,80L);
  }
  private void renderMatchScreen(){
   LinearLayout screen=col();screen.setBackgroundColor(BG);
   LinearLayout header=row();pad(header,14,9,16,9);
   NativeIcon back=new NativeIcon(this,"back",Color.WHITE);header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));back.setOnClickListener(v->homeAndRender());
   LinearLayout brand=row();brand.addView(new Ball(this),new LinearLayout.LayoutParams(dp(30),dp(30)));TextView brandName=txt("ZEKIFY",17,Color.WHITE,true);brandName.setLetterSpacing(.12f);LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(0,-2,1);brandP.leftMargin=dp(9);brand.addView(brandName,brandP);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
   NativeIcon menuIcon=new NativeIcon(this,"menu",Color.WHITE);header.addView(menuIcon,new LinearLayout.LayoutParams(dp(42),dp(42)));menuIcon.setOnClickListener(v->menu());screen.addView(header);
   View divider=new View(this);divider.setBackgroundColor(0x22ffffff);screen.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
   View mini=buildInlinePlayer();if(mini!=null){LinearLayout.LayoutParams miniP=new LinearLayout.LayoutParams(-1,-2);miniP.setMargins(dp(14),dp(12),dp(14),dp(8));screen.addView(mini,miniP);}
   TextView heading=txt("RECOMMENDED MATCHES",10,ORANGE,true);pad(heading,18,12,18,8);screen.addView(heading);
   ScrollView recommendedScroll=new ScrollView(this);recommendedScroll.setFillViewport(false);recommendedScroll.setVerticalScrollBarEnabled(false);
   LinearLayout recommended=col();pad(recommended,14,2,14,18);ArrayList<JSONObject> candidates=new ArrayList<>();for(JSONObject match:matches){if(!match.optString("id").equals(selected.optString("id")))candidates.add(match);}boolean hasPopular=false;for(JSONObject match:candidates)if(popularScore(match)>0){hasPopular=true;break;}if(hasPopular)Collections.sort(candidates,(a,b)->Integer.compare(popularScore(b),popularScore(a)));int shown=0;
   for(JSONObject match:candidates){if(shown>=4)break;boolean featured=hasPopular&&popularScore(match)>0;recommended.addView(matchCard(match,featured));shown++;}
   if(shown==0){TextView empty=txt("No other matches available yet.",12,MUTED,false);pad(empty,8,18,8,18);recommended.addView(empty);}
   recommendedScroll.addView(recommended);screen.addView(recommendedScroll,new LinearLayout.LayoutParams(-1,0,1));
   root=screen;setContentView(screen);if(!setting("reduce_motion",false)){screen.setAlpha(0f);screen.animate().alpha(1f).setDuration(180L).start();}
  }
 private View matchCard(JSONObject m){return matchCard(m,false);}
 private View matchCard(JSONObject m,boolean featured){
  LinearLayout card=col();card.setBackground(shape(featured?0xff17120e:0xff101010,16));card.setElevation(dp(1));pad(card,14,13,14,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);card.setLayoutParams(cp);
  String matchStatus=m.optString("status","").trim().toLowerCase(Locale.ROOT);boolean live=matchStatus.equals("live")||matchStatus.equals("in play")||matchStatus.equals("live now")||matchStatus.equals("1h")||matchStatus.equals("2h")||matchStatus.equals("ht");
  LinearLayout meta=row();TextView leagueLabel=txt(leagueName(m).toUpperCase(Locale.ROOT),9,0xffb5a18f,true);leagueLabel.setLetterSpacing(.09f);meta.addView(leagueLabel,new LinearLayout.LayoutParams(0,-2,1));
  if(featured){TextView popular=txt("FEATURED",8,ORANGE,true);popular.setLetterSpacing(.08f);popular.setBackground(shape(0xff302016,20));pad(popular,8,4,8,4);meta.addView(popular);}
  if(live){View dot=new View(this);GradientDrawable dotBg=new GradientDrawable();dotBg.setColor(0xffff625f);dotBg.setCornerRadius(dp(8));dot.setBackground(dotBg);LinearLayout.LayoutParams dotP=new LinearLayout.LayoutParams(dp(6),dp(6));dotP.rightMargin=dp(6);meta.addView(dot,dotP);}
  TextView timer=txt("",10,live?0xffff7168:ORANGE,true);meta.addView(timer);bindMatchTimer(timer,m.optLong("date",0),live);
  if(!live){NativeIcon bell=new NativeIcon(this,isMatchScheduled(m)?"bell-on":"bell",isMatchScheduled(m)?ORANGE:MUTED);bell.setContentDescription(isMatchScheduled(m)?"Remove match reminder":"Notify me about this match");LinearLayout.LayoutParams bellP=new LinearLayout.LayoutParams(dp(28),dp(28));bellP.leftMargin=dp(5);meta.addView(bell,bellP);bell.setOnClickListener(v->{toggleMatchReminder(m);bell.setIconColor(isMatchScheduled(m)?ORANGE:MUTED);bell.setContentDescription(isMatchScheduled(m)?"Remove match reminder":"Notify me about this match");});}
  card.addView(meta);TextView kickoffDate=txt(date(m.optLong("date",0)),10,MUTED,false);LinearLayout.LayoutParams dateP=new LinearLayout.LayoutParams(-1,-2);dateP.topMargin=dp(4);card.addView(kickoffDate,dateP);
  if(!eventStyle(m)){LinearLayout teams=row();pad(teams,0,17,0,16);teams.setGravity(Gravity.CENTER_VERTICAL);teams.addView(teamBadge(m,"home"));TextView home=txt(team(m,"home"),13,Color.WHITE,true);home.setMaxLines(2);home.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(9);teams.addView(home,hp);
   TextView vs=txt("VS",9,ORANGE,true);vs.setLetterSpacing(.12f);vs.setBackground(shape(0xff241a13,8));pad(vs,8,5,8,5);teams.addView(vs);TextView away=txt(team(m,"away"),13,Color.WHITE,true);away.setMaxLines(2);away.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(9);teams.addView(away,ap);teams.addView(teamBadge(m,"away"));card.addView(teams);
  }else{LinearLayout event=row();pad(event,0,15,0,15);FrameLayout badge=eventBadge(m);event.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout eventText=col();LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,-2,1);ep.leftMargin=dp(12);event.addView(eventText,ep);eventText.addView(txt(matchTitle(m),14,Color.WHITE,true));String sub=firstNonEmpty(m,"eventSubtitle","league","category");if(!sub.isEmpty()){TextView subtitle=txt(sub,10,MUTED,false);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(4);eventText.addView(subtitle,subp);}card.addView(event);}
  View sep=new View(this);sep.setBackgroundColor(0x20ffffff);card.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));LinearLayout foot=row();pad(foot,0,9,0,0);
  if(live){TextView watch=txt("WATCH MATCH",10,Color.WHITE,true);watch.setLetterSpacing(.05f);watch.setGravity(Gravity.CENTER);watch.setBackground(shape(0xffb8322e,9));pad(watch,13,8,13,8);foot.addView(watch,new LinearLayout.LayoutParams(-2,dp(34)));watch.setOnClickListener(v->{if(setting("haptics",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);selectMatch(m);});}
  else{foot.addView(txt("VIEW MATCH DETAILS",9,0xffa9a9a9,true),new LinearLayout.LayoutParams(0,-2,1));foot.addView(new NativeIcon(this,"next",MUTED),new LinearLayout.LayoutParams(dp(17),dp(17)));}card.addView(foot);
  card.setOnClickListener(v->{if(setting("haptics",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);selectMatch(m);});if(setting("compact_cards",false)){card.setPadding(dp(11),dp(9),dp(11),dp(8));cp.bottomMargin=dp(6);}
  if(!setting("reduce_motion",false)){card.setAlpha(0f);card.setTranslationY(dp(4));card.animate().alpha(1f).translationY(0f).setDuration(160L).start();}return card;
 }
 private FrameLayout eventBadge(JSONObject m){
  FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(0xff252525,14));
  TextView fallback=txt(sportGlyph(m.optString("category",selectedSport)),22,ORANGE,true);fallback.setGravity(Gravity.CENTER);frame.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
  String url=eventBadgeUrl(m);if(url.isEmpty()||!setting("show_badges",true))return frame;
  frame.setTag(url);Bitmap cached;synchronized(badgeCache){cached=badgeCache.get(url);}if(cached!=null)showBadge(frame,cached,url);return frame;
 }
 private View teamBadge(JSONObject m,String side){
  FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(0xff252525,50));frame.setElevation(dp(1));
  String name=team(m,side);TextView fallback=txt(name.isEmpty()?"•":name.substring(0,1).toUpperCase(Locale.ROOT),14,ORANGE,true);fallback.setGravity(Gravity.CENTER);frame.addView(fallback,new FrameLayout.LayoutParams(dp(34),dp(34)));
  JSONObject teams=m.optJSONObject("teams"),teamData=teams==null?null:teams.optJSONObject(side);
  String url=teamData==null?"":teamData.optString("badge","");
  if(url.isEmpty()||!setting("show_badges",true))return frame;
  if(url.startsWith("/"))url=API+url;
  final String badgeUrl=url;frame.setTag(badgeUrl);
  Bitmap cached;synchronized(badgeCache){cached=badgeCache.get(badgeUrl);}
  if(cached!=null)showBadge(frame,cached,badgeUrl);
  return frame;
 }
 private void loadVisibleBadges(){
  ScrollView scroll=matchesScroll;if(scroll==null||content==null||scroll.getHeight()<=0)return;
  int top=scroll.getScrollY(),bottom=top+scroll.getHeight();
  for(int i=0;i<content.getChildCount();i++){View card=content.getChildAt(i);if(card.getBottom()>top&&card.getTop()<bottom)loadBadgesIn(card);}
 }
 private void loadBadgesIn(View view){
  if(view instanceof FrameLayout){Object tag=view.getTag();if(tag instanceof String){String url=(String)tag;if(url.startsWith("http"))requestBadge((FrameLayout)view,url);}}
  if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)loadBadgesIn(group.getChildAt(i));}
 }
 private void requestBadge(FrameLayout frame,String badgeUrl){
  if(frame==null||!badgeUrl.equals(frame.getTag()))return;
  Bitmap cached;synchronized(badgeCache){cached=badgeCache.get(badgeUrl);}if(cached!=null){showBadge(frame,cached,badgeUrl);return;}
  badgeWaiters.computeIfAbsent(badgeUrl,k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(new java.lang.ref.WeakReference<>(frame));
  if(!badgeRequests.add(badgeUrl))return;
  imageIo.execute(()->{
   Bitmap bitmap=null;HttpURLConnection connection=null;
   try{
    File badgeDir=getDir("badges",MODE_PRIVATE);File badgeFile=new File(badgeDir,Integer.toHexString(badgeUrl.hashCode())+".png");
    if(badgeFile.exists())bitmap=BitmapFactory.decodeFile(badgeFile.getAbsolutePath());
    if(bitmap==null){connection=(HttpURLConnection)new URL(badgeUrl).openConnection();connection.setConnectTimeout(5000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(true);connection.setUseCaches(true);connection.setRequestProperty("User-Agent","Zekify/2.0 Android");int code=connection.getResponseCode();if(code>=200&&code<300){try(InputStream input=connection.getInputStream()){bitmap=BitmapFactory.decodeStream(input);}}}
    if(bitmap!=null){if(setting("data_saver",false)){Bitmap scaled=Bitmap.createScaledBitmap(bitmap,48,48,true);if(scaled!=bitmap)bitmap=scaled;}synchronized(badgeCache){badgeCache.put(badgeUrl,bitmap);}if(!badgeFile.exists()){try(FileOutputStream out=new FileOutputStream(badgeFile)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception ignored){}}}
   }catch(Exception ignored){}
   finally{if(connection!=null)connection.disconnect();badgeRequests.remove(badgeUrl);}
   final Bitmap result=bitmap;java.util.List<java.lang.ref.WeakReference<FrameLayout>> waiting=badgeWaiters.remove(badgeUrl);
   if(result!=null&&waiting!=null&&!isFinishing())runOnUiThread(()->{for(java.lang.ref.WeakReference<FrameLayout> ref:waiting){FrameLayout target=ref.get();if(target!=null)showBadge(target,result,badgeUrl);}});
  });
 }
 private void showBadge(FrameLayout frame,Bitmap bitmap,String expectedUrl){
  if(bitmap==null||frame==null||!expectedUrl.equals(frame.getTag()))return;
  frame.removeAllViews();ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  frame.addView(image,new FrameLayout.LayoutParams(dp(34),dp(34)));
 }
 private String date(long v){if(v<=0)return "Kickoff TBC";return new java.text.SimpleDateFormat("EEE, d MMM · HH:mm",Locale.getDefault()).format(new Date(v));}
 private String timerText(long kickoff,boolean live){long now=System.currentTimeMillis();long diff=live?Math.max(0,now-kickoff):Math.max(0,kickoff-now);long sec=diff/1000;long days=sec/86400;long hours=(sec%86400)/3600;long mins=(sec%3600)/60;long seconds=sec%60;String value=String.format(Locale.getDefault(),"%02d:%02d:%02d",hours,mins,seconds);return live?"LIVE · "+(days>0?days+"d ":"")+value:"KICKOFF IN · "+(days>0?days+"d ":"")+value;}
 private void bindMatchTimer(TextView view,long kickoff,boolean live){Runnable tick=new Runnable(){public void run(){if(isFinishing()||view.getWindowToken()==null)return;view.setText(timerText(kickoff,live));view.postDelayed(this,1000);}};view.setTag(tick);view.post(tick);}
 private void selectMatch(JSONObject m){
  if(selected!=null&&selected.optString("id").equals(m.optString("id")))return;
  stopInlinePlayback();selected=m;detail=null;selectedSources=null;selectedSourceIndex=0;inlineLoading=true;render();
  final String sport=selectedSport;apiIo.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=detail&sport="+URLEncoder.encode(sport,"UTF-8")+"&id="+URLEncoder.encode(m.optString("id"),"UTF-8"));JSONObject d=p.optJSONObject("data");JSONArray src=d==null?null:d.optJSONArray("sources");runOnUiThread(()->{if(selected==null||!selected.optString("id").equals(m.optString("id")))return;detail=d;selectedSources=src;inlineLoading=false;render();if(src!=null&&src.length()>0)startInlineSource(0);});}catch(Exception e){runOnUiThread(()->{inlineLoading=false;if(inlineStatus!=null)inlineStatus.setText("Unable to load streams. Tap retry.");render();});}});
 }
 private View buildInlinePlayer(){
  LinearLayout card=col();card.setBackground(shape(0xff101010,18));card.setElevation(dp(2));pad(card,14,13,14,13);
  View accent=new View(this);accent.setBackgroundColor(ORANGE);card.addView(accent,new LinearLayout.LayoutParams(-1,dp(2)));
  LinearLayout top=row();LinearLayout.LayoutParams topP=new LinearLayout.LayoutParams(-1,dp(34));topP.topMargin=dp(5);top.addView(new Ball(this),new LinearLayout.LayoutParams(dp(19),dp(19)));TextView playing=txt("NOW PLAYING",10,ORANGE,true);playing.setLetterSpacing(.13f);LinearLayout.LayoutParams playingP=new LinearLayout.LayoutParams(0,-2,1);playingP.leftMargin=dp(8);top.addView(playing,playingP);
  NativeIcon close=new NativeIcon(this,"close",MUTED);top.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));close.setContentDescription("Close mini player");close.setOnClickListener(v->homeAndRender());card.addView(top,topP);
  TextView title=txt(matchTitle(selected),15,Color.WHITE,true);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(3);card.addView(title,tp);
  TextView subtitle=txt("LIVE STREAM  ·  SELECT A SOURCE BELOW",9,MUTED,true);subtitle.setLetterSpacing(.07f);LinearLayout.LayoutParams subP=new LinearLayout.LayoutParams(-1,-2);subP.topMargin=dp(3);card.addView(subtitle,subP);
  inlineFrame=new FrameLayout(this);inlineFrame.setBackground(shape(Color.BLACK,14));inlineFrame.setClipToOutline(true);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(205));fp.topMargin=dp(11);card.addView(inlineFrame,fp);
  if(inlinePlayerView==null){inlinePlayerView=new PlayerView(this);inlinePlayerView.setUseController(false);inlinePlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);}
  ensureInlineWebView();
  if(inlinePlayerView.getParent()!=null)((android.view.ViewGroup)inlinePlayerView.getParent()).removeView(inlinePlayerView);
  if(inlineWebView.getParent()!=null)((android.view.ViewGroup)inlineWebView.getParent()).removeView(inlineWebView);
  if(inlinePlayer!=null){inlinePlayerView.setPlayer(inlinePlayer);inlineFrame.addView(inlinePlayerView,new FrameLayout.LayoutParams(-1,-1));}
  else if(inlineWebView.getVisibility()==View.VISIBLE){inlineFrame.addView(inlineWebView,new FrameLayout.LayoutParams(-1,-1));}
  LinearLayout statusBar=row();statusBar.setBackground(shape(0xff171717,10));pad(statusBar,11,8,11,8);LinearLayout.LayoutParams statusP=new LinearLayout.LayoutParams(-1,-2);statusP.topMargin=dp(9);card.addView(statusBar,statusP);
  View statusDot=new View(this);statusDot.setBackground(shape(ORANGE,50));statusBar.addView(statusDot,new LinearLayout.LayoutParams(dp(6),dp(6)));
  inlineStatus=txt(inlineLoading?"Loading available streams…":(selectedSources==null||selectedSources.length()==0?"No streams available":"Connecting to stream…"),10,MUTED,false);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(0,-2,1);st.leftMargin=dp(8);statusBar.addView(inlineStatus,st);
  TextView sourcesLabel=txt("STREAM SOURCES",9,MUTED,true);sourcesLabel.setLetterSpacing(.1f);LinearLayout.LayoutParams sourcesLabelP=new LinearLayout.LayoutParams(-1,-2);sourcesLabelP.topMargin=dp(11);card.addView(sourcesLabel,sourcesLabelP);
  HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);inlineSources=row();pad(inlineSources,0,7,0,0);horizontal.addView(inlineSources);card.addView(horizontal);
  if(selectedSources!=null){for(int i=0;i<Math.min(4,selectedSources.length());i++){JSONObject s=selectedSources.optJSONObject(i);if(s==null)continue;final int ix=i;Button chip=btn(s.optString("source","Source "+(i+1)),i==selectedSourceIndex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.rightMargin=dp(7);inlineSources.addView(chip,cp);chip.setOnClickListener(v->{selectedSourceIndex=ix;startInlineSource(ix);});}}
  LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(9);card.addView(actions,ap);
  Button full=btn("Continue in fullscreen",true);actions.addView(full,new LinearLayout.LayoutParams(0,dp(44),1));full.setOnClickListener(v->launchFullscreen());
  Button retry=btn("Retry",false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(42),1);rp.leftMargin=dp(8);actions.addView(retry,rp);retry.setOnClickListener(v->startInlineSource(selectedSourceIndex));
  return card;
 }
 private void ensureInlineWebView(){
  if(inlineWebView!=null)return;
  inlineWebView=new WebView(this);inlineWebView.setBackgroundColor(Color.BLACK);
  inlineWebView.getSettings().setJavaScriptEnabled(true);inlineWebView.getSettings().setDomStorageEnabled(true);
  inlineWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);inlineWebView.setLongClickable(false);
  inlineWebView.setOnLongClickListener(v->true);
  inlineWebView.setWebViewClient(new WebViewClient(){
   @Override public void onPageStarted(WebView view,String url,Bitmap favicon){
    if(url==null||"about:blank".equals(url)||!url.equals(view.getUrl()))return;
    final int generation=inlineLoadGeneration;inlineWebGeneration=generation;
    if(inlineStatus!=null)inlineStatus.setText("Connecting to stream source…");
    if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);
    inlineWebTimeout=()->{if(generation==inlineLoadGeneration&&inlineWebGeneration==generation&&inlineStatus!=null)inlineStatus.setText("Source timed out. Tap Retry or choose another source.");};
    inlineNetworkHandler.postDelayed(inlineWebTimeout,25000L);
   }
   @Override public void onPageFinished(WebView view,String url){
    if(url==null||"about:blank".equals(url)||!url.equals(view.getUrl())||inlineWebGeneration!=inlineLoadGeneration)return;
    if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);
    if(inlineStatus!=null)inlineStatus.setText("Source page opened · waiting for video…");
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
    if(request.isForMainFrame()&&request.getUrl()!=null&&request.getUrl().toString().equals(view.getUrl())&&inlineWebGeneration==inlineLoadGeneration){
     if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);
     if(inlineStatus!=null)inlineStatus.setText("Source failed to load. Tap Retry or choose another source.");
    }
   }
   @Override public boolean onRenderProcessGone(WebView view,android.webkit.RenderProcessGoneDetail detail){
    if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
    view.destroy();if(inlineWebView==view)inlineWebView=null;
    inlineLoadGeneration++;inlineWebGeneration=inlineLoadGeneration;
    if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);
    if(inlineStatus!=null)inlineStatus.setText("Stream player stopped. Tap Retry to restart this source.");
    return true;
   }
  });
 }
 private boolean isFctvHlsRelay(String url){try{Uri uri=Uri.parse(url);String path=uri.getPath();return path!=null&&path.equals("/api/public/fctv-hls")&&!uri.getQueryParameter("slot").isEmpty();}catch(Exception ignored){return false;}}
 private boolean directInline(JSONObject s,String url){String type=firstNonEmpty(s,"type","format","mimeType").toLowerCase(Locale.ROOT);String u=url.toLowerCase(Locale.ROOT);return isFctvHlsRelay(url)||type.contains("hls")||type.contains("mpegurl")||type.contains("mp4")||u.matches("(?s).*\\.(m3u8|mp4|m4v|webm)(?:[?#].*)?$");}
 private void startInlineSource(int index){
  if(selectedSources==null||index<0||index>=selectedSources.length())return;selectedSourceIndex=index;final int generation=++inlineLoadGeneration;JSONObject s=selectedSources.optJSONObject(index);if(s==null)return;
  String url=firstNonEmpty(s,"streamUrl","mediaUrl","url","embedUrl");if(url.startsWith("/"))url=API+url;if(url.isEmpty()){if(inlineStatus!=null)inlineStatus.setText("This source has no playback URL.");return;}
  boolean directSource=directInline(s,url);if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);if(inlinePlayer!=null){try{inlinePlayer.stop();inlinePlayer.clearMediaItems();inlinePlayer.release();}catch(RuntimeException ignored){}inlinePlayer=null;}if(inlinePlayerView!=null){inlinePlayerView.setPlayer(null);inlinePlayerView.setVisibility(View.GONE);}ensureInlineWebView();if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.setVisibility(View.GONE);}
  if(inlineFrame==null)return;
  if(directInline(s,url)){
   inlinePlayerView.setVisibility(View.VISIBLE);if(inlinePlayerView.getParent()!=null)((android.view.ViewGroup)inlinePlayerView.getParent()).removeView(inlinePlayerView);inlineFrame.removeAllViews();inlineFrame.addView(inlinePlayerView,new FrameLayout.LayoutParams(-1,-1));
   Map<String,String> headers=new HashMap<>();JSONObject raw=s.optJSONObject("headers");if(raw!=null){Iterator<String> it=raw.keys();while(it.hasNext()){String k=it.next();headers.put(k,raw.optString(k));}}String ref=firstNonEmpty(s,"referer","referrer");if(!ref.isEmpty())headers.put("Referer",ref);
   DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(firstNonEmpty(s,"userAgent").isEmpty()?"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36":firstNonEmpty(s,"userAgent")).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
   inlineRetryCount=0;final ExoPlayer sourcePlayer=new ExoPlayer.Builder(this).build();inlinePlayer=sourcePlayer;inlinePlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);inlinePlayerView.setPlayer(sourcePlayer);sourcePlayer.addListener(new Player.Listener(){public void onPlaybackStateChanged(int state){if(generation!=inlineLoadGeneration||inlinePlayer!=sourcePlayer)return;if(state==Player.STATE_BUFFERING){if(inlineStatus!=null)inlineStatus.setText("Buffering…");inlineNetworkHandler.removeCallbacksAndMessages(null);inlineNetworkHandler.postDelayed(()->{if(generation==inlineLoadGeneration&&inlinePlayer==sourcePlayer&&sourcePlayer.getPlaybackState()==Player.STATE_BUFFERING&&inlineStatus!=null)inlineStatus.setText("Stream is still buffering. Retry or switch source if it does not recover.");},15000L);}else if(state==Player.STATE_READY){inlineNetworkHandler.removeCallbacksAndMessages(null);inlineRetryCount=0;if(inlineStatus!=null)inlineStatus.setText("Stream ready · starting…");}}public void onIsPlayingChanged(boolean isPlaying){if(generation==inlineLoadGeneration&&inlinePlayer==sourcePlayer&&inlineStatus!=null)inlineStatus.setText(isPlaying?"Playing · "+s.optString("source","Stream"):"Waiting for playback…");}public void onPlayerError(PlaybackException e){if(generation!=inlineLoadGeneration||inlinePlayer!=sourcePlayer)return;inlineNetworkHandler.removeCallbacksAndMessages(null);if(inlineRetryCount<2){final int attempt=++inlineRetryCount;if(inlineStatus!=null)inlineStatus.setText("Stream connection lost. Retrying…");inlineNetworkHandler.postDelayed(()->{if(generation==inlineLoadGeneration&&inlinePlayer==sourcePlayer&&inlineFrame!=null){sourcePlayer.seekToDefaultPosition();sourcePlayer.prepare();sourcePlayer.play();}},attempt*1500L);}else if(inlineStatus!=null)inlineStatus.setText("Stream failed. Tap Retry or choose another source.");}});
   boolean hls=isFctvHlsRelay(url)||url.toLowerCase(Locale.ROOT).matches("(?s).*\\.m3u8(?:[?#].*)?$");MediaItem item=new MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(hls?MimeTypes.APPLICATION_M3U8:null).build();
   if(hls)inlinePlayer.setMediaSource(new HlsMediaSource.Factory(data).createMediaSource(item));else inlinePlayer.setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(data).createMediaSource(item));inlinePlayer.prepare();inlinePlayer.play();
  }else{
   inlineFrame.removeAllViews();inlineWebView.setVisibility(View.VISIBLE);inlineFrame.addView(inlineWebView,new FrameLayout.LayoutParams(-1,-1));if(inlineStatus!=null)inlineStatus.setText("Loading "+s.optString("source","stream")+"…");inlineWebView.loadUrl(url);
  }
  if(inlineSources!=null){inlineSources.removeAllViews();for(int i=0;i<Math.min(4,selectedSources.length());i++){JSONObject src=selectedSources.optJSONObject(i);if(src==null)continue;final int ix=i;Button chip=btn(src.optString("source","Source "+(i+1)),i==selectedSourceIndex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.rightMargin=dp(7);inlineSources.addView(chip,cp);chip.setOnClickListener(v->{selectedSourceIndex=ix;startInlineSource(ix);});}}
 }
 private void stopInlinePlayback(){inlineLoadGeneration++;inlineNetworkHandler.removeCallbacksAndMessages(null);if(inlinePlayer!=null){try{inlinePlayer.stop();inlinePlayer.clearMediaItems();inlinePlayer.release();}catch(RuntimeException ignored){}inlinePlayer=null;}if(inlinePlayerView!=null){inlinePlayerView.setPlayer(null);inlinePlayerView.setVisibility(View.GONE);}if(inlineWebTimeout!=null)inlineNetworkHandler.removeCallbacks(inlineWebTimeout);if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.setVisibility(View.GONE);}if(inlineFrame!=null)inlineFrame.removeAllViews();inlineFrame=null;inlineSources=null;inlineStatus=null;}
 private void launchFullscreen(){if(selectedSources==null||selectedSources.length()==0)return;JSONObject source=selectedSources.optJSONObject(selectedSourceIndex);String activeUrl=source==null?"":firstNonEmpty(source,"streamUrl","mediaUrl","url","embedUrl");if(activeUrl.startsWith("/"))activeUrl=API+activeUrl;if(inlinePlayer!=null){PlayerActivity.transferredPlayer=inlinePlayer;inlinePlayer=null;if(inlinePlayerView!=null){PlayerActivity.transferredPlayerView=inlinePlayerView;if(inlinePlayerView.getParent() instanceof ViewGroup)((ViewGroup)inlinePlayerView.getParent()).removeView(inlinePlayerView);inlinePlayerView=null;}}if(inlineWebView!=null){PlayerActivity.transferredWebView=inlineWebView;if(inlineWebView.getParent() instanceof ViewGroup)((ViewGroup)inlineWebView.getParent()).removeView(inlineWebView);inlineWebView=null;}PlayerActivity.returnToMainActivity=true;Intent i=new Intent(this,PlayerActivity.class);i.putExtra("sources",selectedSources.toString());i.putExtra("sourceIndex",selectedSourceIndex);i.putExtra("match",matchTitle(selected));i.putExtra("activeUrl",activeUrl);startActivity(i);overridePendingTransition(0,0);}
 private void renderSourceChoices(JSONArray sources){
  if(sourceList==null)return;sourceList.removeAllViews();if(sources==null||sources.length()==0)return;
  TextView heading=txt("CHOOSE A STREAM SOURCE",10,MUTED,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(8);sourceList.addView(heading,hp);
  for(int i=0;i<sources.length();i++){final int index=i;JSONObject s=sources.optJSONObject(i);if(s==null)continue;boolean active=i==selectedSourceIndex;LinearLayout item=row();item.setBackground(shape(active?0xff302016:PANEL,13));pad(item,13,11,13,11);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(60));ip.bottomMargin=dp(7);sourceList.addView(item,ip);
   TextView radio=txt(active?"●":"○",18,active?ORANGE:MUTED,true);item.addView(radio,new LinearLayout.LayoutParams(dp(22),-2));LinearLayout info=col();LinearLayout.LayoutParams inf=new LinearLayout.LayoutParams(0,-2,1);inf.leftMargin=dp(10);item.addView(info,inf);
   info.addView(txt(s.optString("source","Source "+(i+1)),13,Color.WHITE,true));String meta=s.optString("language","");if(s.optBoolean("hd"))meta+=(meta.isEmpty()?"":"  ·  ")+"HD";if(meta.isEmpty())meta="Stream "+(i+1);TextView sub=txt(meta,10,MUTED,false);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(3);info.addView(sub,subp);
   if(active)item.addView(txt("SELECTED",9,ORANGE,true));item.setOnClickListener(v->{selectedSourceIndex=index;renderSourceChoices(sources);if(matchPreview!=null){String previewUrl=firstNonEmpty(s,"streamUrl","mediaUrl","url","embedUrl");if(previewUrl.startsWith("/"))previewUrl=API+previewUrl;if(!previewUrl.isEmpty())matchPreview.loadUrl(previewUrl);}});
  }
 }
 private void openPendingMatchIfAvailable(){if(pendingMatchId==null||pendingMatchId.isEmpty())return;for(JSONObject match:matches){if(pendingMatchId.equals(match.optString("id"))){pendingMatchId="";selectMatch(match);return;}}JSONObject saved=getScheduledRecords().optJSONObject(pendingMatchId);if(saved!=null){pendingMatchId="";selectMatch(saved);return;}}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);String id=intent.getStringExtra("matchId");if(id!=null&&!id.isEmpty()){pendingMatchId=id;openPendingMatchIfAvailable();}}
 private int popularScore(JSONObject match){if(match==null)return 0;if(match.optBoolean("popular")||match.optBoolean("isPopular")||match.optBoolean("featured")||match.optBoolean("trending"))return 1000;int score=Math.max(match.optInt("popularity",0),match.optInt("popularScore",0));score=Math.max(score,match.optInt("viewers",0));score=Math.max(score,match.optInt("watchers",0));score=Math.max(score,match.optInt("views",0));return Math.max(0,score);}
 private Set<String> getScheduledIds(){Set<String> stored=appPrefs==null?null:appPrefs.getStringSet(REMINDER_IDS,Collections.emptySet());return stored==null?new HashSet<>():new HashSet<>(stored);}
 private boolean isMatchScheduled(JSONObject match){return match!=null&&getScheduledIds().contains(match.optString("id"));}
 private void createReminderChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager manager=getSystemService(NotificationManager.class);if(manager!=null){NotificationChannel channel=new NotificationChannel("zekify_match_reminders_v2","Match reminders",NotificationManager.IMPORTANCE_HIGH);channel.setDescription("Kickoff reminders for matches selected in Zekify");channel.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());channel.enableVibration(true);manager.createNotificationChannel(channel);}}}
 private boolean hasNotificationPermission(){return Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
  private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&!hasNotificationPermission())requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},4102);}
  @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==4102){if(grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Notifications enabled. Tap the match bell again to schedule it.",Toast.LENGTH_LONG).show();else Toast.makeText(this,"Notifications are blocked. Enable them in Android app settings to receive match alerts.",Toast.LENGTH_LONG).show();}}
 private void toggleMatchReminder(JSONObject match){if(match==null||appPrefs==null)return;String id=match.optString("id","");long kickoff=match.optLong("date",0);if(id.isEmpty()||kickoff<=System.currentTimeMillis()){Toast.makeText(this,"Only upcoming matches can be scheduled.",Toast.LENGTH_SHORT).show();return;}Set<String> ids=getScheduledIds();if(ids.contains(id)){ids.remove(id);cancelReminder(id,15);cancelReminder(id,0);JSONObject records=getScheduledRecords();records.remove(id);appPrefs.edit().putString("scheduled_match_records",records.toString()).putStringSet(REMINDER_IDS,new HashSet<>(ids)).apply();Toast.makeText(this,"Match reminder removed.",Toast.LENGTH_SHORT).show();}else{if(!hasNotificationPermission()){requestNotificationPermission();Toast.makeText(this,"Allow notifications, then tap this bell again.",Toast.LENGTH_LONG).show();return;}boolean first=scheduleReminder(match,15,"reminder");boolean second=setting("kickoff_alert",true)&&scheduleReminder(match,0,"kickoff-check");if(!first&&!second){Toast.makeText(this,"Could not schedule this reminder. Check Android alarm and notification settings.",Toast.LENGTH_LONG).show();return;}ids.add(id);JSONObject records=getScheduledRecords();try{records.put(id,match);}catch(JSONException ignored){}appPrefs.edit().putString("scheduled_match_records",records.toString()).putStringSet(REMINDER_IDS,new HashSet<>(ids)).apply();Toast.makeText(this,"Match scheduled. Alerts set for 15 minutes before kickoff and kickoff.",Toast.LENGTH_LONG).show();}if(selected==null)render();}
 private boolean scheduleReminder(JSONObject match,int minutes,String kind){try{long kickoff=match.optLong("date",0);long at=minutes==0?kickoff:kickoff-minutes*60_000L;if(kickoff<=System.currentTimeMillis()||at<=System.currentTimeMillis())return false;Intent intent=new Intent(this,MatchReminderReceiver.class);intent.putExtra("matchId",match.optString("id"));intent.putExtra("matchTitle",matchTitle(match));intent.putExtra("kind",kind);int requestCode=match.optString("id").hashCode()*31+minutes;PendingIntent pending=PendingIntent.getBroadcast(this,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);if(alarm==null)return false;alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);return true;}catch(RuntimeException error){return false;}}
 private void cancelReminder(String id,int minutes){Intent intent=new Intent(this,MatchReminderReceiver.class);int requestCode=id.hashCode()*31+minutes;PendingIntent pending=PendingIntent.getBroadcast(this,requestCode,intent,PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);if(pending!=null){AlarmManager alarm=(AlarmManager)getSystemService(ALARM_SERVICE);if(alarm!=null)alarm.cancel(pending);pending.cancel();}}
 private JSONObject getScheduledRecords(){try{return new JSONObject(appPrefs==null?"{}":appPrefs.getString("scheduled_match_records","{}"));}catch(Exception ignored){return new JSONObject();}}
 private boolean matchHasStarted(JSONObject match){if(match==null)return true;String status=match.optString("status","").trim().toLowerCase(Locale.ROOT);return match.optLong("date",0L)<=System.currentTimeMillis()||status.equals("live")||status.equals("in play")||status.equals("1h")||status.equals("2h")||status.equals("ht")||status.equals("finished")||status.equals("ft")||status.equals("ended")||status.equals("full time")||status.equals("completed");}
 private void removeScheduledMatch(String id){if(id==null||id.isEmpty())return;Set<String> ids=new HashSet<>(getScheduledIds());ids.remove(id);cancelReminder(id,15);cancelReminder(id,0);JSONObject records=getScheduledRecords();records.remove(id);appPrefs.edit().putString("scheduled_match_records",records.toString()).putStringSet(REMINDER_IDS,ids).apply();}
 private void pruneStartedScheduledMatches(){Set<String> ids=new HashSet<>(getScheduledIds());if(ids.isEmpty())return;JSONObject records=getScheduledRecords();boolean changed=false;for(String id:new HashSet<>(ids)){JSONObject match=records.optJSONObject(id);for(JSONObject current:matches){if(id.equals(current.optString("id"))){match=current;try{records.put(id,current);}catch(Exception ignored){}break;}}if(match!=null&&matchHasStarted(match)){ids.remove(id);cancelReminder(id,15);cancelReminder(id,0);records.remove(id);changed=true;}}if(changed)appPrefs.edit().putString("scheduled_match_records",records.toString()).putStringSet(REMINDER_IDS,ids).apply();}
 private void showScheduledMatches(){Dialog dialog=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff101010,22));pad(panel,18,18,18,18);LinearLayout top=row();top.addView(txt("SCHEDULED MATCHES",15,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));NativeIcon close=new NativeIcon(this,"close",Color.WHITE);top.addView(close,new LinearLayout.LayoutParams(dp(38),dp(38)));close.setOnClickListener(v->dialog.dismiss());panel.addView(top);ScrollView scroll=new ScrollView(this);LinearLayout list=col();LinearLayout.LayoutParams listP=new LinearLayout.LayoutParams(-1,-2);listP.topMargin=dp(12);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));scroll.addView(list);pruneStartedScheduledMatches();Set<String> ids=getScheduledIds();JSONObject records=getScheduledRecords();for(JSONObject current:matches){String currentId=current.optString("id");if(ids.contains(currentId))try{records.put(currentId,current);}catch(Exception ignored){}}int count=0;for(String id:new HashSet<>(ids)){JSONObject match=records.optJSONObject(id);if(match==null||matchHasStarted(match)){removeScheduledMatch(id);continue;}LinearLayout item=row();item.setBackground(shape(PANEL,13));pad(item,12,12,10,12);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.bottomMargin=dp(8);list.addView(item,ip);LinearLayout words=col();item.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(txt(matchTitle(match),13,Color.WHITE,true));TextView when=txt(date(match.optLong("date",0)),10,MUTED,false);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(4);words.addView(when,wp);NativeIcon remove=new NativeIcon(this,"close",MUTED);item.addView(remove,new LinearLayout.LayoutParams(dp(32),dp(32)));remove.setContentDescription("Remove reminder");remove.setOnClickListener(v->{removeScheduledMatch(id);list.removeView(item);Toast.makeText(this,"Scheduled match removed.",Toast.LENGTH_SHORT).show();});item.setOnClickListener(v->{dialog.dismiss();selectMatch(match);});count++;}if(count==0){TextView empty=txt("No scheduled matches yet. Tap the bell on an upcoming match to add one.",12,MUTED,false);pad(empty,8,24,8,24);list.addView(empty);}dialog.setContentView(panel);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9f),(int)(getResources().getDisplayMetrics().heightPixels*.72f));}}

 private void showSpeedTest(){
  Dialog d=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff101010,22));pad(panel,18,16,18,16);
  LinearLayout head=row();head.addView(txt("NETWORK SPEED TEST",15,Color.WHITE,true),new LinearLayout.LayoutParams(0,-2,1));NativeIcon close=new NativeIcon(this,"close",Color.WHITE);head.addView(close,new LinearLayout.LayoutParams(dp(34),dp(34)));close.setOnClickListener(v->d.dismiss());panel.addView(head);
  TextView intro=txt("A short test using Cloudflare's public speed-test service. It uses mobile data if Wi-Fi is unavailable.",10,MUTED,false);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(5);panel.addView(intro,ip);
  LinearLayout metrics=row();LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(112));mp.topMargin=dp(18);panel.addView(metrics,mp);
  TextView ping=metricValue("—","PING","ms");TextView jitter=metricValue("—","JITTER","ms");TextView down=metricValue("—","DOWNLOAD","Mbps");TextView up=metricValue("—","UPLOAD","Mbps");
  metrics.addView(metricTile(ping,"PING","ms"),new LinearLayout.LayoutParams(0,-1,1));metrics.addView(metricTile(jitter,"JITTER","ms"),new LinearLayout.LayoutParams(0,-1,1));metrics.addView(metricTile(down,"DOWNLOAD","Mbps"),new LinearLayout.LayoutParams(0,-1,1));metrics.addView(metricTile(up,"UPLOAD","Mbps"),new LinearLayout.LayoutParams(0,-1,1));
  TextView status=txt("Ready to test. Uses real network traffic and continues until the measured speed stabilizes.",10,MUTED,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(16);panel.addView(status,sp);
  Button start=btn("Start speed test",true);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(14);panel.addView(start,bp);
  start.setOnClickListener(v->{start.setEnabled(false);start.setText("Testing…");ping.setText("…");jitter.setText("…");down.setText("…");up.setText("…");status.setText("Measuring latency…");
   apiIo.execute(()->{String error=null;double p=0,j=0,dl=0,ul=0;try{
    ArrayList<Long> samples=new ArrayList<>();for(int n=0;n<4;n++){HttpURLConnection c=(HttpURLConnection)new URL("https://speed.cloudflare.com/__down?bytes=0").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestMethod("GET");long t=System.nanoTime();int code=c.getResponseCode();if(code<200||code>=400)throw new IOException("Ping endpoint returned "+code);try(InputStream in=c.getInputStream()){while(in.read()!=-1){}}finally{c.disconnect();}samples.add((System.nanoTime()-t)/1000000L);}
    long total=0;for(long x:samples)total+=x;p=total/(double)samples.size();double dev=0;for(long x:samples)dev+=Math.abs(x-p);j=dev/samples.size();
    runOnUiThread(()->status.setText("Testing download…"));
    long downloadTotal=0;long downloadNanos=0;ArrayList<Double> downloadRates=new ArrayList<>();int downloadBytes=10*1024*1024;
    do {
     HttpURLConnection dc=(HttpURLConnection)new URL("https://speed.cloudflare.com/__down?bytes="+downloadBytes).openConnection();dc.setConnectTimeout(15000);dc.setReadTimeout(60000);long dt=System.nanoTime();int received=0;
     try(InputStream in=dc.getInputStream()){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){received+=n;long elapsed=System.nanoTime()-dt;double live=received*8.0/Math.max(.001,elapsed/1e9)/1000000.0;final double rate=live;runOnUiThread(()->{if(d.isShowing())down.setText(String.format(Locale.US,"%.1f",rate));});}}
     finally{dc.disconnect();}
     long elapsed=System.nanoTime()-dt;if(received<=0)throw new IOException("Download returned no data");downloadTotal+=received;downloadNanos+=elapsed;downloadRates.add(received*8.0/Math.max(.001,elapsed/1e9)/1000000.0);
     dl=downloadTotal*8.0/Math.max(.001,downloadNanos/1e9)/1000000.0;
     final double current=dl;final int downloadSampleCount=downloadRates.size();runOnUiThread(()->{if(d.isShowing())status.setText("Measuring download… "+downloadSampleCount+" samples · "+String.format(Locale.US,"%.1f Mbps",current));});
    } while(downloadNanos<10_000_000_000L || !speedSamplesStable(downloadRates));
    final double finalDownload=dl;runOnUiThread(()->{if(d.isShowing())down.setText(String.format(Locale.US,"%.1f",finalDownload));status.setText("Download measured. Testing upload…");});
    runOnUiThread(()->status.setText("Testing upload…"));
    byte[] payload=new byte[5*1024*1024];new Random().nextBytes(payload);long uploadTotal=0;long uploadNanos=0;ArrayList<Double> uploadRates=new ArrayList<>();
    do {
     HttpURLConnection uc=(HttpURLConnection)new URL("https://speed.cloudflare.com/__up").openConnection();uc.setConnectTimeout(15000);uc.setReadTimeout(60000);uc.setRequestMethod("POST");uc.setDoOutput(true);uc.setFixedLengthStreamingMode(payload.length);uc.setRequestProperty("Content-Type","application/octet-stream");long ut=System.nanoTime();
     try(OutputStream out=uc.getOutputStream()){int sent=0;while(sent<payload.length){int chunk=Math.min(65536,payload.length-sent);out.write(payload,sent,chunk);sent+=chunk;long elapsed=System.nanoTime()-ut;double live=sent*8.0/Math.max(.001,elapsed/1e9)/1000000.0;final double rate=live;runOnUiThread(()->{if(d.isShowing())up.setText(String.format(Locale.US,"%.1f",rate));});}}
     int response=uc.getResponseCode();try(InputStream in=(response>=400?uc.getErrorStream():uc.getInputStream())){if(in!=null)while(in.read()!=-1){}}finally{uc.disconnect();}
     if(response<200||response>=400)throw new IOException("Upload endpoint returned "+response);
     long elapsed=System.nanoTime()-ut;uploadTotal+=payload.length;uploadNanos+=elapsed;uploadRates.add(payload.length*8.0/Math.max(.001,elapsed/1e9)/1000000.0);
     ul=uploadTotal*8.0/Math.max(.001,uploadNanos/1e9)/1000000.0;
     final double current=ul;final int uploadSampleCount=uploadRates.size();runOnUiThread(()->{if(d.isShowing())status.setText("Measuring upload… "+uploadSampleCount+" samples · "+String.format(Locale.US,"%.1f Mbps",current));});
    } while(uploadNanos<10_000_000_000L || !speedSamplesStable(uploadRates));
   }catch(Exception e){error=e.getMessage()==null?"Speed test failed.":e.getMessage();}
   final String failure=error;final double fp=p,fj=j,fd=dl,fu=ul;runOnUiThread(()->{if(!d.isShowing())return;start.setEnabled(true);start.setText("Run again");if(failure!=null){status.setText("Test incomplete: "+failure);ping.setText("—");jitter.setText("—");down.setText("—");up.setText("—");}else{ping.setText(String.format(Locale.US,"%.0f",fp));jitter.setText(String.format(Locale.US,"%.0f",fj));down.setText(String.format(Locale.US,"%.1f",fd));up.setText(String.format(Locale.US,"%.1f",fu));status.setText("Test complete · Results are estimates and vary with network conditions.");}});
   });
  });
  dialogWindow(d,panel,.94f,.48f);
 }
 private boolean speedSamplesStable(ArrayList<Double> rates){if(rates.size()<3)return false;int n=rates.size();double a=rates.get(n-1),b=rates.get(n-2),d=rates.get(n-3);double mean=(a+b+d)/3.0;if(mean<=0)return false;double spread=Math.max(Math.max(Math.abs(a-mean),Math.abs(b-mean)),Math.abs(d-mean))/mean;return spread<=0.08;}
 private TextView metricValue(String value,String label,String unit){TextView t=txt(value,18,Color.WHITE,true);t.setGravity(Gravity.CENTER);return t;}
 private View metricTile(TextView value,String label,String unit){LinearLayout tile=col();tile.setGravity(Gravity.CENTER);tile.setBackground(shape(PANEL,12));tile.setPadding(dp(2),dp(8),dp(2),dp(8));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-1);p.setMargins(dp(2),0,dp(2),0);tile.setLayoutParams(p);TextView title=txt(label,9,ORANGE,true);title.setGravity(Gravity.CENTER);tile.addView(title);tile.addView(value,new LinearLayout.LayoutParams(-1,0,1));TextView suffix=txt(unit,9,MUTED,false);suffix.setGravity(Gravity.CENTER);tile.addView(suffix);return tile;}
 private void showLiveScores(){startActivity(new Intent(this,LiveScoreActivity.class));}
 private void registerNetworkRecovery(){connectivityManager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(connectivityManager==null)return;networkCallback=new ConnectivityManager.NetworkCallback(){@Override public void onAvailable(Network network){inlineNetworkHandler.postDelayed(()->{if(isFinishing())return;if(setting("auto_refresh",true))load();if(setting("auto_reconnect",true)&&selectedSources!=null&&inlineStatus!=null){String state=inlineStatus.getText().toString().toLowerCase(Locale.ROOT);if(state.contains("failed")||state.contains("timed out")||state.contains("stopped"))startInlineSource(selectedSourceIndex);}},1000L);}};try{connectivityManager.registerDefaultNetworkCallback(networkCallback);}catch(RuntimeException ignored){networkCallback=null;}}
 private void homeAndRender(){stopInlinePlayback();selected=null;selectedSources=null;home();renderLeagues();render();}
 @Override public void onBackPressed(){if(selected!=null){homeAndRender();return;}if(setting("confirm_exit",false)){new AlertDialog.Builder(this).setTitle("Exit Zekify?").setMessage("Close the app now?").setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();}else finish();}

 @Override public void onUserLeaveHint(){
  super.onUserLeaveHint();
  if(!setting("floating_player",false)||selected==null||Build.VERSION.SDK_INT<26)return;
  if(!android.provider.Settings.canDrawOverlays(this)){Toast.makeText(this,"Enable Display over other apps for Zekify in Android settings.",Toast.LENGTH_LONG).show();return;}
  View videoSurface=null;
  if(inlinePlayer!=null&&inlinePlayer.isPlaying()&&inlinePlayerView!=null)videoSurface=inlinePlayerView;
  else if(inlineWebView!=null&&inlineWebView.getVisibility()==View.VISIBLE&&inlineStatus!=null&&!inlineStatus.getText().toString().toLowerCase(Locale.ROOT).contains("failed"))videoSurface=inlineWebView;
  if(videoSurface==null)return;
  final View activeSurface=videoSurface;
  FloatingPlayerService.show(this,activeSurface,()->runOnUiThread(()->{if(inlinePlayer!=null)inlinePlayer.pause();if(inlineWebView!=null)inlineWebView.onPause();}));
 }
 @Override public void onPictureInPictureModeChanged(boolean inPictureInPictureMode,Configuration newConfig){
  super.onPictureInPictureModeChanged(inPictureInPictureMode,newConfig);
  if(!inPictureInPictureMode&&mainPipActive&&!isFinishing()){mainPipActive=false;inlineFrame=null;render();}
 }
 @Override protected void onResume(){super.onResume();FloatingPlayerService.hideForReturn();if(PlayerActivity.returningSourceIndex>=0){selectedSourceIndex=PlayerActivity.returningSourceIndex;PlayerActivity.returningSourceIndex=-1;}if(PlayerActivity.returningPlayer!=null){inlinePlayer=PlayerActivity.returningPlayer;PlayerActivity.returningPlayer=null;}if(PlayerActivity.returningPlayerView!=null){inlinePlayerView=PlayerActivity.returningPlayerView;PlayerActivity.returningPlayerView=null;}if(PlayerActivity.returningWebView!=null){inlineWebView=PlayerActivity.returningWebView;PlayerActivity.returningWebView=null;}if(inlineFrame!=null){inlineFrame.removeAllViews();if(inlinePlayer!=null&&inlinePlayerView!=null){inlinePlayerView.setPlayer(inlinePlayer);inlinePlayerView.setVisibility(View.VISIBLE);inlineFrame.addView(inlinePlayerView,new FrameLayout.LayoutParams(-1,-1));}else if(inlineWebView!=null&&inlineWebView.getVisibility()==View.VISIBLE){if(inlineWebView.getParent() instanceof ViewGroup)((ViewGroup)inlineWebView.getParent()).removeView(inlineWebView);inlineFrame.addView(inlineWebView,new FrameLayout.LayoutParams(-1,-1));}}if(inlinePlayer!=null)inlinePlayer.play();if(inlineWebView!=null)inlineWebView.onResume();}
 @Override protected void onPause(){boolean inPip=Build.VERSION.SDK_INT>=24&&isInPictureInPictureMode();boolean floating=FloatingPlayerService.isShowing();if(!inPip&&!floating&&inlinePlayer!=null)inlinePlayer.pause();if(!inPip&&!floating&&inlineWebView!=null)inlineWebView.onPause();super.onPause();}
 @Override protected void onDestroy(){if(!FloatingPlayerService.isShowing())stopInlinePlayback();inlineNetworkHandler.removeCallbacksAndMessages(null);if(connectivityManager!=null&&networkCallback!=null){try{connectivityManager.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}networkCallback=null;}apiIo.shutdownNow();imageIo.shutdownNow();shareIo.shutdownNow();super.onDestroy();}

 private static class NativeIcon extends View {
  private final String name; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  NativeIcon(Context c,String name,int color){super(c);this.name=name;paint.setColor(color);paint.setStrokeWidth(2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setClickable(true);setFocusable(true);}
  void setIconColor(int color){paint.setColor(color);invalidate();}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float side=Math.min(getWidth(),getHeight());canvas.save();canvas.translate((getWidth()-side)/2f,(getHeight()-side)/2f);canvas.scale(side/24f,side/24f);paint.setStyle(Paint.Style.STROKE);Path p=new Path();
   switch(name){
    case "menu":p.moveTo(4,6);p.lineTo(20,6);p.moveTo(4,12);p.lineTo(20,12);p.moveTo(4,18);p.lineTo(20,18);canvas.drawPath(p,paint);break;
    case "search":canvas.drawCircle(10.5f,10.5f,6.5f,paint);p.moveTo(15.5f,15.5f);p.lineTo(21,21);canvas.drawPath(p,paint);break;
    case "back":p.moveTo(15,18);p.lineTo(9,12);p.lineTo(15,6);p.moveTo(9,12);p.lineTo(21,12);canvas.drawPath(p,paint);break;
    case "next":p.moveTo(3,12);p.lineTo(21,12);p.moveTo(15,6);p.lineTo(21,12);p.lineTo(15,18);canvas.drawPath(p,paint);break;
    case "play":paint.setStyle(Paint.Style.FILL);p.moveTo(7,4);p.lineTo(20,12);p.lineTo(7,20);p.close();canvas.drawPath(p,paint);break;
    case "calendar":canvas.drawRoundRect(3,5,21,21,2,2,paint);p.moveTo(7,3);p.lineTo(7,8);p.moveTo(17,3);p.lineTo(17,8);p.moveTo(3,10);p.lineTo(21,10);canvas.drawPath(p,paint);break;
    case "bell":case "bell-on":p.moveTo(5,17);p.cubicTo(7,15,7,13,7,10);p.cubicTo(7,4,17,4,17,10);p.cubicTo(17,13,17,15,19,17);p.close();canvas.drawPath(p,paint);canvas.drawLine(4,18,20,18,paint);canvas.drawArc(9,18,15,22,0,180,false,paint);if(name.equals("bell-on")){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(18,5,2,paint);}break;
    case "filter":p.moveTo(3,5);p.lineTo(21,5);p.lineTo(14,13);p.lineTo(14,20);p.lineTo(10,18);p.lineTo(10,13);p.close();canvas.drawPath(p,paint);break;
    case "refresh":p.addArc(4,4,20,20,-45,275);canvas.drawPath(p,paint);p.moveTo(18,3);p.lineTo(21,4);p.lineTo(20,8);canvas.drawPath(p,paint);break;
    case "bug":canvas.drawOval(8,7,16,19,paint);p.moveTo(12,3);p.lineTo(12,7);p.moveTo(5,9);p.lineTo(8,11);p.moveTo(19,9);p.lineTo(16,11);p.moveTo(4,14);p.lineTo(8,14);p.moveTo(20,14);p.lineTo(16,14);p.moveTo(6,20);p.lineTo(9,17);p.moveTo(18,20);p.lineTo(15,17);p.moveTo(12,10);p.lineTo(12,17);canvas.drawPath(p,paint);break;
    case "settings":canvas.drawCircle(12,12,8,paint);canvas.drawCircle(12,12,3,paint);for(int i=0;i<8;i++){double a=Math.PI*i/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}break;
    case "copy":canvas.drawRoundRect(7,3,20,18,2,2,paint);canvas.drawRoundRect(4,7,17,21,2,2,paint);break;
    case "close":p.moveTo(6,6);p.lineTo(18,18);p.moveTo(18,6);p.lineTo(6,18);canvas.drawPath(p,paint);break;
    case "share":p.moveTo(7,12);p.lineTo(17,6);p.moveTo(7,12);p.lineTo(17,18);canvas.drawPath(p,paint);canvas.drawCircle(5,12,2.5f,paint);canvas.drawCircle(19,5,2.5f,paint);canvas.drawCircle(19,19,2.5f,paint);break;
    case "mail":canvas.drawRoundRect(2,4,22,20,2,2,paint);p.moveTo(3,6);p.lineTo(12,13);p.lineTo(21,6);canvas.drawPath(p,paint);break;
    case "instagram":canvas.drawRoundRect(2.5f,2.5f,21.5f,21.5f,6,6,paint);canvas.drawCircle(12,12,4.6f,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(17.7f,6.5f,1.35f,paint);break;
    case "tiktok":p.moveTo(13,3);p.lineTo(13,15.4f);p.cubicTo(13,19.5f,7.4f,21.5f,5.1f,18.2f);p.cubicTo(2.7f,14.8f,5.1f,11.2f,9.3f,11.2f);p.lineTo(9.3f,14.1f);p.cubicTo(7.1f,14.1f,6.5f,16.2f,7.8f,17.3f);p.cubicTo(9.1f,18.4f,10.5f,17.4f,10.5f,15.6f);p.lineTo(10.5f,3);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(13,3);p.cubicTo(14,6.5f,16.2f,8,20.5f,8.2f);p.lineTo(20.5f,11.1f);p.cubicTo(16.9f,11,14.8f,9.9f,13,8.3f);canvas.drawPath(p,paint);break;
    case "x":p.moveTo(4,3);p.lineTo(10,3);p.lineTo(20,21);p.lineTo(14,21);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(20,3);p.lineTo(22,3);p.lineTo(9,21);p.lineTo(3,21);p.close();canvas.drawPath(p,paint);break;
    default:canvas.drawCircle(12,12,7,paint);break;
   }
   canvas.restore();
  }
 }
 private static class Ball extends View{Paint p=new Paint(3);Ball(Context c){super(c);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),r=Math.min(w,h)*.45f,x=w/2,y=h/2;p.setColor(0xfff4f4f4);c.drawCircle(x,y,r,p);p.setColor(0xffff8a3d);Path pent=new Path();pent.moveTo(x,y-r*.42f);pent.lineTo(x+r*.34f,y-r*.12f);pent.lineTo(x+r*.21f,y+r*.3f);pent.lineTo(x-r*.21f,y+r*.3f);pent.lineTo(x-r*.34f,y-r*.12f);pent.close();c.drawPath(pent,p);p.setColor(0xff171717);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.055f));c.drawCircle(x,y,r,p);c.drawLine(x,y-r*.42f,x,y-r,p);c.drawLine(x+r*.34f,y-r*.12f,x+r*.8f,y-r*.6f,p);c.drawLine(x+r*.21f,y+r*.3f,x+r*.7f,y+r*.8f,p);c.drawLine(x-r*.21f,y+r*.3f,x-r*.7f,y+r*.8f,p);c.drawLine(x-r*.34f,y-r*.12f,x-r*.8f,y-r*.6f,p);p.setStyle(Paint.Style.FILL);}}
}
