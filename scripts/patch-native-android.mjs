import { readFile, writeFile, mkdir, copyFile, readdir } from "node:fs/promises";
const pkg="com.v3x.zekify";
const root="android/app/src/main/java/"+pkg.replaceAll(".","/");
await mkdir(root,{recursive:true});
const appAssets="android/app/src/main/assets";
await mkdir(appAssets,{recursive:true});
await writeFile(appAssets+"/zekify-integrity.bin","ZEKIFY-INTEGRITY-V1:official-native-build");
await copyFile("native-android/MainActivity.java",root+"/MainActivity.java");
await copyFile("native-android/MaterialMotion.java",root+"/MaterialMotion.java");
await copyFile("native-android/OfflineMatchStore.kt",root+"/OfflineMatchStore.kt");
await copyFile("native-android/TeamBadgeCatalog.kt",root+"/TeamBadgeCatalog.kt");
await copyFile("native-android/FootballEntityActivity.kt",root+"/FootballEntityActivity.kt");
await copyFile("native-android/FollowedEntitiesActivity.kt",root+"/FollowedEntitiesActivity.kt");
await copyFile("native-android/SecurityGuard.java",root+"/SecurityGuard.java");
await copyFile("native-android/PlayerActivity.java",root+"/PlayerActivity.java");
await copyFile("native-android/FloatingPlayerService.java",root+"/FloatingPlayerService.java");
await copyFile("native-android/LiveScoreActivity.java",root+"/LiveScoreActivity.java");
await copyFile("native-android/UpdateHistoryActivity.java",root+"/UpdateHistoryActivity.java");

const playerActivityPath = root + "/PlayerActivity.java";
let playerActivity = await readFile(playerActivityPath, "utf8");
playerActivity = playerActivity.replace("public void onCreate(Bundle state) {\n        super.onCreate(state);", "public void onCreate(Bundle state) {\n        super.onCreate(state);\n        if (!SecurityGuard.verify(this)) { SecurityGuard.showBlocked(this); return; }");
playerActivity = playerActivity.replace("protected void onResume() {\n        super.onResume();", "protected void onResume() {\n        super.onResume();\n        if (!SecurityGuard.verify(this)) { SecurityGuard.showBlocked(this); return; }");
if (!playerActivity.includes("SecurityGuard.verify(this)")) throw new Error("PlayerActivity security checks did not apply");
const customViewAdd = 'root.addView(view, new FrameLayout.LayoutParams(-1, -1));';
if ((playerActivity.split(customViewAdd).length - 1) !== 2) throw new Error("Expected both Chrome fullscreen callbacks before patching");
playerActivity = playerActivity.replaceAll(customViewAdd, 'configureCustomFullscreenView(view); root.addView(view, new FrameLayout.LayoutParams(-1, -1)); hideBars();');
const hideMethod = '    private void hideCustomVideoView() {';
const fullscreenHelper = `    private void configureCustomFullscreenView(View view) {
        if (view == null) return;
        view.setBackgroundColor(Color.BLACK);
        view.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        view.setClipToOutline(false);
        applyFullscreenSurfaceSizing(view);
        view.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            applyFullscreenSurfaceSizing(v);
            hideBars();
        });
        view.requestLayout();
        view.post(this::hideBars);
    }

    private void applyFullscreenSurfaceSizing(View node) {
        if (node == null) return;
        if (node instanceof android.view.SurfaceView || node instanceof android.view.TextureView
            || node instanceof android.widget.VideoView) {
            ViewGroup.LayoutParams params = node.getLayoutParams();
            if (params != null) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                node.setLayoutParams(params);
            }
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            group.setClipChildren(false);
            group.setClipToPadding(false);
            for (int i = 0; i < group.getChildCount(); i++) {
                applyFullscreenSurfaceSizing(group.getChildAt(i));
            }
        }
    }

`;
if (!playerActivity.includes(hideMethod)) throw new Error("Fullscreen teardown anchor missing");
playerActivity = playerActivity.replace(hideMethod, fullscreenHelper + hideMethod);
if (!playerActivity.includes("configureCustomFullscreenView(view); root.addView(view")) throw new Error("Fullscreen surface sizing patch did not apply");

