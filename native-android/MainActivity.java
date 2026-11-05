package com.v3x.zekify;

import android.app.*;
import android.content.*;
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
 private JSONObject selected,detail;
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
  Button menu=btn("☰",false);menu.setOnClickListener(v->menu());LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(48),dp(44));mp.leftMargin=dp(10);header.addView(menu,mp);root.addView(header);
  View line=new View(this);line.setBackgroundColor(0x22ffffff);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  LinearLayout heading=col();pad(heading,18,22,18,14);heading.addView(txt("THE ACTION",10,ORANGE,true));TextView title=txt("Matches",29,Color.WHITE,true);heading.addView(title);root.addView(heading);
  LinearLayout tabs=row();pad(tabs,18,0,18,14);
  Button live=btn("●  Live",tab.equals("live")),up=btn("Upcoming",tab.equals("upcoming"));
  live.setOnClickListener(v->{tab="live";home();render();});up.setOnClickListener(v->{tab="upcoming";home();render();});
  tabs.addView(live,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams upP=new LinearLayout.LayoutParams(0,dp(44),1);upP.leftMargin=dp(8);tabs.addView(up,upP);root.addView(tabs);
  search=new EditText(this);search.setSingleLine(true);search.setTextSize(13);search.setTextColor(Color.WHITE);search.setHintTextColor(MUTED);search.setHint("⌕   Search teams, matches or leagues");search.setBackground(shape(PANEL,12));pad(search,14,0,14,0);search.setText(query);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.setMargins(dp(18),0,dp(18),dp(10));root.addView(search,sp);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);leagueRow=row();pad(leagueRow,18,0,18,12);hs.addView(leagueRow);root.addView(hs);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);content=col();pad(content,18,2,18,24);sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);renderLeagues();
 }
 private void menu(){new AlertDialog.Builder(this).setTitle("ZEKIFY").setMessage("DEVELOPED BY\\nTITUS LACZEK\\n\\nX   @iam_czek\\nTikTok   @iamczek\\nInstagram   @iamczek\\nlaczekrsa@proton.me").setPositiveButton("Close",null).show();}
 private JSONObject request(String path)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)out.write(buf,0,n);c.disconnect();if(code>=400)throw new Exception("Service error "+code);return new JSONObject(out.toString("UTF-8"));}
 private void load(){io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=matches&sport=football");JSONArray a=p.optJSONArray("data");if(!p.optBoolean("success")||a==null)throw new Exception("Unexpected match response");matches.clear();for(int i=0;i<a.length();i++)matches.add(a.getJSONObject(i));runOnUiThread(()->{renderLeagues();render();});}catch(Exception e){runOnUiThread(()->{if(content!=null){content.removeAllViews();TextView err=txt("Unable to load matches. Tap to retry.",14,Color.WHITE,false);pad(err,12,25,12,25);err.setOnClickListener(v->load());content.addView(err);}});}});}
 private String team(JSONObject m,String side){JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);return t==null?"Team":t.optString("name","Team");}
 private String leagueName(JSONObject m){String s=m.optString("league","");return s.trim().isEmpty()?"Other competitions":s.trim();}
 private void renderLeagues(){if(leagueRow==null)return;leagueRow.removeAllViews();LinkedHashSet<String> ls=new LinkedHashSet<>();ls.add("All");for(JSONObject m:matches)ls.add(leagueName(m));for(String s:ls){Button b=btn(s,s.equals(league));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.rightMargin=dp(7);leagueRow.addView(b,p);b.setOnClickListener(v->{league=s;renderLeagues();render();});}}
 private void render(){if(content==null)return;content.removeAllViews();int count=0;for(JSONObject m:matches){String status=m.optString("status","upcoming");if(!status.equals(tab)||(!league.equals("All")&&!league.equals(leagueName(m))))continue;String hay=(m.optString("title")+" "+leagueName(m)+" "+team(m,"home")+" "+team(m,"away")).toLowerCase(Locale.ROOT);if(!hay.contains(query))continue;content.addView(matchCard(m));count++;}if(count==0){TextView empty=txt("No "+tab+" matches match your filters.",14,MUTED,false);pad(empty,12,36,12,36);content.addView(empty);}}
 private View matchCard(JSONObject m){LinearLayout card=col();card.setBackground(shape(PANEL,16));pad(card,15,13,15,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(11);card.setLayoutParams(cp);
  LinearLayout meta=row();meta.addView(txt(leagueName(m).toUpperCase(Locale.ROOT),10,ORANGE,true),new LinearLayout.LayoutParams(0,-2,1));boolean live=m.optString("status").equals("live");meta.addView(txt(live?"● LIVE":date(m.optLong("date",0)),10,live?0xffff7168:MUTED,true));card.addView(meta);
  LinearLayout teams=row();pad(teams,0,22,0,20);teams.addView(teamBadge(m,"home"));TextView home=txt(team(m,"home"),14,Color.WHITE,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(9);teams.addView(home,hp);TextView vs=txt("VS",10,ORANGE,true);teams.addView(vs);TextView away=txt(team(m,"away"),14,Color.WHITE,true);away.setGravity(Gravity.RIGHT);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1);ap.rightMargin=dp(9);teams.addView(away,ap);teams.addView(teamBadge(m,"away"));card.addView(teams);
  View sep=new View(this);sep.setBackgroundColor(0x22ffffff);card.addView(sep,new LinearLayout.LayoutParams(-1,dp(1)));LinearLayout foot=row();pad(foot,0,10,0,0);foot.addView(txt("Match details",11,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));foot.addView(txt("›",21,ORANGE,true));card.addView(foot);card.setOnClickListener(v->open(m));return card;}
 private View teamBadge(JSONObject m,String side){FrameLayout f=new FrameLayout(this);f.setBackground(shape(0xff252525,50));f.setElevation(dp(1));TextView fallback=txt(team(m,side).substring(0,1).toUpperCase(Locale.ROOT),14,ORANGE,true);fallback.setGravity(Gravity.CENTER);f.addView(fallback,new FrameLayout.LayoutParams(dp(34),dp(34)));JSONObject t=m.optJSONObject("teams");JSONObject tm=t==null?null:t.optJSONObject(side);String url=tm==null?"":tm.optString("badge","");if(!url.isEmpty()){if(url.startsWith("/"))url=API+url;String finalUrl=url;io.execute(()->{try{Bitmap bm=BitmapFactory.decodeStream(new URL(finalUrl).openStream());runOnUiThread(()->{if(bm!=null){f.removeAllViews();ImageView im=new ImageView(this);im.setImageBitmap(bm);im.setScaleType(ImageView.ScaleType.FIT_CENTER);f.addView(im,new FrameLayout.LayoutParams(dp(34),dp(34)));}});}catch(Exception ignored){}});}return f;}
 private String date(long v){if(v<=0)return "Kickoff TBC";return new java.text.SimpleDateFormat("EEE, d MMM · HH:mm",Locale.getDefault()).format(new Date(v));}
 private void open(JSONObject m){selected=m;detail=null;LinearLayout page=col();page.setBackgroundColor(BG);pad(page,18,18,18,18);Button back=btn("‹   Back to matches",false);back.setOnClickListener(v->homeAndRender());page.addView(back);
  TextView league=txt(leagueName(m).toUpperCase(Locale.ROOT),10,ORANGE,true);league.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(30);page.addView(league,lp);
  LinearLayout matchup=row();matchup.setGravity(Gravity.CENTER);pad(matchup,0,35,0,25);matchup.addView(teamBadge(m,"home"));TextView h=txt(team(m,"home"),15,Color.WHITE,true);h.setGravity(Gravity.CENTER);LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(0,-2,1);x.leftMargin=dp(8);matchup.addView(h,x);matchup.addView(txt("VS",11,ORANGE,true));TextView a=txt(team(m,"away"),15,Color.WHITE,true);a.setGravity(Gravity.CENTER);LinearLayout.LayoutParams y=new LinearLayout.LayoutParams(0,-2,1);y.rightMargin=dp(8);matchup.addView(a,y);matchup.addView(teamBadge(m,"away"));page.addView(matchup);
  TextView time=txt(date(m.optLong("date",0)),12,MUTED,false);time.setGravity(Gravity.CENTER);page.addView(time);
  TextView state=txt(m.optString("status").equals("live")?"● LIVE NOW":"UPCOMING FIXTURE",10,ORANGE,true);state.setGravity(Gravity.CENTER);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(12);st.bottomMargin=dp(25);page.addView(state,st);
  LinearLayout preview=col();preview.setGravity(Gravity.CENTER);preview.setBackground(shape(0xff17120f,18));pad(preview,15,35,15,35);preview.addView(txt("ZEKIFY  •  MATCH STREAM",10,ORANGE,true));TextView icon=txt("▶",30,Color.WHITE,true);icon.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(75));preview.addView(icon,ip);preview.addView(txt("STREAM SOURCES",10,MUTED,true));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(14);page.addView(preview,pp);
  TextView details=txt("MATCH DETAILS",11,ORANGE,true);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(24);page.addView(details,dp1);page.addView(txt(m.optString("title",team(m,"home")+" vs "+team(m,"away")),15,Color.WHITE,true));
  TextView sourceCount=txt("Loading stream sources…",12,MUTED,false);LinearLayout.LayoutParams sc=new LinearLayout.LayoutParams(-1,-2);sc.topMargin=dp(12);page.addView(sourceCount,sc);
  Button play=btn("▶   Watch match",true);play.setEnabled(false);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(50));bp.topMargin=dp(16);page.addView(play,bp);
  setContentView(page);io.execute(()->{try{JSONObject p=request("/api/public/football-streams?mode=detail&sport=football&id="+URLEncoder.encode(m.optString("id"),"UTF-8"));JSONObject d=p.optJSONObject("data");JSONArray sources=d==null?null:d.optJSONArray("sources");runOnUiThread(()->{detail=d;int n=sources==null?0:sources.length();sourceCount.setText(n+" available source"+(n==1?"":"s"));play.setEnabled(n>0);play.setOnClickListener(v->{Intent i=new Intent(this,PlayerActivity.class);i.putExtra("sources",sources.toString());i.putExtra("match",team(m,"home")+" vs "+team(m,"away"));startActivity(i);});});}catch(Exception e){runOnUiThread(()->sourceCount.setText("Unable to load sources. Go back and retry."));}});
 }
 private void homeAndRender(){home();renderLeagues();render();}
 @Override public void onBackPressed(){homeAndRender();}
 private static class Ball extends View{Paint p=new Paint(3);Ball(Context c){super(c);}protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),r=Math.min(w,h)*.45f,x=w/2,y=h/2;p.setColor(0xfff4f4f4);c.drawCircle(x,y,r,p);p.setColor(0xffff8a3d);Path pent=new Path();pent.moveTo(x,y-r*.42f);pent.lineTo(x+r*.34f,y-r*.12f);pent.lineTo(x+r*.21f,y+r*.3f);pent.lineTo(x-r*.21f,y+r*.3f);pent.lineTo(x-r*.34f,y-r*.12f);pent.close();c.drawPath(pent,p);p.setColor(0xff171717);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.055f));c.drawCircle(x,y,r,p);c.drawLine(x,y-r*.42f,x,y-r,p);c.drawLine(x+r*.34f,y-r*.12f,x+r*.8f,y-r*.6f,p);c.drawLine(x+r*.21f,y+r*.3f,x+r*.7f,y+r*.8f,p);c.drawLine(x-r*.21f,y+r*.3f,x-r*.7f,y+r*.8f,p);c.drawLine(x-r*.34f,y-r*.12f,x-r*.8f,y-r*.6f,p);p.setStyle(Paint.Style.FILL);}}
}
