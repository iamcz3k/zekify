import { readFile, readdir } from "node:fs/promises";

const manifestPath = "android/app/src/main/AndroidManifest.xml";
const manifest = await readFile(manifestPath, "utf8");
if (!manifest.includes("android.intent.action.MAIN") || !manifest.includes("android.intent.category.LAUNCHER")) {
  throw new Error("Android launcher activity is missing.");
}
if (!manifest.includes('android:name=".MainActivity"')) {
  throw new Error("Capacitor MainActivity is missing.");
}
const resourceRoot = "android/app/src/main/res";
const resourceDirs = await readdir(resourceRoot, { withFileTypes: true });
let launcherIconFound = false;
for (const entry of resourceDirs.filter(item => item.isDirectory() && item.name.startsWith("mipmap"))) {
  const files = await readdir(resourceRoot + "/" + entry.name);
  if (files.includes("ic_launcher.png") || files.includes("ic_launcher.webp") || files.includes("ic_launcher.xml")) launcherIconFound = true;
}
if (!launcherIconFound) throw new Error("Android launcher icon was not generated.");
console.log("Verified standard Capacitor launcher activity and generated launcher icons.");
