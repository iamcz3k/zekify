import { readFile, writeFile, mkdir, copyFile } from "node:fs/promises";
const pkg="com.v3x.zekify";
const root="android/app/src/main/java/"+pkg.replaceAll(".","/");
await mkdir(root,{recursive:true});
await copyFile("native-android/MainActivity.java",root+"/MainActivity.java");
await copyFile("native-android/PlayerActivity.java",root+"/PlayerActivity.java");

const playerActivityPath = root + "/PlayerActivity.java";
let playerActivity = await readFile(playerActivityPath, "utf8");
playerActivity = playerActivity.replace(
 'if (!controlsVisible) setControlsVisible(true);',
 'if (customVideoView != null) { setControlsVisible(false); return; } if (!controlsVisible) setControlsVisible(true);'
);
playerActivity = playerActivity.replace(
 'controlsVisible = visible;',
 'if (customVideoView != null) visible = false; controlsVisible = visible;'
);
if (!playerActivity.includes('if (customVideoView != null) { setControlsVisible(false); return; }')) throw new Error("Fullscreen controls guard did not apply");
await writeFile(playerActivityPath, playerActivity);

await copyFile("native-android/MatchReminderReceiver.java",root+"/MatchReminderReceiver.java");
await copyFile("native-android/MatchReminderBootReceiver.java",root+"/MatchReminderBootReceiver.java");
await copyFile("native-android/ZekifyUpdateManager.java",root+"/ZekifyUpdateManager.java");
const manifestPath="android/app/src/main/AndroidManifest.xml";
let manifest=await readFile(manifestPath,"utf8");
if(manifest.includes('android:name=".MainActivity"')&&!manifest.match(/<activity[^>]*android:name=".MainActivity"[^>]*android:launchMode=/)){manifest=manifest.replace(/(<activity[^>]*android:name=".MainActivity"[^>]*)(>)/,'$1 android:launchMode="singleTop"$2');}
if(!manifest.includes('android:name=".MatchReminderReceiver"')) manifest=manifest.replace("</application>",'    <receiver android:name=".MatchReminderReceiver" android:enabled="true" android:exported="false" />\n    </application>');
if(!manifest.includes('android:name=".MatchReminderBootReceiver"')) manifest=manifest.replace("</application>",'    <receiver android:name=".MatchReminderBootReceiver" android:enabled="true" android:exported="false"><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /><action android:name="android.intent.action.MY_PACKAGE_REPLACED" /></intent-filter></receiver>\n    </application>');
if(!manifest.includes('android:name=".PlayerActivity"')){
 manifest=manifest.replace("</application>",'    <activity android:name=".PlayerActivity" android:exported="false" android:screenOrientation="sensorLandscape" android:supportsPictureInPicture="true" android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden" />\n    </application>');
}
const mainActivityTag = manifest.match(/<activity\b(?=[^>]*android:name=".MainActivity")[^>]*>/);
if (mainActivityTag) {
 let tag = mainActivityTag[0];
 if (!tag.includes("android:supportsPictureInPicture=")) tag = tag.replace(/>$/, ' android:supportsPictureInPicture="true">');
 if (!tag.includes("android:configChanges=")) tag = tag.replace(/>$/, ' android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout">');
 else if (!/android:configChanges="[^"]*(smallestScreenSize|screenLayout)/.test(tag)) tag = tag.replace('android:configChanges="', 'android:configChanges="smallestScreenSize|screenLayout|');
 manifest = manifest.replace(mainActivityTag[0], tag);
}
if(!manifest.includes("android.permission.INTERNET")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.INTERNET" />\n    <application');
if(!manifest.includes("android.permission.ACCESS_NETWORK_STATE")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <application');
if(!manifest.includes("android.permission.POST_NOTIFICATIONS")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <application');
if(!manifest.includes("android.permission.RECEIVE_BOOT_COMPLETED")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n    <application');
if(!manifest.includes("android.permission.REQUEST_INSTALL_PACKAGES")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    <application');
if(manifest.includes('android:name="androidx.core.content.FileProvider"')){
 const providerAt=manifest.indexOf('android:name="androidx.core.content.FileProvider"');
 const resourceAt=manifest.indexOf('android:resource="@xml/',providerAt);
 if(resourceAt>=0){const resourceValueStart=resourceAt+'android:resource="'.length;const resourceEnd=manifest.indexOf('"',resourceValueStart);manifest=manifest.slice(0,resourceAt)+'android:resource="@xml/zekify_file_paths"'+manifest.slice(resourceEnd+1);}
}else{
 manifest=manifest.replace("</application>",'    <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/zekify_file_paths" /></provider>\n    </application>');
}
const xmlRoot="android/app/src/main/res/xml";
await mkdir(xmlRoot,{recursive:true});
await writeFile(xmlRoot+"/zekify_file_paths.xml",'<?xml version="1.0" encoding="utf-8"?>\n<paths xmlns:android="http://schemas.android.com/apk/res/android"><cache-path name="shared_apks" path="shared/" /></paths>\n');
// MainActivity is a real Android Activity, not Capacitor BridgeActivity. It must not keep the launch-only splash theme after startup.
manifest=manifest.replace(/(<activity\b(?=[^>]*android:name="\.MainActivity")[^>]*?)android:theme="@style\/AppTheme\.NoActionBarLaunch"/, '$1android:theme="@style/AppTheme.NoActionBar"');
if (manifest.includes('android:name=".MainActivity"') && manifest.includes('android:theme="@style/AppTheme.NoActionBarLaunch"')) throw new Error("Launcher activity still uses splash-only theme");
await writeFile(manifestPath,manifest);
const gradlePath="android/app/build.gradle";
let gradle=await readFile(gradlePath,"utf8");
if(!gradle.includes("androidx.media3:media3-exoplayer")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer:1.5.1"\n    implementation "androidx.media3:media3-exoplayer-hls:1.5.1"\n    implementation "androidx.media3:media3-ui:1.5.1"');
}
if(!gradle.includes("androidx.media3:media3-exoplayer-hls")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer-hls:1.5.1"');
}
await writeFile(gradlePath,gradle);
const wrapperPath="android/gradle/wrapper/gradle-wrapper.properties";
let wrapper=await readFile(wrapperPath,"utf8");
const newline=String.fromCharCode(10);
if(wrapper.includes("networkTimeout=")) wrapper=wrapper.split(newline).map(line=>line.startsWith("networkTimeout=")?"networkTimeout=120000":line).join(newline);
else wrapper += newline+"networkTimeout=120000"+newline;
await writeFile(wrapperPath,wrapper);

// Force the Android system splash surface to true black on every supported theme variant.
const resourceRoot = "android/app/src/main/res";
await mkdir(resourceRoot + "/values", {recursive:true});
const colorsPath = resourceRoot + "/values/zekify_colors.xml";
await writeFile(colorsPath, '<?xml version="1.0" encoding="utf-8"?>\n<resources><color name="zekify_splash_black">#000000</color></resources>\n');
for (const folder of ["values","values-night","values-v31","values-night-v31"]) {
 const stylePath = resourceRoot + "/" + folder + "/styles.xml";
 try {
  let styles = await readFile(stylePath,"utf8");
  const open = styles.indexOf('<style name="AppTheme.NoActionBarLaunch"');
  const close = open < 0 ? -1 : styles.indexOf("</style>", open);
  if (open >= 0 && close >= 0) {
   const blackItems = '<item name="windowSplashScreenBackground">@color/zekify_splash_black</item><item name="android:windowBackground">@color/zekify_splash_black</item>';
   styles = styles.slice(0, close) + blackItems + styles.slice(close);
  }
  await writeFile(stylePath,styles);
 } catch (e) { if (e.code !== "ENOENT") throw e; }
}
const drawableRoot = "android/app/src/main/res/drawable";
await mkdir(drawableRoot, {recursive:true});
for (const icon of [
 "zekify_ic_back.xml",
 "zekify_ic_refresh.xml",
 "zekify_ic_sources.xml",
 "zekify_ic_fit.xml",
 "zekify_ic_volume_down.xml",
 "zekify_ic_volume_up.xml"
]) {
 await copyFile("native-android/res/drawable/" + icon, drawableRoot + "/" + icon);
}

const mainActivityPath = root + "/MainActivity.java";
let mainActivity = await readFile(mainActivityPath, "utf8");
mainActivity = mainActivity.replace(
 'private String team(JSONObject m,String side){JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);return t==null?"Team":t.optString("name","Team");}',
 'private String team(JSONObject m,String side){if(m==null)return "Team";JSONObject teams=m.optJSONObject("teams"),t=teams==null?null:teams.optJSONObject(side);String name=t==null?"":t.optString("name","").trim();return name.isEmpty()||"null".equalsIgnoreCase(name)?"Team":name;}'
);
mainActivity = mainActivity.replace(
 'View spacer=new View(this);panel.addView(spacer,new LinearLayout.LayoutParams(1,0,1));View divider=new View(this);',
 'View divider=new View(this);'
);
mainActivity = mainActivity.replace(
 'dialog.setContentView(panel);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();',
 'ScrollView menuScroll=new ScrollView(this);menuScroll.setFillViewport(true);menuScroll.setVerticalScrollBarEnabled(false);menuScroll.setBackgroundColor(Color.TRANSPARENT);menuScroll.addView(panel,new ScrollView.LayoutParams(-1,-2));dialog.setContentView(menuScroll);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(true);dialog.show();Window w=dialog.getWindow();'
);
if (!mainActivity.includes('ScrollView menuScroll=new ScrollView(this)')) throw new Error("Scrollable menu patch did not apply");
if (!mainActivity.includes('String name=t==null?"":t.optString("name","").trim()')) throw new Error("Safe team name patch did not apply");
mainActivity = mainActivity.replace(
 'private Dialog startupDialog;',
 'private Dialog startupDialog;\n private boolean updateCheckStarted=false;'
);
mainActivity = mainActivity.replace(
 'private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}}',
 'private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}if(!updateCheckStarted){updateCheckStarted=true;new Handler(Looper.getMainLooper()).postDelayed(()->checkForUpdates(false),900L);}}\n private void checkForUpdates(boolean manual){ZekifyUpdateManager.check(this,(update,error)->{if(isFinishing())return;if(update!=null){String notes=update.notes==null?"Bug fixes and improvements.":update.notes;if(notes.length()>900)notes=notes.substring(0,900)+"…";ZekifyUpdateManager.showRequiredUpdate(this,update);}else if(manual){Toast.makeText(this,error==null?"Zekify is up to date.":error,Toast.LENGTH_LONG).show();}});}'
);
mainActivity = mainActivity.replace(
 'scheduled.setOnClickListener(v->{dialog.dismiss();showScheduledMatches();});',
 'scheduled.setOnClickListener(v->{dialog.dismiss();showScheduledMatches();});\n  LinearLayout updateRow=row();updateRow.setBackground(shape(0xff191919,14));pad(updateRow,14,13,14,13);LinearLayout.LayoutParams updateRowP=new LinearLayout.LayoutParams(-1,dp(54));updateRowP.topMargin=dp(8);panel.addView(updateRow,updateRowP);NativeIcon updateIcon=new NativeIcon(this,"refresh",ORANGE);updateRow.addView(updateIcon,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView updateLabel=txt("Check for updates",13,Color.WHITE,true);LinearLayout.LayoutParams updateLabelP=new LinearLayout.LayoutParams(0,-2,1);updateLabelP.leftMargin=dp(12);updateRow.addView(updateLabel,updateLabelP);updateRow.setOnClickListener(v->{dialog.dismiss();checkForUpdates(true);});\n  String crashReport=appPrefs==null?"":appPrefs.getString("last_uncaught_error",appPrefs.getString("last_render_error",""));\n  if(crashReport!=null&&!crashReport.trim().isEmpty()){LinearLayout crashRow=row();crashRow.setBackground(shape(0xff191919,14));pad(crashRow,14,13,14,13);LinearLayout.LayoutParams crashP=new LinearLayout.LayoutParams(-1,dp(54));crashP.topMargin=dp(8);panel.addView(crashRow,crashP);NativeIcon crashIcon=new NativeIcon(this,"copy",ORANGE);crashRow.addView(crashIcon,new LinearLayout.LayoutParams(dp(23),dp(23)));TextView crashLabel=txt("Copy crash report",13,Color.WHITE,true);LinearLayout.LayoutParams crashLabelP=new LinearLayout.LayoutParams(0,-2,1);crashLabelP.leftMargin=dp(12);crashRow.addView(crashLabel,crashLabelP);crashRow.setOnClickListener(v->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("Zekify crash report",crashReport));Toast.makeText(this,"Crash report copied.",Toast.LENGTH_SHORT).show();});}'
);
mainActivity = mainActivity.replace(
 '@Override protected void onResume(){super.onResume();if(PlayerActivity.returningSourceIndex>=0)',
 '@Override protected void onResume(){super.onResume();ZekifyUpdateManager.resumeInstall(this);if(PlayerActivity.returningSourceIndex>=0)'
);

if (!mainActivity.includes("badgeWaiters")) {
 mainActivity = mainActivity.replace(
  'private final Set<String> badgeRequests=Collections.synchronizedSet(new HashSet<>());',
  'private final Set<String> badgeRequests=Collections.synchronizedSet(new HashSet<>());\n private final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<FrameLayout>>> badgeWaiters=new java.util.concurrent.ConcurrentHashMap<>();'
 );
}
mainActivity = mainActivity.replace(
 'if(!badgeRequests.add(badgeUrl))return frame;',
 'badgeWaiters.computeIfAbsent(badgeUrl,k->new java.util.concurrent.CopyOnWriteArrayList<>()).add(new java.lang.ref.WeakReference<>(frame));\n  if(!badgeRequests.add(badgeUrl))return frame;'
);
mainActivity = mainActivity.replace(
 'final Bitmap result=bitmap;\n   if(result!=null&&!isFinishing())runOnUiThread(()->showBadge(frame,result,badgeUrl));',
 'final Bitmap result=bitmap;\n   java.util.List<java.lang.ref.WeakReference<FrameLayout>> waiting=badgeWaiters.remove(badgeUrl);\n   if(result!=null&&waiting!=null&&!isFinishing())runOnUiThread(()->{for(java.lang.ref.WeakReference<FrameLayout> ref:waiting){FrameLayout target=ref.get();if(target!=null)showBadge(target,result,badgeUrl);}});'
);
mainActivity = mainActivity.replace(
 'if(bitmap==null||frame==null||!expectedUrl.equals(frame.getTag())||!frame.isAttachedToWindow())return;',
 'if(bitmap==null||frame==null||!expectedUrl.equals(frame.getTag()))return;'
);
mainActivity = mainActivity.replace(
 'if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.loadUrl("about:blank");}inlineFrame=null;',
 'if(inlineWebView!=null){inlineWebView.stopLoading();inlineWebView.loadUrl("about:blank");inlineWebView.setVisibility(View.GONE);}inlineFrame=null;'
);


mainActivity = mainActivity.replace(
 'root=screen;setContentView(screen);',
 'root=screen;setContentView(screen);screen.setAlpha(0f);screen.animate().alpha(1f).setDuration(180L).start();'
);
mainActivity = mainActivity.replace(
 'setContentView(root);renderLeagues();',
 'setContentView(root);root.setAlpha(0f);root.animate().alpha(1f).setDuration(180L).start();renderLeagues();'
);
mainActivity = mainActivity.replace(
 'card.setOnClickListener(v->selectMatch(m));return card;',
 'card.setOnClickListener(v->{card.animate().alpha(0.88f).setDuration(65L).withEndAction(()->{card.animate().alpha(1f).setDuration(100L).start();selectMatch(m);}).start();});return card;'
);
if (!mainActivity.includes("badgeWaiters")) throw new Error("Badge cache navigation patch did not apply");
await writeFile(mainActivityPath, mainActivity);

console.log("Configured native Android screens, player vector icons, black system splash and Media3 playback.");