playerActivity = playerActivity.replace("    private View customVideoView;", "    private View customVideoView;\n    private View videoScrim;");
const scrimAnchor = "        timelinePanel = new LinearLayout(this);";
if (!playerActivity.includes(scrimAnchor)) throw new Error("Scrim insertion anchor missing");
playerActivity = playerActivity.replace(scrimAnchor, `        videoScrim = new View(this);
        videoScrim.setBackgroundColor(Color.BLACK);
        videoScrim.setAlpha(0f);
        videoScrim.setClickable(false);
        videoScrim.setFocusable(false);
        videoScrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(videoScrim, new FrameLayout.LayoutParams(-1, -1));
        videoScrim.setVisibility(View.GONE);

        timelinePanel = new LinearLayout(this);`);
const controlsAnchor = "    private void setControlsVisible(boolean visible) {";
if (!playerActivity.includes(controlsAnchor)) throw new Error("Scrim controls anchor missing");
playerActivity = playerActivity.replace(controlsAnchor, `    private void updateVideoScrim() {
        if (videoScrim == null) return;
        boolean hasVideo = customVideoView != null
            || (playerView != null && playerView.getVisibility() == View.VISIBLE)
            || (webView != null && webView.getVisibility() == View.VISIBLE);
        boolean show = controlsVisible && !screenLocked && hasVideo;
        videoScrim.animate().cancel();
        if (show) {
            videoScrim.setVisibility(View.VISIBLE);
            videoScrim.setAlpha(0f);
            videoScrim.bringToFront();
            if (timelinePanel != null) timelinePanel.bringToFront();
            if (status != null) status.bringToFront();
            if (toolbar != null) toolbar.bringToFront();
            if (gestureIndicator != null && gestureIndicator.getVisibility() == View.VISIBLE) gestureIndicator.bringToFront();
            if (unlockButton != null && unlockButton.getVisibility() == View.VISIBLE) unlockButton.bringToFront();
            videoScrim.animate().alpha(0.42f).setDuration(200L).start();
        } else {
            videoScrim.animate().alpha(0f).setDuration(200L).withEndAction(() -> {
                if (videoScrim != null && (!controlsVisible || screenLocked)) videoScrim.setVisibility(View.GONE);
            }).start();
        }
    }

    private void setControlsVisible(boolean visible) {`);
playerActivity = playerActivity.replace("        controlsVisible = visible;", "        controlsVisible = visible;\n        updateVideoScrim();");
playerActivity = playerActivity.replace("        webView.setVisibility(View.VISIBLE);\n        webView.onResume();", "        webView.setVisibility(View.VISIBLE);\n        updateVideoScrim();\n        webView.onResume();");
playerActivity = playerActivity.replace("        playerView.setVisibility(View.VISIBLE);\n        playerView.requestLayout();", "        playerView.setVisibility(View.VISIBLE);\n        updateVideoScrim();\n        playerView.requestLayout();");
if (!playerActivity.includes("videoScrim.animate().alpha(0.42f)")) throw new Error("Video scrim fade patch did not apply");
const lockStatePromptDuration = "locked ? 5000L : 1300L";
if (!playerActivity.includes(lockStatePromptDuration)) throw new Error("Lock/unlock status prompt duration anchor missing");
playerActivity = playerActivity.replace(lockStatePromptDuration, "2000L");
const tapToUnlockDuration = "controlsHandler.postDelayed(hideUnlockTask, 5000L);";
if (!playerActivity.includes(tapToUnlockDuration)) throw new Error("Tap-to-unlock prompt duration anchor missing");
playerActivity = playerActivity.replace(tapToUnlockDuration, "controlsHandler.postDelayed(hideUnlockTask, 5000L);");

await writeFile(playerActivityPath, playerActivity);

await copyFile("native-android/MatchReminderReceiver.java",root+"/MatchReminderReceiver.java");
await copyFile("native-android/MatchReminderBootReceiver.java",root+"/MatchReminderBootReceiver.java");
await copyFile("native-android/ZekifyUpdateManager.java",root+"/ZekifyUpdateManager.java");
await copyFile("native-android/LiveSourcePrefetchWorker.java",root+"/LiveSourcePrefetchWorker.java");
await copyFile("native-android/LiveSourcePrefetch.java",root+"/LiveSourcePrefetch.java");

