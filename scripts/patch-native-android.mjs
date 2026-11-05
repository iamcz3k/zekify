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
await writeFile(manifestPath,manifest);
const gradlePath="android/app/build.gradle";
let gradle=await readFile(gradlePath,"utf8");
if(!gradle.includes("androidx.media3:media3-exoplayer")) {
 gradle=gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.media3:media3-exoplayer:1.5.1"\n    implementation "androidx.media3:media3-ui:1.5.1"');
}
await writeFile(gradlePath,gradle);
console.log("Configured native Android screens, vector-style branding and Media3 playback.");
