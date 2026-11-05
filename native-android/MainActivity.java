package com.v3x.zekify;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API = "https://laczekstream.qzz.io";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> matches = new ArrayList<>();
    private LinearLayout root, list;
    private EditText search;
    private String tab = "live", league = "All", query = "";
    private boolean detailsOpen = false;
    private JSONObject selectedMatch, detailMatch;
    private int orange = Color.rgb(255,138,61), bg = Color.rgb(8,8,8), card = Color.rgb(20,20,20), muted = Color.rgb(160,160,160);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildHome();
        loadMatches();
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT); v.setTextIsSelectable(false); return v;
    }
    private Button button(String s) {
        Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(shape(orange,18)); b.setPadding(18,8,18,8); return b;
    }
    private void buildHome() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(18,12,18,12);
        TextView brand=text("⚽  ZEKIFY",22,Color.WHITE,true); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button menu=button("Menu"); menu.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("ZEKIFY").setMessage("Developed by TITUS LACZEK\n\nX: @iam_czek\nTikTok: @iamczek\nInstagram: @iamczek\nlaczekrsa@proton.me").setPositiveButton("Close",null).show()); header.addView(menu);
        root.addView(header);
        LinearLayout tabs=new LinearLayout(this); tabs.setPadding(16,4,16,10);
        Button live=button("● Live"); Button upcoming=button("Upcoming");
        live.setOnClickListener(v->{tab="live";renderMatches();}); upcoming.setOnClickListener(v->{tab="upcoming";renderMatches();});
        tabs.addView(live,new LinearLayout.LayoutParams(0,-2,1)); tabs.addView(upcoming,new LinearLayout.LayoutParams(0,-2,1)); root.addView(tabs);
        search=new EditText(this); search.setSingleLine(true); search.setHint("Search teams, matches, leagues"); search.setHintTextColor(muted); search.setTextColor(Color.WHITE); search.setBackground(shape(card,16)); search.setPadding(16,8,16,8);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.setMargins(16,0,16,10);root.addView(search,sp);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase(Locale.ROOT);renderMatches();}public void afterTextChanged(Editable e){}});
        ScrollView scroll=new ScrollView(this); list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(16,4,16,18);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private JSONObject request(String path) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection(); c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("Accept","application/json");
        InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);c.disconnect();return new JSONObject(out.toString("UTF-8"));
    }
    private void loadMatches(){
        list.removeAllViews();list.addView(text("Loading football…",16,Color.WHITE,false));
        io.execute(()->{try{JSONObject json=request("/api/public/football-streams?mode=matches&sport=football");JSONArray a=json.optJSONArray("data");matches.clear();if(a!=null)for(int i=0;i<a.length();i++)matches.add(a.getJSONObject(i));runOnUiThread(this::renderMatches);}catch(Exception e){runOnUiThread(()->{list.removeAllViews();TextView err=text("Unable to load matches. Tap to retry.",16,Color.WHITE,false);err.setPadding(20,24,20,24);err.setOnClickListener(v->loadMatches());list.addView(err);});}});
    }
    private String team(JSONObject m,String side){JSONObject t=m.optJSONObject("teams");JSONObject x=t==null?null:t.optJSONObject(side);return x==null?"Team":x.optString("name","Team");}
    private String matchSearch(JSONObject m){return (m.optString("title")+" "+m.optString("league")+" "+team(m,"home")+" "+team(m,"away")).toLowerCase(Locale.ROOT);}
    private void renderMatches(){
        if(list==null||detailsOpen)return;list.removeAllViews();int count=0;
        for(JSONObject m:matches){if(!tab.equals(m.optString("status","upcoming")))continue;if(!query.isEmpty()&&!matchSearch(m).contains(query))continue;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(16,14,16,14);item.setBackground(shape(card,18));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,10);list.addView(item,lp);
            String l=m.optString("league","Football");TextView leagueView=text(l.toUpperCase(Locale.ROOT),11,orange,true);item.addView(leagueView);
            TextView teams=text(team(m,"home")+"   VS   "+team(m,"away"),17,Color.WHITE,true);teams.setPadding(0,10,0,10);item.addView(teams);
            TextView status=text(m.optString("status").equals("live")?"● LIVE":"Tap for match details",12,muted,false);item.addView(status);
            item.setOnClickListener(v->openMatch(m));count++;
        }
        if(count==0)list.addView(text("No "+tab+" matches found.",15,muted,false));
    }
    private void openMatch(JSONObject m){
        selectedMatch=m;detailsOpen=true;root.removeAllViews();root.setPadding(18,18,18,18);root.setOrientation(LinearLayout.VERTICAL);
        Button back=button("‹  Back to matches");back.setOnClickListener(v->{detailsOpen=false;buildHome();renderMatches();});root.addView(back);
        TextView title=text(team(m,"home")+"   VS   "+team(m,"away"),24,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setPadding(0,35,0,20);root.addView(title);
        TextView league=text(m.optString("league","Football"),14,orange,true);league.setGravity(Gravity.CENTER);root.addView(league);
        TextView status=text(m.optString("status","").toUpperCase(Locale.ROOT),12,muted,true);status.setGravity(Gravity.CENTER);status.setPadding(0,10,0,25);root.addView(status);
        TextView loading=text("Loading stream sources…",14,muted,false);loading.setGravity(Gravity.CENTER);root.addView(loading);
        io.execute(()->{try{JSONObject data=request("/api/public/football-streams?mode=detail&sport=football&id="+URLEncoder.encode(m.optString("id"),"UTF-8"));detailMatch=data.optJSONObject("data");runOnUiThread(()->showDetail(loading));}catch(Exception e){runOnUiThread(()->loading.setText("Could not load details. Tap Back and retry."));}});
    }
    private void showDetail(TextView loading){
        if(detailMatch==null)return;JSONArray sources=detailMatch.optJSONArray("sources");int n=sources==null?0:sources.length();
        loading.setText(n+" stream source"+(n==1?"":"s")+" available");
        Button play=button("▶  Play match");play.setEnabled(n>0);play.setOnClickListener(v->{if(n>0){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("sources",sources.toString());i.putExtra("match",team(selectedMatch,"home")+" vs "+team(selectedMatch,"away"));startActivity(i);}});
        root.addView(play);
        if(n>1){TextView sourceHint=text("Multiple sources available. Choose one inside the player.",13,muted,false);sourceHint.setPadding(0,14,0,0);root.addView(sourceHint);}
    }
    @Override public void onBackPressed(){if(detailsOpen){detailsOpen=false;buildHome();renderMatches();}else super.onBackPressed();}
}