const sportsAssetsSource = "native-android/assets/sports";
const sportsAssetsTarget = "android/app/src/main/assets/sports";
await mkdir(sportsAssetsTarget, {recursive:true});
for (const asset of await readdir(sportsAssetsSource)) {
  if (asset.endsWith(".svg")) await copyFile(sportsAssetsSource + "/" + asset, sportsAssetsTarget + "/" + asset);
}
await copyFile(sportsAssetsSource + "/LICENSE", sportsAssetsTarget + "/LICENSE");
await copyFile(sportsAssetsSource + "/README.md", sportsAssetsTarget + "/ATTRIBUTION.txt");
const footballBadgesSource="native-android/assets/football/badges";
const footballBadgesTarget="android/app/src/main/assets/football/badges";
await mkdir(footballBadgesTarget,{recursive:true});
for(const asset of await readdir(footballBadgesSource)){
 if(asset.endsWith(".svg")||asset.endsWith(".webp")||asset.endsWith(".png")||asset.endsWith(".json")||asset.endsWith(".md")||asset.endsWith(".txt")){
  await copyFile(footballBadgesSource+"/"+asset,footballBadgesTarget+"/"+asset);
 }
}

const manifestPath="android/app/src/main/AndroidManifest.xml";
let manifest=await readFile(manifestPath,"utf8");
if(manifest.includes('android:name=".MainActivity"')&&!manifest.match(/<activity[^>]*android:name=".MainActivity"[^>]*android:launchMode=/)){manifest=manifest.replace(/(<activity[^>]*android:name=".MainActivity"[^>]*)(>)/,'$1 android:launchMode="singleTop"$2');}
if(!manifest.includes('android:name=".MatchReminderReceiver"')) manifest=manifest.replace("</application>",'    <receiver android:name=".MatchReminderReceiver" android:enabled="true" android:exported="false" />\n    </application>');
if(!manifest.includes('android:name=".MatchReminderBootReceiver"')) manifest=manifest.replace("</application>",'    <receiver android:name=".MatchReminderBootReceiver" android:enabled="true" android:exported="false"><intent-filter><action android:name="android.intent.action.BOOT_COMPLETED" /><action android:name="android.intent.action.MY_PACKAGE_REPLACED" /></intent-filter></receiver>\n    </application>');
if(!manifest.includes('android:name=".PlayerActivity"')){
 manifest=manifest.replace("</application>",'    <activity android:name=".PlayerActivity" android:exported="false" android:screenOrientation="sensorLandscape" android:resizeableActivity="true" android:supportsPictureInPicture="true" android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden" />\n    </application>');
}
const mainActivityTag = manifest.match(/<activity\b(?=[^>]*android:name=".MainActivity")[^>]*>/);
if (mainActivityTag) {
 let tag = mainActivityTag[0];
 if (!tag.includes("android:resizeableActivity=")) tag = tag.replace(/>$/, ' android:resizeableActivity="true">');
 if (!tag.includes("android:supportsPictureInPicture=")) tag = tag.replace(/>$/, ' android:supportsPictureInPicture="true">');
 if (!tag.includes("android:configChanges=")) tag = tag.replace(/>$/, ' android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout">');
 else if (!/android:configChanges="[^"]*(smallestScreenSize|screenLayout)/.test(tag)) tag = tag.replace('android:configChanges="', 'android:configChanges="smallestScreenSize|screenLayout|');
 manifest = manifest.replace(mainActivityTag[0], tag);
}
if(!manifest.includes('android:enableOnBackInvokedCallback="true"'))manifest=manifest.replace("<application","<application android:enableOnBackInvokedCallback=\"true\"");
if(!manifest.includes("android.permission.VIBRATE")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.VIBRATE" />\\n    <application');
if(!manifest.includes("android.permission.INTERNET")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.INTERNET" />\n    <application');
if(!manifest.includes("android.permission.ACCESS_NETWORK_STATE")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <application');
if(!manifest.includes("android.permission.POST_NOTIFICATIONS")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n    <application');
if(!manifest.includes("android.permission.RECEIVE_BOOT_COMPLETED")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />\n    <application');
if(!manifest.includes("android.permission.REQUEST_INSTALL_PACKAGES")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n    <application');
if(!manifest.includes("android.permission.SYSTEM_ALERT_WINDOW")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n    <application');
if(!manifest.includes("android.permission.FOREGROUND_SERVICE")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n    <application');
if(!manifest.includes("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />\n    <application');
if(!manifest.includes('android:name=".FloatingPlayerService"')) manifest=manifest.replace("</application>",'    <service android:name=".FloatingPlayerService" android:enabled="true" android:exported="false" android:foregroundServiceType="mediaPlayback" />\n    </application>');
if(!manifest.includes('android:name=".LiveScoreActivity"')) manifest=manifest.replace("</application>",'    <activity android:name=".LiveScoreActivity" android:exported="false" android:screenOrientation="unspecified" />\n    </application>');
if(!manifest.includes('android:name=".UpdateHistoryActivity"')) manifest=manifest.replace("</application>",'    <activity android:name=".UpdateHistoryActivity" android:exported="false" android:screenOrientation="unspecified" />\n    </application>');
if(!manifest.includes('android:name=".FootballEntityActivity"')) manifest=manifest.replace("</application>",'    <activity android:name=".FootballEntityActivity" android:exported="false" android:screenOrientation="unspecified" />\n    </application>');
if(!manifest.includes('android:name=".FollowedEntitiesActivity"')) manifest=manifest.replace("</application>",'    <activity android:name=".FollowedEntitiesActivity" android:exported="false" android:screenOrientation="unspecified" />\n    </application>');
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
manifest=manifest.replace(/(<activity\b(?=[^>]*android:name="\.MainActivity")[^>]*?)android:theme="@style\/AppTheme\.NoActionBarLaunch"/, '$1android:theme="@style/Zekify.Splash"');
manifest=manifest.replace(/(<activity\b(?=[^>]*android:name="\.MainActivity")[^>]*?)android:theme="@style\/AppTheme\.NoActionBar"/, '$1android:theme="@style/Zekify.Splash"');
if (manifest.includes('android:name=".MainActivity"') && !manifest.includes('android:theme="@style/Zekify.Splash"')) throw new Error("Launcher activity is missing the AndroidX splash theme");
await writeFile(manifestPath,manifest);
const rootGradlePath="android/build.gradle";
let rootGradle=await readFile(rootGradlePath,"utf8");
if(!rootGradle.includes("kotlin-gradle-plugin")){
 if(!rootGradle.includes("dependencies {"))throw new Error("Could not add Kotlin Gradle plugin to Android root build");
 rootGradle=rootGradle.replace("dependencies {","dependencies {\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0'");
 await writeFile(rootGradlePath,rootGradle);
}
const gradlePath="android/app/build.gradle";
let gradle=await readFile(gradlePath,"utf8");
if(!gradle.includes("org.jetbrains.kotlin.android")&&!gradle.includes("kotlin-android")){
 if(gradle.includes("apply plugin: 'com.android.application'"))gradle=gradle.replace("apply plugin: 'com.android.application'","apply plugin: 'com.android.application'\napply plugin: 'org.jetbrains.kotlin.android'");
 else if(gradle.includes("plugins {"))gradle=gradle.replace("plugins {","plugins {\n    id 'org.jetbrains.kotlin.android' version '2.1.0'");
 else throw new Error("Could not enable Kotlin Android plugin");
}
if(!gradle.includes("androidx.core:core-splashscreen"))gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.core:core-splashscreen:1.0.1"');
if(!gradle.includes("androidx.media3:media3-exoplayer")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer:1.5.1"\n    implementation "androidx.media3:media3-exoplayer-hls:1.5.1"\n    implementation "androidx.media3:media3-ui:1.5.1"');
}
if(!gradle.includes("androidx.media3:media3-exoplayer-hls")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer-hls:1.5.1"');
}
if(!gradle.includes("androidx.work:work-runtime"))gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.work:work-runtime:2.9.1"');
if(!gradle.includes("com.caverock:androidsvg-aar")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "com.caverock:androidsvg-aar:1.4"');
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
const splashStylesPath = resourceRoot + "/values/zekify_splash_styles.xml";
await writeFile(splashStylesPath, '<?xml version="1.0" encoding="utf-8"?>\n<resources><style name="Zekify.Splash" parent="Theme.SplashScreen"><item name="windowSplashScreenBackground">@color/zekify_splash_black</item><item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item><item name="postSplashScreenTheme">@style/AppTheme.NoActionBar</item></style></resources>\n');
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
 "zekify_ic_stretch.xml",
 "zekify_ic_volume_down.xml",
 "zekify_ic_volume_up.xml",
 "zekify_ic_volume_off.xml",
 "zekify_ic_lock.xml",
 "zekify_ic_unlock.xml",
 "zekify_ic_menu.xml",
 "zekify_illustration_offline.xml",
 "zekify_illustration_empty_fixtures.xml"
]) {
 await copyFile("native-android/res/drawable/" + icon, drawableRoot + "/" + icon);
}

const mainActivityPath = root + "/MainActivity.java";
let mainActivity = await readFile(mainActivityPath, "utf8");
mainActivity = mainActivity.replace("@Override public void onCreate(Bundle b){super.onCreate(b);appPrefs=", "@Override public void onCreate(Bundle b){super.onCreate(b);appPrefs=");
mainActivity = mainActivity.replace("installCrashRecorder();pendingMatchId=", "installCrashRecorder();if(!SecurityGuard.verify(this)){SecurityGuard.showBlocked(this);return;}pendingMatchId=");
mainActivity = mainActivity.replace("@Override protected void onResume(){super.onResume();FloatingPlayerService.hideForReturn();", "@Override protected void onResume(){super.onResume();if(!SecurityGuard.verify(this)){SecurityGuard.showBlocked(this);return;}FloatingPlayerService.hideForReturn();");
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
if (mainActivity.includes('scheduled.setOnClickListener(v->{dialog.dismiss();showScheduledMatches();});') && !mainActivity.includes('ScrollView menuScroll=new ScrollView(this)')) throw new Error("Scrollable legacy menu patch did not apply");
if (!mainActivity.includes('private String team(JSONObject m,String side)')) throw new Error("Team-name resolver is missing");
mainActivity = mainActivity.replace(
 'private Dialog startupDialog;',
 'private Dialog startupDialog;\n private boolean updateCheckStarted=false;'
);
mainActivity = mainActivity.replace(
 'private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}queueUpdatesPrompt();}',
 'private void hideStartupSplash(){if(startupDialog!=null&&startupDialog.isShowing()){startupDialog.dismiss();startupDialog=null;}queueUpdatesPrompt();if(!updateCheckStarted){updateCheckStarted=true;new Handler(Looper.getMainLooper()).postDelayed(()->checkForUpdates(false),900L);}}\n private void checkForUpdates(boolean manual){ZekifyUpdateManager.check(this,(update,error)->{if(isFinishing())return;if(update!=null){String notes=update.notes==null?"Bug fixes and improvements.":update.notes;if(notes.length()>900)notes=notes.substring(0,900)+"…";ZekifyUpdateManager.showRequiredUpdate(this,update);}else if(manual){Toast.makeText(this,error==null?"Zekify is up to date.":error,Toast.LENGTH_LONG).show();}});}'
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
if (!mainActivity.includes("if(!SecurityGuard.verify(this))")) throw new Error("MainActivity security gate did not apply");
const liveScorePath = root + "/LiveScoreActivity.java";
let liveScore = await readFile(liveScorePath, "utf8");
liveScore = liveScore.replace("@Override public void onCreate(Bundle state){super.onCreate(state);", "@Override public void onCreate(Bundle state){super.onCreate(state);if(!SecurityGuard.verify(this)){SecurityGuard.showBlocked(this);return;}");
if (!liveScore.includes("if(!SecurityGuard.verify(this))")) throw new Error("LiveScoreActivity security gate did not apply");
await writeFile(liveScorePath, liveScore);
const floatingServicePath = root + "/FloatingPlayerService.java";
let floatingService = await readFile(floatingServicePath, "utf8");
floatingService = floatingService.replace("super.onCreate();instance=this;", "super.onCreate();if(!SecurityGuard.verify(this)){stopSelf();return;}instance=this;");
floatingService = floatingService.replace("@Override public int onStartCommand(Intent intent,int flags,int startId){attachOverlay();return START_NOT_STICKY;}", "@Override public int onStartCommand(Intent intent,int flags,int startId){if(!SecurityGuard.verify(this)){stopSelf(startId);return START_NOT_STICKY;}attachOverlay();return START_NOT_STICKY;}");
if (!floatingService.includes("if(!SecurityGuard.verify(this))")) throw new Error("FloatingPlayerService security gate did not apply");
await writeFile(floatingServicePath, floatingService);

// Security verification is Java-only so unsupported ABIs and vendor ROMs cannot crash startup.
let hg=await readFile(gradlePath,"utf8");
if(!hg.includes("minifyEnabled true")) hg=hg.replace("release {","release {\n            minifyEnabled true\n            shrinkResources false\n            proguardFiles getDefaultProguardFile(\"proguard-android-optimize.txt\"), \"proguard-rules.pro\"");
await writeFile(gradlePath,hg);
await writeFile("android/app/proguard-rules.pro","-keep class com.v3x.zekify.SecurityGuard { *; }\n-keep class com.v3x.zekify.** extends android.app.Activity\n-keep class com.v3x.zekify.** extends android.app.Service\n-dontwarn org.apache.**\n");
console.log("Configured native Android screens, player vector icons, black system splash and Media3 playback.");
