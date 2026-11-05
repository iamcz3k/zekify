import { readFile, writeFile, mkdir, copyFile } from "node:fs/promises";
const pkg="com.v3x.zekify";
const root="android/app/src/main/java/"+pkg.replaceAll(".","/");
await mkdir(root,{recursive:true});
await copyFile("native-android/MainActivity.java",root+"/MainActivity.java");
await copyFile("native-android/PlayerActivity.java",root+"/PlayerActivity.java");
const manifestPath="android/app/src/main/AndroidManifest.xml";
let manifest=await readFile(manifestPath,"utf8");
if(!manifest.includes('android:name=".PlayerActivity"')){
 manifest=manifest.replace("</application>",'    <activity android:name=".PlayerActivity" android:exported="false" android:screenOrientation="sensorLandscape" android:configChanges="orientation|screenSize|keyboardHidden" />\n    </application>');
}
if(!manifest.includes("android.permission.INTERNET")) manifest=manifest.replace("<application",'<uses-permission android:name="android.permission.INTERNET" />\n    <application');
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

// Force the Android system splash surface to true black on every supported theme variant.
const fs = await import("node:fs/promises");
const resourceRoot = "android/app/src/main/res";
await mkdir(resourceRoot + "/values", {recursive:true});
const colorsPath = resourceRoot + "/values/zekify_colors.xml";
await writeFile(colorsPath, '<?xml version="1.0" encoding="utf-8"?>\\n<resources><color name="zekify_splash_black">#000000</color></resources>\\n');
for (const folder of ["values","values-night","values-v31","values-night-v31"]) {
 const stylePath = resourceRoot + "/" + folder + "/styles.xml";
 try {
  let styles = await readFile(stylePath,"utf8");
  styles = styles.replace(/(<style\\s+name="AppTheme\\.NoActionBarLaunch"[^>]*>)([\\s\\S]*?)(<\\/style>)/, (all,open,body,close) => {
   body = body.replace(/<item\\s+name="windowSplashScreenBackground">[\\s\\S]*?<\\/item>/g, "");
   body = body.replace(/<item\\s+name="android:windowBackground">[\\s\\S]*?<\\/item>/g, "");
   return open + body + '<item name="windowSplashScreenBackground">@color/zekify_splash_black</item><item name="android:windowBackground">@color/zekify_splash_black</item>' + close;
  });
  await writeFile(stylePath,styles);
 } catch (e) { if (e.code !== "ENOENT") throw e; }
}
\nconsole.log("Configured native Android screens, black system splash and Media3 playback.");
