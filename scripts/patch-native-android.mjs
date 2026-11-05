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
console.log("Configured native Android screens, black system splash and Media3 playback.");
