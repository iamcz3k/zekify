package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
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
 private JSONObject selected,detail; private LinearLayout sourceList; private int selectedSourceIndex=0;
 private final int ORANGE=Color.rgb(255,138,61),BG=Color.rgb(5,5,5),PANEL=Color.rgb(17,17,17),MUTED=Color.rgb(145,145,145);
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR^View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);home();load();}
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
  View spacer=new View(this);panel.addView(spacer,new LinearLayout.LayoutParams(1,0,1));View divider=new View(this);divider.setBackgroundColor(0x22ffffff);LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,dp(1));vlp.topMargin=dp(18);vlp.bottomMargin=dp(14);panel.addView(divider,vlp);TextView foot=txt("ZEKIFY  ·  FOOTBALL EVERYWHERE",9,MUTED,true);foot.setGravity(Gravity.CENTER);panel.addView(foot);
  dialog.setContentView(panel);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams a=w.getAttributes();a.width=(int)(getResources().getDisplayMetrics().widthPixels*.88f);a.height=-1;a.gravity=Gravity.RIGHT;a.dimAmount=.72f;w.setAttributes(a);}
 }
 private View socialRow(String icon,String label,String value,String url,Dialog dialog){
  LinearLayout item=row();item.setBackground(shape(0xff191919,14));pad(item,13,11,13,11);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(62));p.bottomMargin=dp(8);item.setLayoutParams(p);
  NativeIcon mark=new NativeIcon(this,icon,Color.WHITE);item.addView(mark,new LinearLayout.LayoutParams(dp(25),dp(25)));LinearLayout words=col();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.leftMargin=dp(13);item.addView(words,wp);words.addView(txt(label,13,Color.WHITE,true));TextView val=txt(value,10,MUTED,false);LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);vp.topMargin=dp(3);words.addView(val,vp);NativeIcon arrow=new NativeIcon(this,"next",ORANGE);item.addView(arrow,new LinearLayout.LayoutParams(dp(19),dp(19)));
  item.setOnClickListener(v->{try{Intent i=url.startsWith("mailto:")?new Intent(Intent.ACTION_SENDTO,Uri.parse(url)):new Intent(Intent.ACTION_VIEW,Uri.parse(url));startActivity(i);}catch(Exception ignored){} });return item;
 }
 private JSONObject request(String path)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);c.disconnect();if(code>=400)throw new Exception("Service error "+code);return new JSONObject(out.toString("UTF-8"));}
 private void load(){io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=matches&sport=football");JSONArray a=p.optJSONArray("data");if(!p.optBoolean("success")||a==null)throw new Exception("Unexpected match response");matches.clear();for(int i=0;i<a.length();i++)matches.add(a.getJSONObject(i));runOnUiThread(()->{renderLeagues();render();});}catch(Exception e){runOnUiThread(()->{if(content!=null){content.removeAllViews();TextView err=txt("Unable to load matches. Tap to retry.",14,Color.WHITE,false);pad(err,12,25,12,25);err.setOnClickListener(v->load());content.addView(err);}});}});}
 private String team(JSONObject m,String side){JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);return t==null?"Team":t.optString("name","Team");}
 private String leagueName(JSONObject m){String s=m.optString("league","");return s.trim().isEmpty()?"Other competitions":s.trim();}
 private void renderLeagues(){if(leagueRow==null)return;leagueRow.removeAllViews();LinkedHashSet<String> ls=new LinkedHashSet<>();ls.add("All");for(JSONObject m:matches)ls.add(leagueName(m));for(String s:ls){Button b=btn(s,s.equals(league));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.rightMargin=dp(7);leagueRow.addView(b,p);b.setOnClickListener(v->{league=s;renderLeagues();render();});}}
 private void render(){if(content==null)return;content.removeAllViews();int count=0;for(JSONObject m:matches){String status=m.optString("status","upcoming");if(!status.equals(tab)||(!league.equals("All")&&!league.equals(leagueName(m))))continue;String hay=(m.optString("title")+" "+leagueName(m)+" "+team(m,"home")+" "+team(m,"away")).toLowerCase(Locale.ROOT);if(!hay.contains(query))continue;content.addView(matchCard(m));count++;}if(count==0){TextView empty=txt("No "+tab+" matches match your filters.",14,MUTED,false);pad(empty,12,36,12,36);content.addView(empty);}}
 private View matchCard(JSONObject m){LinearLayout card=col();card.setBackground(shape(PANEL,16));pad(card,15,13,15,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(11);card.setLayoutParams(cp);
  LinearLayout meta=row();meta.addView(txt(leagueName(m).toUpperCase(Locale.ROOT),10,ORANGE,true),new LinearLayout.LayoutParams(0,-2,1));boolean live=m.optString("status").equals("live");TextView timer=txt("",10,live?0xffff7168:ORANGE,true);meta.addView(timer);bindMatchTimer(timer,m.optLong("date",0),live);card.addView(meta);
  LinearLayout teams=row();pad(teams,0,22,0,20);teams.addView(teamBadge(m,"home"));TextView home=txt(team(m,"home"),14,Color.WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(9);teams.addView(home,hp);TextView vs=txt("VS",10,ORANGE,true);teams.addView(vs);TextView away=txt(team(m,"away"),14,Color.WHITE,true);away.setGravity(Gravity.RIGHT);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(9);teams.addView(away,ap);teams.addView(teamBadge(m,"away"));card.addView(teams);
  View sep=new View(this);sep.setBackgroundColor(0x22ffffff);card.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));LinearLayout foot=row();pad(foot,0,10,0,0);foot.addView(txt("Match details",11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));foot.addView(new NativeIcon(this,"next",ORANGE),new LinearLayout.LayoutParams(dp(18),dp(18)));card.addView(foot);card.setOnClickListener(v->open(m));return card;}
 private View teamBadge(JSONObject m,String side){FrameLayout f=new FrameLayout(this);f.setBackground(shape(0xff252525,50));f.setElevation(dp(1));TextView fallback=txt(team(m,side).substring(0,1).toUpperCase(Locale.ROOT),14,ORANGE,true);fallback.setGravity(Gravity.CENTER);f.addView(fallback,new FrameLayout.LayoutParams(dp(34),dp(34)));JSONObject t=m.optJSONObject("teams");JSONObject tm=t==null?null:t.optJSONObject(side);String url=tm==null?"":tm.optString("badge","");if(!url.isEmpty()){if(url.startsWith("/"))url=API+url;String finalUrl=url;io.execute(()->{try{Bitmap bm=BitmapFactory.decodeStream(new URL(finalUrl).openStream());runOnUiThread(()->{if(bm!=null){f.removeAllViews();ImageView im=new ImageView(this);im.setImageBitmap(bm);im.setScaleType(ImageView.ScaleType.FIT_CENTER);f.addView(im,new FrameLayout.LayoutParams(dp(34),dp(34)));}});}catch(Exception ignored){}});}return f;}
 private String date(long v){if(v<=0)return "Kickoff TBC";return new java.text.SimpleDateFormat("EEE, d MMM · HH:mm",Locale.getDefault()).format(new Date(v));}
 private String timerText(long kickoff,boolean live){long now=System.currentTimeMillis();long diff=live?Math.max(0,now-kickoff):Math.max(0,kickoff-now);long sec=diff/1000;long days=sec/86400;long hours=(sec%86400)/3600;long mins=(sec%3600)/60;long seconds=sec%60;String value=String.format(Locale.getDefault(),"%02d:%02d:%02d",hours,mins,seconds);return live?"LIVE · "+(days>0?days+"d ":"")+value:"KICKOFF IN · "+(days>0?days+"d ":"")+value;}
 private void bindMatchTimer(TextView view,long kickoff,boolean live){Runnable tick=new Runnable(){public void run(){if(isFinishing()||view.getWindowToken()==null)return;view.setText(timerText(kickoff,live));view.postDelayed(this,1000);}};view.setTag(tick);view.post(tick);}
 private void open(JSONObject m){selected=m;detail=null;selectedSourceIndex=0;LinearLayout page=col();page.setBackgroundColor(BG);pad(page,18,18,18,18);LinearLayout back=row();back.addView(new NativeIcon(this,"back",Color.WHITE),new LinearLayout.LayoutParams(dp(20),dp(20)));TextView backLabel=txt("Back to matches",12,Color.WHITE,true);LinearLayout.LayoutParams backText=new LinearLayout.LayoutParams(-2,-2);backText.leftMargin=dp(8);back.addView(backLabel,backText);back.setPadding(dp(8),dp(10),dp(12),dp(10));back.setOnClickListener(v->homeAndRender());page.addView(back);
  TextView league=txt(leagueName(m).toUpperCase(Locale.ROOT),10,ORANGE,true);league.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(30);page.addView(league,lp);
  LinearLayout matchup=row();matchup.setGravity(Gravity.CENTER);pad(matchup,0,35,0,25);matchup.addView(teamBadge(m,"home"));TextView h=txt(team(m,"home"),15,Color.WHITE,true);h.setGravity(Gravity.CENTER);LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,-2,1);x.leftMargin=dp(8);matchup.addView(h,x);matchup.addView(txt("VS",11,ORANGE,true));TextView a=txt(team(m,"away"),15,Color.WHITE,true);a.setGravity(Gravity.CENTER);LinearLayout.LayoutParams y=new LinearLayout.LayoutParams(0,-2,1);y.rightMargin=dp(8);matchup.addView(a,y);matchup.addView(teamBadge(m,"away"));page.addView(matchup);
  TextView time=txt(date(m.optLong("date",0)),12,MUTED,false);time.setGravity(Gravity.CENTER);page.addView(time);
  boolean matchLive=m.optString("status").equals("live");TextView state=txt(matchLive?"LIVE NOW":"UPCOMING FIXTURE",10,ORANGE,true);state.setGravity(Gravity.CENTER);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(12);st.bottomMargin=dp(6);page.addView(state,st);TextView matchClock=txt("",13,matchLive?0xffff7168:ORANGE,true);matchClock.setGravity(Gravity.CENTER);page.addView(matchClock);bindMatchTimer(matchClock,m.optLong("date",0),matchLive);
  LinearLayout preview=col();preview.setGravity(Gravity.CENTER);preview.setBackground(shape(0xff17120f,18));pad(preview,15,35,15,35);preview.addView(txt("ZEKIFY MATCH STREAM",10,ORANGE,true));NativeIcon icon=new NativeIcon(this,"play",Color.WHITE);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(34),dp(34));ip.gravity=Gravity.CENTER;ip.topMargin=dp(20);ip.bottomMargin=dp(20);preview.addView(icon,ip);preview.addView(txt("STREAM SOURCES",10,MUTED,true));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(14);page.addView(preview,pp);
  TextView details=txt("MATCH DETAILS",11,ORANGE,true);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(24);page.addView(details,dp1);page.addView(txt(m.optString("title",team(m,"home")+" vs "+team(m,"away")),15,Color.WHITE,true));
  TextView sourceCount=txt("Loading stream sources…",12,MUTED,false);LinearLayout.LayoutParams sc=new LinearLayout.LayoutParams(-1,-2);sc.topMargin=dp(12);page.addView(sourceCount,sc);sourceList=col();LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.topMargin=dp(10);page.addView(sourceList,sl);
  Button play=btn("Play",true);play.setEnabled(false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(14);preview.addView(play,bp);
  ScrollView detailScroll=new ScrollView(this);detailScroll.setFillViewport(true);detailScroll.setBackgroundColor(BG);detailScroll.addView(page);setContentView(detailScroll);io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=detail&sport=football&id="+URLEncoder.encode(m.optString("id"),"UTF-8"));JSONObject d=p.optJSONObject("data");JSONArray sources=d==null?null:d.optJSONArray("sources");runOnUiThread(()->{detail=d;int n=sources==null?0:sources.length();sourceCount.setText(n+" available source"+(n==1?"":"s"));play.setEnabled(n>0);renderSourceChoices(sources);if(n>0){JSONObject first=sources.optJSONObject(selectedSourceIndex);String previewUrl=first==null?"":firstNonEmpty(first,"streamUrl","mediaUrl","url","embedUrl");if(previewUrl.startsWith("/"))previewUrl=API+previewUrl;if(!previewUrl.isEmpty()){WebView previewWeb=new WebView(this);previewWeb.setBackgroundColor(Color.BLACK);previewWeb.setVerticalScrollBarEnabled(false);previewWeb.setHorizontalScrollBarEnabled(false);previewWeb.getSettings().setJavaScriptEnabled(true);previewWeb.getSettings().setDomStorageEnabled(true);previewWeb.getSettings().setMediaPlaybackRequiresUserGesture(true);previewWeb.setOnTouchListener((v,e)->true);preview.addView(previewWeb,1,new LinearLayout.LayoutParams(-1,dp(190)));previewWeb.loadUrl(previewUrl);}}play.setOnClickListener(v->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("sources",sources.toString());i.putExtra("sourceIndex",selectedSourceIndex);i.putExtra("match",team(m,"home")+" vs "+team(m,"away"));startActivity(i);});});}catch(Exception e){runOnUiThread(()->sourceCount.setText("Unable to load sources. Go back and retry."));}});
 }
 private void renderSourceChoices(JSONArray sources){
  if(sourceList==null)return;sourceList.removeAllViews();if(sources==null||sources.length()==0)return;
  TextView heading=txt("CHOOSE A STREAM SOURCE",10,MUTED,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(8);sourceList.addView(heading,hp);
  for(int i=0;i<sources.length();i++){final int index=i;JSONObject s=sources.optJSONObject(i);if(s==null)continue;boolean active=i==selectedSourceIndex;LinearLayout item=row();item.setBackground(shape(active?0xff302016:PANEL,13));pad(item,13,11,13,11);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(60));ip.bottomMargin=dp(7);sourceList.addView(item,ip);
   TextView radio=txt(active?"●":"○",18,active?ORANGE:MUTED,true);item.addView(radio,new LinearLayout.LayoutParams(dp(22),-2));LinearLayout info=col();LinearLayout.LayoutParams inf=new LinearLayout.LayoutParams(0,-2,1);inf.leftMargin=dp(10);item.addView(info,inf);
   info.addView(txt(s.optString("source","Source "+(i+1)),13,Color.WHITE,true));String meta=s.optString("language","");if(s.optBoolean("hd"))meta+=(meta.isEmpty()?"":"  ·  ")+"HD";if(meta.isEmpty())meta="Stream "+(i+1);TextView sub=txt(meta,10,MUTED,false);LinearLayout.LayoutParams subp=new LinearLayout.LayoutParams(-1,-2);subp.topMargin=dp(3);info.addView(sub,subp);
   if(active)item.addView(txt("SELECTED",9,ORANGE,true));item.setOnClickListener(v->{selectedSourceIndex=index;renderSourceChoices(sources);});
  }
 }
 private void homeAndRender(){home();renderLeagues();render();}
 @Override public void onBackPressed(){homeAndRender();}

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
