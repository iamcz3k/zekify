package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
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
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private static final String API="https://laczekstream.qzz.io";
 private final ExecutorService io=Executors.newFixedThreadPool(3);
 private final ArrayList<JSONObject> matches=new ArrayList<>();
 private LinearLayout root,content,leagueRow;
 private EditText search;
 private String tab="live",league="All",query="";
 private JSONObject selected,detail; private LinearLayout sourceList; private WebView matchPreview; private int selectedSourceIndex=0; private JSONArray selectedSources; private FrameLayout inlineFrame; private PlayerView inlinePlayerView; private ExoPlayer inlinePlayer; private WebView inlineWebView; private TextView inlineStatus; private LinearLayout inlineSources; private boolean inlineLoading=false; private Dialog startupDialog;
 private final int ORANGE=Color.rgb(255,138,61),BG=Color.rgb(5,5,5),PANEL=Color.rgb(17,17,17),MUTED=Color.rgb(145,145,145);
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR^View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);home();showStartupSplash();load();}
 private void showStartupSplash(){
  startupDialog=new Dialog(this);startupDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);FrameLayout splash=new FrameLayout(this);splash.setBackgroundColor(Color.BLACK);
  LinearLayout center=col();center.setGravity(Gravity.CENTER);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);splash.addView(center,cp);
  center.addView(new Ball(this),new LinearLayout.LayoutParams(dp(78),dp(78)));TextView brand=txt("ZEKIFY",20,Color.WHITE,true);brand.setLetterSpacing(.18f);brand.setGravity(Gravity.CENTER);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(18);center.addView(brand,bp);
  ProgressBar progress=new ProgressBar(this);progress.setIndeterminate(true);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ORANGE));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(42),dp(42));pp.topMargin=dp(24);center.addView(progress,pp);
  startupDialog.setContentView(splash);startupDialog.setCancelable(false);startupDialog.show();Window w=startupDialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setStatusBarColor(Color.BLACK);w.setNavigationBarColor(Color.BLACK);}
 }
 private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}}
 private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(r);d.setStroke(1,0x22ffffff);return d;}
 private TextView txt(String s,int z,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(true);t.setTextIsSelectable(false);return t;}
 private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
 private LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(1);return x;}
 private void pad(View v,int l,int t,int r,int b){v.setPadding(dp(l),dp(t),dp(r),dp(b));}
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private Button btn(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextSize(12);b.setAllCaps(false);b.setTextColor(primary?Color.BLACK:Color.WHITE);b.setTypeface(null,Typeface.BOLD);b.setBackground(shape(primary?ORANGE:PANEL,12));b.setMinHeight(dp(42));b.setPadding(dp(14),0,dp(14),0);return b;}
 private void home(){
  root=col();root.setBackgroundColor(BG);
  LinearLayout header=row();pad(header,18,12,18,12);
  LinearLayout brand=row();brand.addView(new Ball(this),new LinearLayout.LayoutParams(dp(34),dp(34)));
  TextView name=txt("ZEKIFY",19,Color.WHITE,true);name.setLetterSpacing(.14f);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.leftMargin=dp(9);brand.addView(name,np);
  header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  TextView category=txt("FOOTBALL",10,ORANGE,true);category.setLetterSpacing(.16f);header.addView(category);
  NativeIcon menu=new NativeIcon(this,"menu",Color.WHITE);menu.setContentDescription("Menu");menu.setOnClickListener(v->menu());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(44),dp(44));mp.leftMargin=dp(10);header.addView(menu,mp);root.addView(header);
  View line=new View(this);line.setBackgroundColor(0x22ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  LinearLayout heading=col();pad(heading,18,22,18,14);heading.addView(txt("THE ACTION",10,ORANGE,true));TextView title=txt("Matches",29,Color.WHITE,true);heading.addView(title);root.addView(heading);
  LinearLayout tabs=row();pad(tabs,18,0,18,14);
  Button live=btn("Live",tab.equals("live")),up=btn("Upcoming",tab.equals("upcoming"));
  live.setOnClickListener(v->{tab="live";home();render();});up.setOnClickListener(v->{tab="upcoming";home();render();});
  tabs.addView(live,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams upP=new LinearLayout.LayoutParams(0,dp(44),1);upP.leftMargin=dp(8);tabs.addView(up,upP);root.addView(tabs);
  LinearLayout searchBox=row();searchBox.setBackground(shape(PANEL,12));pad(searchBox,14,0,14,0);searchBox.addView(new NativeIcon(this,"search",MUTED),new LinearLayout.LayoutParams(dp(19),dp(19)));search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);search.setTextColor(Color.WHITE);search.setHintTextColor(MUTED);search.setHint("Search teams, matches or leagues");search.setBackgroundColor(Color.TRANSPARENT);search.setTextIsSelectable(false);search.setLongClickable(false);pad(search,10,0,0,0);search.setText(query);searchBox.addView(search,new LinearLayout.LayoutParams(0,-1,1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.setMargins(dp(18),0,dp(18),dp(10));root.addView(searchBox,sp);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);leagueRow=row();pad(leagueRow,18,0,18,12);hs.addView(leagueRow);root.addView(hs);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);content=col();pad(content,18,2,18,24);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);renderLeagues();
 }
 private void menu(){
  Dialog dialog=new Dialog(this);LinearLayout panel=col();panel.setBackground(shape(0xff101010,24));pad(panel,20,18,20,20);
  LinearLayout top=row();LinearLayout brand=row();brand.addView(new Ball(this),new LinearLayout.LayoutParams(dp(32),dp(32)));TextView brandName=txt("ZEKIFY",18,Color.WHITE,true);brandName.setLetterSpacing(.12f);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);bp.leftMargin=dp(9);brand.addView(brandName,bp);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  NativeIcon close=new NativeIcon(this,"close",Color.WHITE);top.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));close.setOnClickListener(v->dialog.dismiss());panel.addView(top);
  LinearLayout owner=col();owner.setBackground(shape(0xff21170f,18));pad(owner,17,18,17,18);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.topMargin=dp(24);panel.addView(owner,op);
  owner.addView(txt("THE CREATOR",10,ORANGE,true));TextView developed=txt("Developed by",13,0xffc8c8c8,false);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2);dlp.topMargin=dp(13);owner.addView(developed,dlp);owner.addView(txt("TITUS LACZEK",23,Color.WHITE,true));TextView tagline=txt("Made for the game. Built with passion.",11,0xffaaaaaa,false);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(8);owner.addView(tagline,tlp);
  TextView socials=txt("CONNECT",10,ORANGE,true);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(27);slp.bottomMargin=dp(10);panel.addView(socials,slp);
  panel.addView(socialRow("x","X / Twitter","@iam_czek","https://x.com/iam_czek",dialog));
  panel.addView(socialRow("tiktok","TikTok","@iamczek","https://www.tiktok.com/@iamczek",dialog));
  panel.addView(socialRow("instagram","Instagram","@iamczek","https://www.instagram.com/iamczek",dialog));
  panel.addView(socialRow("mail","Email","laczekrsa@proton.me","mailto:laczekrsa@proton.me",dialog));
  LinearLayout share=row();share.setBackground(shape(0xff21170f,14));pad(share,14,14,14,14);LinearLayout.LayoutParams shareP=new LinearLayout.LayoutParams(-1,dp(54));shareP.topMargin=dp(12);panel.addView(share,shareP);NativeIcon shareIcon=new NativeIcon(this,"share",ORANGE);share.addView(shareIcon,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView shareText=txt("Share Zekify",13,Color.WHITE,true);LinearLayout.LayoutParams shareTextP=new LinearLayout.LayoutParams(0,-2,1);shareTextP.leftMargin=dp(12);share.addView(shareText,shareTextP);share.setOnClickListener(v->shareInstalledApk());
  View spacer=new View(this);panel.addView(spacer,new LinearLayout.LayoutParams(1,0,1));View divider=new View(this);divider.setBackgroundColor(0x22ffffff);LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,dp(1));vlp.topMargin=dp(18);vlp.bottomMargin=dp(14);panel.addView(divider,vlp);TextView foot=txt("ZEKIFY  ·  FOOTBALL EVERYWHERE",9,MUTED,true);foot.setGravity(Gravity.CENTER);panel.addView(foot);
  dialog.setContentView(panel);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.width=(int)(getResources().getDisplayMetrics().widthPixels*.88f);a.height=-1;a.gravity=Gravity.RIGHT;a.dimAmount=.72f;w.setAttributes(a);}
 }
 
 private void shareInstalledApk(){
  io.execute(()->{
   try{
    File dir=new File(getCacheDir(),"shared");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create APK share folder");
    File apk=new File(dir,"Zekify.apk");File installed=new File(getApplicationInfo().sourceDir);
    try(InputStream in=new FileInputStream(installed);OutputStream out=new FileOutputStream(apk)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);}
    Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
    Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/vnd.android.package-archive");send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newUri(getContentResolver(),"Zekify APK",uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    runOnUiThread(()->startActivity(Intent.createChooser(send,"Share Zekify APK")));
   }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Could not prepare the APK for sharing.",Toast.LENGTH_LONG).show());}
  });
 }
 private View socialRow(String icon,String label,String value,String url,Dialog dialog){
  LinearLayout item=row();item.setBackground(shape(0xff191919,14));pad(item,13,11,13,11);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(62));p.bottomMargin=dp(8);item.setLayoutParams(p);
  NativeIcon mark=new NativeIcon(this,icon,Color.WHITE);item.addView(mark,new LinearLayout.LayoutParams(dp(25),dp(25)));LinearLayout words=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(13);item.addView(words,wp);words.addView(txt(label,13,Color.WHITE,true));TextView val=txt(value,10,MUTED,false);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(3);words.addView(val,vp);NativeIcon arrow=new NativeIcon(this,"next",ORANGE);item.addView(arrow,new LinearLayout.LayoutParams(dp(19),dp(19)));
  item.setOnClickListener(v->{try{Intent i=url.startsWith("mailto:")?new Intent(Intent.ACTION_SENDTO,Uri.parse(url)):new Intent(Intent.ACTION_VIEW,Uri.parse(url));startActivity(i);}catch(Exception ignored){} });return item;
 }
 private JSONObject request(String path)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);c.disconnect();if(code>=400)throw new Exception("Service error "+code);return new JSONObject(out.toString("UTF-8"));}
 private void load(){io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=matches&sport=football");JSONArray a=p.optJSONArray("data");if(!p.optBoolean("success")||a==null)throw new Exception("Unexpected match response");matches.clear();for(int i=0;i<a.length();i++)matches.add(a.getJSONObject(i));runOnUiThread(()->{renderLeagues();render();hideStartupSplash();});}catch(Exception e){runOnUiThread(()->{hideStartupSplash();if(content!=null){content.removeAllViews();TextView err=txt("Unable to load matches. Tap to retry.",14,Color.WHITE,false);pad(err,12,25,12,25);err.setOnClickListener(v->load());content.addView(err);}});}});}
 private String firstNonEmpty(JSONObject source,String... keys){if(source==null)return "";for(String key:keys){String value=source.optString(key,"").trim();if(!value.isEmpty()&&!"null".equalsIgnoreCase(value))return value;}return "";}
 private String team(JSONObject m,String side){JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);return t==null?"Team":t.optString("name","Team");}
 private String leagueName(JSONObject m){String s=m.optString("league","");return s.trim().isEmpty()?"Other competitions":s.trim();}
 private void renderLeagues(){if(leagueRow==null)return;leagueRow.removeAllViews();LinkedHashSet<String> ls=new LinkedHashSet<>();ls.add("All");for(JSONObject m:matches)ls.add(leagueName(m));for(String s:ls){Button b=btn(s,s.equals(league));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.rightMargin=dp(7);leagueRow.addView(b,p);b.setOnClickListener(v->{league=s;renderLeagues();render();});}}
  private void render(){
   if(selected!=null){renderMatchScreen();return;}
   if(content==null)return;content.removeAllViews();int count=0;
   for(JSONObject m:matches){String status=m.optString("status","upcoming");if(!status.equals(tab)||(!league.equals("All")&&!league.equals(leagueName(m))))continue;String hay=(m.optString("title")+" "+leagueName(m)+" "+team(m,"home")+" "+team(m,"away")).toLowerCase(Locale.ROOT);if(!hay.contains(query))continue;content.addView(matchCard(m));count++;}
   if(count==0){TextView empty=txt("No "+tab+" matches match your filters.",14,MUTED,false);pad(empty,12,36,12,36);content.addView(empty);}
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
   LinearLayout recommended=col();pad(recommended,14,2,14,18);int shown=0;
   for(JSONObject match:matches){if(match.optString("id").equals(selected.optString("id")))continue;if(shown>=4)break;recommended.addView(matchCard(match));shown++;}
   if(shown==0){TextView empty=txt("No other matches available yet.",12,MUTED,false);pad(empty,8,18,8,18);recommended.addView(empty);}
   recommendedScroll.addView(recommended);screen.addView(recommendedScroll,new LinearLayout.LayoutParams(-1,0,1));
   root=screen;setContentView(screen);
  }
 private View matchCard(JSONObject m){LinearLayout card=col();card.setBackground(shape(PANEL,16));pad(card,15,13,15,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(11);card.setLayoutParams(cp);
  LinearLayout meta=row();meta.addView(txt(leagueName(m).toUpperCase(Locale.ROOT),10,ORANGE,true),new LinearLayout.LayoutParams(0,-2,1));boolean live=m.optString("status").equals("live");TextView timer=txt("",10,live?0xffff7168:ORANGE,true);meta.addView(timer);bindMatchTimer(timer,m.optLong("date",0),live);card.addView(meta);
  LinearLayout teams=row();pad(teams,0,22,0,20);teams.addView(teamBadge(m,"home"));TextView home=txt(team(m,"home"),14,Color.WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(9);teams.addView(home,hp);TextView vs=txt("VS",10,ORANGE,true);teams.addView(vs);TextView away=txt(team(m,"away"),14,Color.WHITE,true);away.setGravity(Gravity.RIGHT);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(9);teams.addView(away,ap);teams.addView(teamBadge(m,"away"));card.addView(teams);
  View sep=new View(this);sep.setBackgroundColor(0x22ffffff);card.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));LinearLayout foot=row();pad(foot,0,10,0,0);foot.addView(txt("Match details",11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));foot.addView(new NativeIcon(this,"next",ORANGE),new LinearLayout.LayoutParams(dp(18),dp(18)));card.addView(foot);card.setOnClickListener(v->selectMatch(m));return card;}
 private View teamBadge(JSONObject m,String side){FrameLayout f=new FrameLayout(this);f.setBackground(shape(0xff252525,50));f.setElevation(dp(1));TextView fallback=txt(team(m,side).substring(0,1).toUpperCase(Locale.ROOT),14,ORANGE,true);fallback.setGravity(Gravity.CENTER);f.addView(fallback,new FrameLayout.LayoutParams(dp(34),dp(34)));JSONObject t=m.optJSONObject("teams");JSONObject tm=t==null?null:t.optJSONObject(side);String url=tm==null?"":tm.optString("badge","");if(!url.isEmpty()){if(url.startsWith("/"))url=API+url;String finalUrl=url;io.execute(()->{try{Bitmap bm=BitmapFactory.decodeStream(new URL(finalUrl).openStream());runOnUiThread(()->{if(bm!=null){f.removeAllViews();ImageView im=new ImageView(this);im.setImageBitmap(bm);im.setScaleType(ImageView.ScaleType.FIT_CENTER);f.addView(im,new FrameLayout.LayoutParams(dp(34),dp(34)));}});}catch(Exception ignored){}});}return f;}
 private String date(long v){if(v<=0)return "Kickoff TBC";return new java.text.SimpleDateFormat("EEE, d MMM · HH:mm",Locale.getDefault()).format(new Date(v));}
 private String timerText(long kickoff,boolean live){long now=System.currentTimeMillis();long diff=live?Math.max(0,now-kickoff):Math.max(0,kickoff-now);long sec=diff/1000;long days=sec/86400;long hours=(sec%86400)/3600;long mins=(sec%3600)/60;long seconds=sec%60;String value=String.format(Locale.getDefault(),"%02d:%02d:%02d",hours,mins,seconds);return live?"LIVE · "+(days>0?days+"d ":"")+value:"KICKOFF IN · "+(days>0?days+"d ":"")+value;}
 private void bindMatchTimer(TextView view,long kickoff,boolean live){Runnable tick=new Runnable(){public void run(){if(isFinishing()||view.getWindowToken()==null)return;view.setText(timerText(kickoff,live));view.postDelayed(this,1000);}};view.setTag(tick);view.post(tick);}
 private void selectMatch(JSONObject m){
  if(selected!=null&&selected.optString("id").equals(m.optString("id")))return;
  stopInlinePlayback();selected=m;detail=null;selectedSources=null;selectedSourceIndex=0;inlineLoading=true;render();
  io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=detail&sport=football&id="+URLEncoder.encode(m.optString("id"),"UTF-8"));JSONObject d=p.optJSONObject("data");JSONArray src=d==null?null:d.optJSONArray("sources");runOnUiThread(()->{if(selected==null||!selected.optString("id").equals(m.optString("id")))return;detail=d;selectedSources=src;inlineLoading=false;render();if(src!=null&&src.length()>0)startInlineSource(0);});}catch(Exception e){runOnUiThread(()->{inlineLoading=false;if(inlineStatus!=null)inlineStatus.setText("Unable to load streams. Tap retry.");render();});}});
 }
 private View buildInlinePlayer(){
  LinearLayout card=col();card.setBackground(shape(0xff111111,18));pad(card,12,12,12,12);
  LinearLayout top=row();top.addView(txt("NOW PLAYING",10,ORANGE,true),new LinearLayout.LayoutParams(0,-2,1));
  NativeIcon close=new NativeIcon(this,"close",MUTED);top.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));close.setOnClickListener(v->homeAndRender());card.addView(top);
  TextView title=txt(team(selected,"home")+" vs "+team(selected,"away"),15,Color.WHITE,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(4);card.addView(title,tp);
  inlineFrame=new FrameLayout(this);inlineFrame.setBackgroundColor(Color.BLACK);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(205));fp.topMargin=dp(10);card.addView(inlineFrame,fp);
  if(inlinePlayerView==null){inlinePlayerView=new PlayerView(this);inlinePlayerView.setUseController(false);inlinePlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);}
  if(inlineWebView==null){inlineWebView=new WebView(this);inlineWebView.setBackgroundColor(Color.BLACK);inlineWebView.getSettings().setJavaScriptEnabled(true);inlineWebView.getSettings().setDomStorageEnabled(true);inlineWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);inlineWebView.setLongClickable(false);inlineWebView.setOnLongClickListener(v->true);}
  if(inlinePlayerView.getParent()!=null)((android.view.ViewGroup)inlinePlayerView.getParent()).removeView(inlinePlayerView);
  if(inlineWebView.getParent()!=null)((android.view.ViewGroup)inlineWebView.getParent()).removeView(inlineWebView);
  if(inlinePlayer!=null){inlinePlayerView.setPlayer(inlinePlayer);inlineFrame.addView(inlinePlayerView,new FrameLayout.LayoutParams(-1,-1));}
  else if(inlineWebView.getVisibility()==View.VISIBLE){inlineFrame.addView(inlineWebView,new FrameLayout.LayoutParams(-1,-1));}
  inlineStatus=txt(inlineLoading?"Loading available streams…":(selectedSources==null||selectedSources.length()==0?"No streams available":"Connecting to stream…"),11,MUTED,false);inlineStatus.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(8);card.addView(inlineStatus,st);
  HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.setHorizontalScrollBarEnabled(false);inlineSources=row();pad(inlineSources,0,8,0,0);horizontal.addView(inlineSources);card.addView(horizontal);
  if(selectedSources!=null){for(int i=0;i<Math.min(4,selectedSources.length());i++){JSONObject s=selectedSources.optJSONObject(i);if(s==null)continue;final int ix=i;Button chip=btn(s.optString("source","Source "+(i+1)),i==selectedSourceIndex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.rightMargin=dp(7);inlineSources.addView(chip,cp);chip.setOnClickListener(v->{selectedSourceIndex=ix;startInlineSource(ix);});}}
  LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(8);card.addView(actions,ap);
  Button full=btn("Fullscreen",true);actions.addView(full,new LinearLayout.LayoutParams(0,dp(42),1));full.setOnClickListener(v->launchFullscreen());
  Button retry=btn("Retry",false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(42),1);rp.leftMargin=dp(8);actions.addView(retry,rp);retry.setOnClickListener(v->startInlineSource(selectedSourceIndex));
  return card;
 }
 private boolean directInline(JSONObject s,String url){String type=firstNonEmpty(s,"type","format","mimeType").toLowerCase(Locale.ROOT);String u=url.toLowerCase(Locale.ROOT);return type.contains("hls")||type.contains("mpegurl")||type.contains("mp4")||u.matches("(?s).*\\.(m3u8|mp4|m4v|webm)(?:[?#].*)?$");}
 private void startInlineSource(int index){
  if(selectedSources==null||index<0||index>=selectedSources.length())return;selectedSourceIndex=index;JSONObject s=selectedSources.optJSONObject(index);if(s==null)return;
  String url=firstNonEmpty(s,"streamUrl","mediaUrl","url","embedUrl");if(url.startsWith("/"))url=API+url;if(url.isEmpty()){if(inlineStatus!=null)inlineStatus.setText("This source has no playback URL.");return;}
  if(inlinePlayer!=null){inlinePlayer.release();inlinePlayer=null;}if(inlinePlayerView!=null)inlinePlayerView.setPlayer(null);
  if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.loadUrl("about:blank");inlineWebView.setVisibility(View.GONE);}
  if(inlineFrame==null)return;
  if(directInline(s,url)){
   inlinePlayerView.setVisibility(View.VISIBLE);if(inlinePlayerView.getParent()!=null)((android.view.ViewGroup)inlinePlayerView.getParent()).removeView(inlinePlayerView);inlineFrame.removeAllViews();inlineFrame.addView(inlinePlayerView,new FrameLayout.LayoutParams(-1,-1));
   Map<String,String> headers=new HashMap<>();JSONObject raw=s.optJSONObject("headers");if(raw!=null){Iterator<String> it=raw.keys();while(it.hasNext()){String k=it.next();headers.put(k,raw.optString(k));}}String ref=firstNonEmpty(s,"referer","referrer");if(!ref.isEmpty())headers.put("Referer",ref);
   DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(firstNonEmpty(s,"userAgent").isEmpty()?"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36":firstNonEmpty(s,"userAgent")).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
   inlinePlayer=new ExoPlayer.Builder(this).build();inlinePlayerView.setPlayer(inlinePlayer);inlinePlayer.addListener(new Player.Listener(){public void onPlaybackStateChanged(int state){if(inlineStatus!=null)inlineStatus.setText(state==Player.STATE_BUFFERING?"Buffering…":state==Player.STATE_READY?"Playing · "+s.optString("source","Stream"):"");}public void onPlayerError(PlaybackException e){if(inlineStatus!=null)inlineStatus.setText("Stream failed. Choose another source.");}});
   MediaItem item=new MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(url.toLowerCase(Locale.ROOT).contains(".m3u8")?MimeTypes.APPLICATION_M3U8:null).build();
   if(url.toLowerCase(Locale.ROOT).contains(".m3u8"))inlinePlayer.setMediaSource(new HlsMediaSource.Factory(data).createMediaSource(item));else inlinePlayer.setMediaSource(new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(data).createMediaSource(item));inlinePlayer.prepare();inlinePlayer.play();
  }else{
   inlineFrame.removeAllViews();inlineWebView.setVisibility(View.VISIBLE);inlineFrame.addView(inlineWebView,new FrameLayout.LayoutParams(-1,-1));if(inlineStatus!=null)inlineStatus.setText("Loading "+s.optString("source","stream")+"…");inlineWebView.loadUrl(url);
  }
  if(inlineSources!=null){inlineSources.removeAllViews();for(int i=0;i<Math.min(4,selectedSources.length());i++){JSONObject src=selectedSources.optJSONObject(i);if(src==null)continue;final int ix=i;Button chip=btn(src.optString("source","Source "+(i+1)),i==selectedSourceIndex);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(38));cp.rightMargin=dp(7);inlineSources.addView(chip,cp);chip.setOnClickListener(v->{selectedSourceIndex=ix;startInlineSource(ix);});}}
 }
 private void stopInlinePlayback(){if(inlinePlayer!=null){inlinePlayer.release();inlinePlayer=null;}if(inlinePlayerView!=null)inlinePlayerView.setPlayer(null);if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.loadUrl("about:blank");}inlineFrame=null;inlineSources=null;inlineStatus=null;}
 private void launchFullscreen(){if(selectedSources==null||selectedSources.length()==0)return;if(inlinePlayer!=null)inlinePlayer.pause();if(inlineWebView!=null)inlineWebView.onPause();Intent i=new Intent(this,PlayerActivity.class);i.putExtra("sources",selectedSources.toString());i.putExtra("sourceIndex",selectedSourceIndex);i.putExtra("match",team(selected,"home")+" vs "+team(selected,"away"));startActivity(i);}
 private void renderSourceChoices(JSONArray sources){
  if(sourceList==null)return;sourceList.removeAllViews();if(sources==null||sources.length()==0)return;
  TextView heading=txt("CHOOSE A STREAM SOURCE",10,MUTED,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(8);sourceList.addView(heading,hp);
  for(int i=0;i<sources.length();i++){final int index=i;JSONObject s=sources.optJSONObject(i);if(s==null)continue;boolean active=i==selectedSourceIndex;LinearLayout item=row();item.setBackground(shape(active?0xff302016:PANEL,13));pad(item,13,11,13,11);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(60));ip.bottomMargin=dp(7);sourceList.addView(item,ip);
   TextView radio=txt(active?"●":"○",18,active?ORANGE:MUTED,true);item.addView(radio,new LinearLayout.LayoutParams(dp(22),-2));LinearLayout info=col();LinearLayout.LayoutParams inf=new LinearLayout.LayoutParams(0,-2,1);inf.leftMargin=dp(10);item.addView(info,inf);
   info.addView(txt(s.optString("source","Source "+(i+1)),13,Color.WHITE,true));String meta=s.optString("language","");if(s.optBoolean("hd"))meta+=(meta.isEmpty()?"":"  ·  ")+"HD";if(meta.isEmpty())meta="Stream "+(i+1);TextView sub=txt(meta,10,MUTED,false);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(3);info.addView(sub,subp);
   if(active)item.addView(txt("SELECTED",9,ORANGE,true));item.setOnClickListener(v->{selectedSourceIndex=index;renderSourceChoices(sources);if(matchPreview!=null){String previewUrl=firstNonEmpty(s,"streamUrl","mediaUrl","url","embedUrl");if(previewUrl.startsWith("/"))previewUrl=API+previewUrl;if(!previewUrl.isEmpty())matchPreview.loadUrl(previewUrl);}});
  }
 }
 private void homeAndRender(){stopInlinePlayback();selected=null;selectedSources=null;home();renderLeagues();render();}
 @Override public void onBackPressed(){homeAndRender();}
 @Override protected void onResume(){super.onResume();if(inlinePlayer!=null)inlinePlayer.play();if(inlineWebView!=null)inlineWebView.onResume();}
 @Override protected void onPause(){if(inlinePlayer!=null)inlinePlayer.pause();if(inlineWebView!=null)inlineWebView.onPause();super.onPause();}
 @Override protected void onDestroy(){stopInlinePlayback();io.shutdownNow();super.onDestroy();}

 private static class NativeIcon extends View {
  private final String name; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  NativeIcon(Context c,String name,int color){super(c);this.name=name;paint.setColor(color);paint.setStrokeWidth(2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setClickable(true);setFocusable(true);}
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float side=Math.min(getWidth(),getHeight());canvas.save();canvas.translate((getWidth()-side)/2f,(getHeight()-side)/2f);canvas.scale(side/24f,side/24f);paint.setStyle(Paint.Style.STROKE);Path p=new Path();
   switch(name){
    case "menu":p.moveTo(4,6);p.lineTo(20,6);p.moveTo(4,12);p.lineTo(20,12);p.moveTo(4,18);p.lineTo(20,18);canvas.drawPath(p,paint);break;
    case "search":canvas.drawCircle(10.5f,10.5f,6.5f,paint);p.moveTo(15.5f,15.5f);p.lineTo(21,21);canvas.drawPath(p,paint);break;
    case "back":p.moveTo(15,18);p.lineTo(9,12);p.lineTo(15,6);p.moveTo(9,12);p.lineTo(21,12);canvas.drawPath(p,paint);break;
    case "next":p.moveTo(3,12);p.lineTo(21,12);p.moveTo(15,6);p.lineTo(21,12);p.lineTo(15,18);canvas.drawPath(p,paint);break;
    case "play":paint.setStyle(Paint.Style.FILL);p.moveTo(7,4);p.lineTo(20,12);p.lineTo(7,20);p.close();canvas.drawPath(p,paint);break;
    case "calendar":canvas.drawRoundRect(3,5,21,21,2,2,paint);p.moveTo(7,3);p.lineTo(7,8);p.moveTo(17,3);p.lineTo(17,8);p.moveTo(3,10);p.lineTo(21,10);canvas.drawPath(p,paint);break;
    case "filter":p.moveTo(3,5);p.lineTo(21,5);p.lineTo(14,13);p.lineTo(14,20);p.lineTo(10,18);p.lineTo(10,13);p.close();canvas.drawPath(p,paint);break;
    case "close":p.moveTo(6,6);p.lineTo(18,18);p.moveTo(18,6);p.lineTo(6,18);canvas.drawPath(p,paint);break;
    case "share":p.moveTo(7,12);p.lineTo(17,6);p.moveTo(7,12);p.lineTo(17,18);canvas.drawPath(p,paint);canvas.drawCircle(5,12,2.5f,paint);canvas.drawCircle(19,5,2.5f,paint);canvas.drawCircle(19,19,2.5f,paint);break;
    case "mail":canvas.drawRoundRect(2,4,22,20,2,2,paint);p.moveTo(3,6);p.lineTo(12,13);p.lineTo(21,6);canvas.drawPath(p,paint);break;
    case "instagram":canvas.drawRoundRect(3,3,21,21,5,5,paint);canvas.drawCircle(12,12,4.2f,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(17.5f,6.5f,1.2f,paint);break;
    case "tiktok":p.moveTo(13,4);p.lineTo(13,16);canvas.drawPath(p,paint);p.moveTo(13,5);p.cubicTo(14,8,16,9,20,9);canvas.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(9,17,3.3f,paint);break;
    case "x":p.moveTo(4,4);p.lineTo(20,20);p.moveTo(19,4);p.lineTo(5,20);canvas.drawPath(p,paint);break;
    default:canvas.drawCircle(12,12,7,paint);break;
   }
   canvas.restore();
  }
 }
 private static class Ball extends View{Paint p=new Paint(3);Ball(Context c){super(c);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),r=Math.min(w,h)*.45f,x=w/2,y=h/2;p.setColor(0xfff4f4f4);c.drawCircle(x,y,r,p);p.setColor(0xffff8a3d);Path pent=new Path();pent.moveTo(x,y-r*.42f);pent.lineTo(x+r*.34f,y-r*.12f);pent.lineTo(x+r*.21f,y+r*.3f);pent.lineTo(x-r*.21f,y+r*.3f);pent.lineTo(x-r*.34f,y-r*.12f);pent.close();c.drawPath(pent,p);p.setColor(0xff171717);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.055f));c.drawCircle(x,y,r,p);c.drawLine(x,y-r*.42f,x,y-r,p);c.drawLine(x+r*.34f,y-r*.12f,x+r*.8f,y-r*.6f,p);c.drawLine(x+r*.21f,y+r*.3f,x+r*.7f,y+r*.8f,p);c.drawLine(x-r*.21f,y+r*.3f,x-r*.7f,y+r*.8f,p);c.drawLine(x-r*.34f,y-r*.12f,x-r*.8f,y-r*.6f,p);p.setStyle(Paint.Style.FILL);}}
}
