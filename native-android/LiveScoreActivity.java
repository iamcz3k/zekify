package com.v3x.zekify;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class LiveScoreActivity extends Activity {
 private static final String API="https://repo-seed-transplanter.lovable.app";
 private final ExecutorService net=Executors.newSingleThreadExecutor();
 private final ExecutorService images=Executors.newFixedThreadPool(2);
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final java.util.concurrent.ConcurrentHashMap<String,Bitmap> imageCache=new java.util.concurrent.ConcurrentHashMap<>();
 private final HashSet<String> collapsed=new HashSet<>();
 private ArrayList<JSONObject> leagues=new ArrayList<>();
 private Calendar selectedDay=Calendar.getInstance();
 private String filter="all",query="";
 private LinearLayout page,leagueList,filterRow;
 private EditText search;
 private TextView dateLabel,liveCount,status;
 private SharedPreferences prefs;
 private final Runnable refresh=new Runnable(){public void run(){if(isToday())load(false);handler.postDelayed(this,30000L);}};

 @Override public void onCreate(Bundle state){super.onCreate(state);if(!SecurityGuard.verify(this)){SecurityGuard.showBlocked(this);return;}getWindow().setStatusBarColor(Color.rgb(7,7,7));getWindow().setNavigationBarColor(Color.rgb(7,7,7));prefs=getSharedPreferences("zekify_livescore",MODE_PRIVATE);buildUi();load(true);handler.postDelayed(refresh,30000L);}
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private Button chip(String title,boolean active){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(11);b.setTypeface(null,1);b.setTextColor(active?0xffffa66a:0xffb7b7b7);b.setPadding(dp(14),0,dp(14),0);b.setMinHeight(dp(38));b.setMinimumHeight(dp(38));b.setBackground(bg(active?0xff3a281c:0xff191919,20));return b;}
 private void buildUi(){
  page=column();page.setBackgroundColor(0xff050505);page.setPadding(dp(16),dp(12),dp(16),dp(10));
  LinearLayout top=horizontal();TextView back=label("‹",29,Color.WHITE,false);back.setGravity(Gravity.CENTER);back.setBackground(bg(0xff171717,16));top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));back.setOnClickListener(v->finish());
  ImageView liveScoreLogo=new ImageView(this);liveScoreLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);top.addView(liveScoreLogo,new LinearLayout.LayoutParams(dp(38),dp(38)));loadImage("https://www.google.com/s2/favicons?domain=livescore.com&sz=128",liveScoreLogo);LinearLayout brand=column();brand.addView(label("LiveScore",21,Color.WHITE,true));brand.addView(label("MATCH CENTRE  ·  FIXTURES",9,0xffa0a0a0,true));LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(0,-2,1);brandP.leftMargin=dp(10);top.addView(brand,brandP);
  liveCount=label("0 LIVE",10,0xffffa66a,true);liveCount.setPadding(dp(12),dp(8),dp(12),dp(8));liveCount.setBackground(bg(0xff2b1d16,18));top.addView(liveCount);page.addView(top);
  LinearLayout date=horizontal();date.setBackground(bg(0xff121212,18));date.setPadding(dp(6),dp(4),dp(6),dp(4));LinearLayout.LayoutParams dateP=new LinearLayout.LayoutParams(-1,dp(52));dateP.topMargin=dp(16);page.addView(date,dateP);
  Button prev=chip("‹",false),next=chip("›",false),today=chip("Today",true);date.addView(prev,new LinearLayout.LayoutParams(dp(44),-1));dateLabel=label("",12,Color.WHITE,true);dateLabel.setGravity(Gravity.CENTER);date.addView(dateLabel,new LinearLayout.LayoutParams(0,-1,1));date.addView(today,new LinearLayout.LayoutParams(dp(76),-1));date.addView(next,new LinearLayout.LayoutParams(dp(44),-1));
  prev.setOnClickListener(v->{selectedDay.add(Calendar.DAY_OF_MONTH,-1);load(true);});next.setOnClickListener(v->{selectedDay.add(Calendar.DAY_OF_MONTH,1);load(true);});today.setOnClickListener(v->{selectedDay=Calendar.getInstance();load(true);});updateDateLabel();
  LinearLayout searchWrap=horizontal();searchWrap.setBackground(bg(0xff141414,18));searchWrap.setPadding(dp(14),0,dp(14),0);LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(52));searchP.topMargin=dp(12);page.addView(searchWrap,searchP);
  TextView searchIcon=label("⌕",22,0xffaaaaaa,true);searchWrap.addView(searchIcon,new LinearLayout.LayoutParams(dp(25),-1));
  search=new EditText(this);search.setSingleLine(true);search.setTextColor(Color.WHITE);search.setHintTextColor(0xff777777);search.setHint("Search teams or competitions");search.setTextSize(13);search.setPadding(dp(8),0,0,0);search.setBackgroundColor(Color.TRANSPARENT);searchWrap.addView(search,new LinearLayout.LayoutParams(0,-1,1));search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().trim().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(android.text.Editable e){}});
  HorizontalScrollView filters=new HorizontalScrollView(this);filters.setHorizontalScrollBarEnabled(false);filterRow=horizontal();filters.addView(filterRow);LinearLayout.LayoutParams filterP=new LinearLayout.LayoutParams(-1,dp(46));filterP.topMargin=dp(10);page.addView(filters,filterP);renderFilters();
  LinearLayout statusRow=horizontal();LinearLayout.LayoutParams statusP=new LinearLayout.LayoutParams(-1,-2);statusP.topMargin=dp(10);page.addView(statusRow,statusP);View dot=new View(this);GradientDrawable dotBg=new GradientDrawable();dotBg.setShape(GradientDrawable.OVAL);dotBg.setColor(0xffff8a3d);dot.setBackground(dotBg);statusRow.addView(dot,new LinearLayout.LayoutParams(dp(7),dp(7)));status=label("Loading fixtures…",10,0xff999999,false);LinearLayout.LayoutParams statusTextP=new LinearLayout.LayoutParams(0,-2,1);statusTextP.leftMargin=dp(8);statusRow.addView(status,statusTextP);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout.LayoutParams scrollP=new LinearLayout.LayoutParams(-1,0,1);scrollP.topMargin=dp(8);page.addView(scroll,scrollP);leagueList=column();scroll.addView(leagueList);
  setContentView(page);
 }
 private void updateDateLabel(){SimpleDateFormat f=new SimpleDateFormat("EEEE, d MMMM yyyy",Locale.getDefault());dateLabel.setText(f.format(selectedDay.getTime()));}
 private String apiDate(){SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(selectedDay.getTime());}
 private boolean isToday(){SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return apiDate().equals(f.format(new Date()));}
 private void renderFilters(){filterRow.removeAllViews();String[] keys={"all","live","upcoming","finished","favourites"};String[] names={"All","Live","Upcoming","Finished","★ Favourites"};for(int i=0;i<keys.length;i++){final String key=keys[i];Button b=chip(names[i],filter.equals(key));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-1);p.rightMargin=dp(6);filterRow.addView(b,p);b.setOnClickListener(v->{filter=key;renderFilters();render();});}}
 private JSONObject get(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","Zekify-Android");try{int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}}finally{c.disconnect();}}
 private void load(boolean showLoading){updateDateLabel();if(showLoading)status.setText("Loading fixtures…");net.execute(()->{ArrayList<JSONObject> fresh=new ArrayList<>();String error=null;try{JSONObject response=get(API+"/api/public/fotmob?mode=matches&date="+apiDate());if(!response.optBoolean("ok"))throw new IOException(response.optString("error","FotMob feed unavailable"));JSONObject data=response.optJSONObject("data");JSONArray arr=data==null?null:data.optJSONArray("leagues");if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject lg=arr.optJSONObject(i);if(lg!=null)fresh.add(lg);}}catch(Exception e){error=e.getMessage()==null?"Unable to load scores":e.getMessage();}final String failure=error;runOnUiThread(()->{if(isFinishing())return;if(failure!=null){status.setText("Scores unavailable · "+failure);if(leagues.isEmpty())leagueList.removeAllViews();return;}leagues=fresh;status.setText(fresh.isEmpty()?"No fixtures for this date.":"Updated just now · refreshes every 30 seconds");render();});});}
 private boolean isLive(JSONObject m){JSONObject s=m.optJSONObject("status");return s!=null&&s.optBoolean("started")&&!s.optBoolean("finished")&&!s.optBoolean("cancelled");}
 private boolean isFinished(JSONObject m){JSONObject s=m.optJSONObject("status");return s!=null&&s.optBoolean("finished");}
 private boolean isUpcoming(JSONObject m){JSONObject s=m.optJSONObject("status");return s!=null&&!s.optBoolean("started")&&!s.optBoolean("cancelled");}
 private Set<String> favourites(){return new HashSet<>(prefs.getStringSet("favourite_leagues",Collections.emptySet()));}
 private void toggleFavourite(String id){Set<String> f=favourites();if(f.contains(id))f.remove(id);else f.add(id);prefs.edit().putStringSet("favourite_leagues",f).apply();render();}
 private void render(){
  if(leagueList==null)return;leagueList.removeAllViews();int live=0;for(JSONObject lg:leagues){JSONArray ms=lg.optJSONArray("matches");if(ms!=null)for(int i=0;i<ms.length();i++){JSONObject m=ms.optJSONObject(i);if(m!=null&&isLive(m))live++;}}liveCount.setText(live+" LIVE");
  Set<String> fav=favourites();int shown=0;
  for(JSONObject lg:leagues){JSONArray all=lg.optJSONArray("matches");if(all==null)continue;ArrayList<JSONObject> matches=new ArrayList<>();String leagueName=lg.optString("name","Competition");String id=lg.optString("id",lg.optString("primaryId",""));
   if(filter.equals("favourites")&&!fav.contains(id)&&!fav.contains(lg.optString("primaryId","")))continue;
   for(int i=0;i<all.length();i++){JSONObject m=all.optJSONObject(i);if(m==null)continue;if(filter.equals("live")&&!isLive(m))continue;if(filter.equals("upcoming")&&!isUpcoming(m))continue;if(filter.equals("finished")&&!isFinished(m))continue;if(!query.isEmpty()){String hay=team(m,"home")+" "+team(m,"away")+" "+leagueName;if(!hay.toLowerCase(Locale.ROOT).contains(query))continue;}matches.add(m);}
   if(matches.isEmpty())continue;shown+=matches.size();
   LinearLayout section=column();section.setBackground(bg(0xff121212,22));section.setClipToOutline(true);LinearLayout.LayoutParams secP=new LinearLayout.LayoutParams(-1,-2);secP.bottomMargin=dp(12);leagueList.addView(section,secP);
   LinearLayout heading=horizontal();heading.setPadding(dp(15),dp(13),dp(10),dp(13));section.addView(heading);
   String logo=leagueLogo(lg);if(!logo.isEmpty()){ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);heading.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));loadImage(logo,icon);}
   TextView name=label(leagueName+(lg.optString("ccode").isEmpty()?"":" · "+lg.optString("ccode")),11,Color.WHITE,true);LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(0,-2,1);nameP.leftMargin=dp(9);heading.addView(name,nameP);
   TextView count=label(String.valueOf(matches.size()),10,0xffaaaaaa,true);count.setPadding(dp(6),0,dp(8),0);heading.addView(count);
   TextView star=label(fav.contains(id)?"★":"☆",20,fav.contains(id)?0xffff8a3d:0xff777777,true);heading.addView(star,new LinearLayout.LayoutParams(dp(34),dp(32)));star.setGravity(Gravity.CENTER);star.setOnClickListener(v->toggleFavourite(id));
   TextView fold=label(collapsed.contains(id)?"+":"−",18,0xff999999,true);fold.setGravity(Gravity.CENTER);heading.addView(fold,new LinearLayout.LayoutParams(dp(24),dp(32)));heading.setOnClickListener(v->{if(collapsed.contains(id))collapsed.remove(id);else collapsed.add(id);render();});
   if(!collapsed.contains(id))for(JSONObject m:matches)section.addView(matchRow(m));
  }
  if(shown==0){TextView empty=label(filter.equals("favourites")?"No favourite leagues yet. Tap ☆ beside a league to save it.":"No matches found for this filter.",12,0xff888888,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(10),dp(42),dp(10),dp(42));leagueList.addView(empty);}
 }
 private String team(JSONObject m,String side){JSONObject t=m.optJSONObject(side);return t==null?"Team":t.optString("name","Team");}
 private String teamLogo(JSONObject m,String side){JSONObject t=m.optJSONObject(side);int id=t==null?0:t.optInt("id",0);return id>0?"https://images.fotmob.com/image_resources/logo/teamlogo/"+id+"_small.png":"";}
 private String leagueLogo(JSONObject lg){int id=lg.optInt("primaryId",lg.optInt("id",0));return id>0?"https://images.fotmob.com/image_resources/logo/leaguelogo/"+id+".png":"";}
 private String kickoff(JSONObject m){long ts=0;JSONObject s=m.optJSONObject("status");if(s!=null){String utc=s.optString("utcTime","");if(!utc.isEmpty())try{ts=javax.xml.datatype.DatatypeFactory.newInstance().newXMLGregorianCalendar(utc).toGregorianCalendar().getTimeInMillis();}catch(Exception ignored){}}if(ts==0)ts=m.optLong("timeTS",0);if(ts<=0)return m.optString("time","");return new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(ts));}
 private String matchStatus(JSONObject m){JSONObject s=m.optJSONObject("status");if(s==null)return "UPCOMING";if(s.optBoolean("cancelled")){JSONObject r=s.optJSONObject("reason");return r==null?"POSTPONED":r.optString("short","POSTPONED");}if(isLive(m)){JSONObject l=s.optJSONObject("liveTime");return "LIVE · "+(l==null?"":l.optString("short",""));}if(isFinished(m))return "FULL TIME";return kickoff(m);}
 private View matchRow(JSONObject m){
  LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(bg(0xff1a1a1a,18));row.setClipToOutline(true);
  LinearLayout teams=new LinearLayout(this);teams.setGravity(Gravity.CENTER_VERTICAL);row.addView(teams,new LinearLayout.LayoutParams(-1,dp(48)));
  ImageView homeLogo=new ImageView(this);homeLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);teams.addView(homeLogo,new LinearLayout.LayoutParams(dp(25),dp(25)));loadImage(teamLogo(m,"home"),homeLogo);
  TextView home=label(team(m,"home"),12,Color.WHITE,true);home.setMaxLines(2);home.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(7);teams.addView(home,hp);
  LinearLayout score=column();score.setGravity(Gravity.CENTER);teams.addView(score,new LinearLayout.LayoutParams(dp(82),-2));boolean live=isLive(m),done=isFinished(m);JSONObject h=m.optJSONObject("home"),a=m.optJSONObject("away");String hs=h==null?"":h.optString("score",""),as=a==null?"":a.optString("score","");
  TextView scoreText=label(live||done?(hs.isEmpty()?"0":hs)+" - "+(as.isEmpty()?"0":as):"VS",14,live?0xffff8a3d:Color.WHITE,true);scoreText.setGravity(Gravity.CENTER);score.addView(scoreText);TextView state=label(matchStatus(m),9,live?0xffff8a3d:0xff999999,true);state.setGravity(Gravity.CENTER);score.addView(state);
  TextView away=label(team(m,"away"),12,Color.WHITE,true);away.setMaxLines(2);away.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(7);teams.addView(away,ap);
  ImageView awayLogo=new ImageView(this);awayLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);teams.addView(awayLogo,new LinearLayout.LayoutParams(dp(25),dp(25)));loadImage(teamLogo(m,"away"),awayLogo);
  row.setOnClickListener(v->showMatch(m));return row;
 }
 private void loadImage(String url,ImageView target){if(url.isEmpty())return;target.setTag(url);Bitmap cached=imageCache.get(url);if(cached!=null){target.setImageBitmap(cached);return;}images.execute(()->{HttpURLConnection c=null;Bitmap b=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);try(InputStream in=c.getInputStream()){b=BitmapFactory.decodeStream(in);}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}if(b!=null){imageCache.put(url,b);Bitmap result=b;runOnUiThread(()->{if(url.equals(target.getTag()))target.setImageBitmap(result);});}});}
 private void showMatch(JSONObject match){
  Dialog d=new Dialog(this);LinearLayout box=column();box.setBackground(bg(0xff090909,28));box.setPadding(dp(18),dp(14),dp(18),dp(18));
  LinearLayout head=horizontal();TextView back=label("‹",30,Color.WHITE,false);back.setGravity(Gravity.CENTER);back.setBackground(bg(0xff1b1b1b,16));head.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));back.setOnClickListener(v->d.dismiss());
  LinearLayout headText=column();LinearLayout.LayoutParams htp=new LinearLayout.LayoutParams(0,-2,1);htp.leftMargin=dp(10);head.addView(headText,htp);headText.addView(label("MATCH CENTRE",10,0xffffa66a,true));headText.addView(label(match.optString("leagueName","LiveScore"),12,Color.WHITE,true));box.addView(head);
  LinearLayout hero=column();hero.setBackground(bg(0xff191919,24));hero.setPadding(dp(15),dp(15),dp(15),dp(15));LinearLayout.LayoutParams heroP=new LinearLayout.LayoutParams(-1,-2);heroP.topMargin=dp(14);box.addView(hero,heroP);
  TextView state=label(matchStatus(match),10,isLive(match)?0xffff8178:0xffffa66a,true);state.setGravity(Gravity.CENTER);hero.addView(state);
  LinearLayout score=horizontal();score.setPadding(0,dp(16),0,dp(16));TextView home=label(team(match,"home"),12,Color.WHITE,true);home.setGravity(Gravity.CENTER);score.addView(home,new LinearLayout.LayoutParams(0,-2,1));JSONObject h=match.optJSONObject("home"),a=match.optJSONObject("away");String hs=h==null?"":h.optString("score",""),as=a==null?"":a.optString("score","");TextView goals=label(isLive(match)||isFinished(match)?(hs.isEmpty()?"0":hs)+"  -  "+(as.isEmpty()?"0":as):"VS",24,Color.WHITE,true);goals.setGravity(Gravity.CENTER);score.addView(goals);TextView away=label(team(match,"away"),12,Color.WHITE,true);away.setGravity(Gravity.CENTER);score.addView(away,new LinearLayout.LayoutParams(0,-2,1));hero.addView(score);
  TextView info=label(kickoff(match)+"  ·  "+(match.optString("status","").isEmpty()?"Fixture":match.optString("status")),10,0xffaaaaaa,false);info.setGravity(Gravity.CENTER);hero.addView(info);
  HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout tabRow=horizontal();tabs.addView(tabRow);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(44));tp.topMargin=dp(14);box.addView(tabs,tp);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(false);LinearLayout body=column();sc.addView(body);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1);bp.topMargin=dp(10);box.addView(sc,bp);
  final JSONObject[] details={null};final String[] active={"Overview"};
  String[] tabNames={"Overview","Details"};
  Runnable draw=()->{body.removeAllViews();if(details[0]==null){TextView wait=label("Loading match centre…",11,0xffaaaaaa,false);wait.setGravity(Gravity.CENTER);wait.setPadding(0,dp(24),0,dp(24));body.addView(wait);return;}JSONObject content=details[0].optJSONObject("content");if(content==null)content=new JSONObject();if(active[0].equals("Overview")){JSONObject general=details[0].optJSONObject("general");addDetail(body,"Competition",general==null?match.optString("leagueName","Football"):general.optString("parentLeagueName",general.optString("leagueName","Football")));addDetail(body,"Kick-off",general==null?kickoff(match):general.optString("matchTimeUTC",kickoff(match)));addDetail(body,"Status",matchStatus(match));JSONObject homeScore=match.optJSONObject("home"),awayScore=match.optJSONObject("away");if(homeScore!=null||awayScore!=null)addDetail(body,"Score",(homeScore==null?"0":homeScore.optString("score","0"))+" - "+(awayScore==null?"0":awayScore.optString("score","0")));}else{addDetail(body,"Match details","Available events, statistics, lineups, league table and head-to-head information");flatten(content,body,0);}
  };;
  for(String t:tabNames){Button b=chip(t,t.equals(active[0]));LinearLayout.LayoutParams tpp=new LinearLayout.LayoutParams(-2,-1);tpp.rightMargin=dp(6);tabRow.addView(b,tpp);b.setOnClickListener(v->{active[0]=t;for(int i=0;i<tabRow.getChildCount();i++)((Button)tabRow.getChildAt(i)).setBackground(bg(tabNames[i].equals(t)?0xff3a281c:0xff191919,20));draw.run();});}
  draw.run();d.setContentView(box);d.setCancelable(true);d.show();if(d.getWindow()!=null){d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.96f),(int)(getResources().getDisplayMetrics().heightPixels*.92f));}
  net.execute(()->{JSONObject result=null;try{result=get(API+"/api/public/fotmob?mode=match&id="+URLEncoder.encode(match.optString("id"),"UTF-8"));if(!result.optBoolean("ok"))result=null;else result=result.optJSONObject("data");}catch(Exception ignored){}final JSONObject finalResult=result;runOnUiThread(()->{if(!d.isShowing())return;details[0]=finalResult;if(finalResult==null){body.removeAllViews();addDetail(body,"Unavailable","Match details could not be loaded.");}else draw.run();});});
 }
 private void addDetail(LinearLayout target,String title,String value){LinearLayout item=column();item.setPadding(dp(10),dp(9),dp(10),dp(9));item.setBackground(bg(0xff191919,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);target.addView(item,p);item.addView(label(title,9,0xffff8a3d,true));TextView val=label(value==null||value.isEmpty()?"—":value,11,Color.WHITE,false);val.setPadding(0,dp(3),0,0);item.addView(val);}
 private void flatten(JSONObject obj,LinearLayout target,int depth){if(obj==null||depth>3)return;Iterator<String> it=obj.keys();while(it.hasNext()){String key=it.next();Object value=obj.opt(key);if(value==null||value==JSONObject.NULL)continue;String title=key.replaceAll("([a-z])([A-Z])","$1 $2");if(value instanceof JSONObject){if(depth<2)flatten((JSONObject)value,target,depth+1);}else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;if(depth<2)for(int i=0;i<Math.min(a.length(),12);i++){Object x=a.opt(i);if(x instanceof JSONObject)flatten((JSONObject)x,target,depth+1);else if(x!=null)addDetail(target,title,String.valueOf(x));}}else if(!(value instanceof Boolean)||((Boolean)value)){String v=String.valueOf(value);if(v.length()<140&&!key.toLowerCase(Locale.ROOT).contains("url")&&!key.equals("id"))addDetail(target,title,v);}}}
 @Override public void onBackPressed(){finish();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);net.shutdownNow();images.shutdownNow();super.onDestroy();}
}
