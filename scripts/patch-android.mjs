import { mkdir, readFile, writeFile, readdir } from "node:fs/promises";
import { dirname } from "node:path";

const appId = "com.v3x.zekify";
const javaDir = "android/app/src/main/java/" + appId.replaceAll(".", "/");
await mkdir(javaDir, { recursive: true });

const mainActivity = `package ${appId};

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private boolean immersive = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ImmersiveModePlugin.class);
        super.onCreate(savedInstanceState);
    }

    public void setImmersiveMode(boolean enabled) {
        immersive = enabled;
        getWindow().getDecorView().setSystemUiVisibility(enabled ? (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        ) : View.SYSTEM_UI_FLAG_VISIBLE);
        if (enabled) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && immersive) setImmersiveMode(true);
    }
}
`;

const immersivePlugin = `package ${appId};

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "ImmersiveMode")
public class ImmersiveModePlugin extends Plugin {
    @PluginMethod
    public void enter(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ((MainActivity) getActivity()).setImmersiveMode(true);
            call.resolve(new JSObject());
        });
    }

    @PluginMethod
    public void exit(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            ((MainActivity) getActivity()).setImmersiveMode(false);
            call.resolve(new JSObject());
        });
    }
}
`;

await writeFile(javaDir + "/MainActivity.java", mainActivity);
await writeFile(javaDir + "/ImmersiveModePlugin.java", immersivePlugin);

const manifestPath = "android/app/src/main/AndroidManifest.xml";
let manifest = await readFile(manifestPath, "utf8");
if (!manifest.includes('android:name=".MainActivity"')) {
  throw new Error("Generated Android manifest is missing the Capacitor MainActivity.");
}
if (!manifest.includes('android.intent.action.MAIN') || !manifest.includes('android.intent.category.LAUNCHER')) {
  manifest = manifest.replace(
    /(<activity[^>]*android:name="\.MainActivity"[^>]*>)/,
    '$1\\n            <intent-filter>\\n                <action android:name="android.intent.action.MAIN" />\\n                <category android:name="android.intent.category.LAUNCHER" />\\n            </intent-filter>'
  );
}
manifest = manifest.replace(/android:exported="false"/, 'android:exported="true"');
await writeFile(manifestPath, manifest);

const resourceRoot = "android/app/src/main/res";
const resourceDirs = await readdir(resourceRoot, { withFileTypes: true });
let launcherIconFound = false;
for (const entry of resourceDirs.filter(item => item.isDirectory() && item.name.startsWith("mipmap"))) {
  const files = await readdir(resourceRoot + "/" + entry.name);
  if (files.some(name => /^ic_launcher(_round)?\\.(png|webp|xml)$/.test(name))) launcherIconFound = true;
}
if (!launcherIconFound) {
  throw new Error("Android launcher/adaptive icon was not generated. Check assets:generate and capacitor-assets.");
}
if (!manifest.includes('android:icon="@mipmap/ic_launcher"')) {
  manifest = manifest.replace(/<application([^>]*)>/, '<application$1 android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round">');
  await writeFile(manifestPath, manifest);
}
console.log("Patched immersive Android player, registered native bridge, and verified launcher icon.");
