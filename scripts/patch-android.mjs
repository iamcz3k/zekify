import { readFile, readdir, mkdir, writeFile } from "node:fs/promises";

const manifestPath = "android/app/src/main/AndroidManifest.xml";
const manifest = await readFile(manifestPath, "utf8");
if (!manifest.includes("android.intent.action.MAIN") || !manifest.includes("android.intent.category.LAUNCHER")) {
  throw new Error("Android launcher activity is missing.");
}
if (!manifest.includes('android:name=".MainActivity"')) {
  throw new Error("Capacitor MainActivity is missing.");
}

const packageName = "com.v3x.zekify";
const packagePath = packageName.replaceAll(".", "/");
const javaRoot = "android/app/src/main/java/" + packagePath;
await mkdir(javaRoot, { recursive: true });

const pluginPath = javaRoot + "/ZekifyDisplayPlugin.java";
const pluginSource = `package ${packageName};

import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

@CapacitorPlugin(name = "ZekifyDisplay")
public class ZekifyDisplayPlugin extends Plugin {
    private static volatile boolean immersiveEnabled = false;
    private static float originalBrightness = -1f;
    private static boolean brightnessCaptured = false;

    public static void applySystemBars(Window window) {
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, immersiveEnabled);
        window.setStatusBarColor(android.graphics.Color.BLACK);
        window.setNavigationBarColor(android.graphics.Color.BLACK);
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller == null) return;
        if (immersiveEnabled) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    public static void reapply(Window window) {
        if (immersiveEnabled) applySystemBars(window);
    }

    @PluginMethod
    public void setImmersive(PluginCall call) {
        boolean enabled = call.getBoolean("enabled", false);
        immersiveEnabled = enabled;
        applySystemBars(getActivity().getWindow());
        JSObject result = new JSObject();
        result.put("enabled", enabled);
        call.resolve(result);
    }

    @PluginMethod
    public void setBrightness(PluginCall call) {
        if (call.getDouble("level") == null) {
            call.reject("Brightness level is required");
            return;
        }
        float level = (float) Math.max(0.05, Math.min(1.0, call.getDouble("level")));
        Window window = getActivity().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        if (!brightnessCaptured) {
            originalBrightness = params.screenBrightness;
            brightnessCaptured = true;
        }
        params.screenBrightness = level;
        window.setAttributes(params);
        JSObject result = new JSObject();
        result.put("level", level);
        call.resolve(result);
    }

    @PluginMethod
    public void restore(PluginCall call) {
        immersiveEnabled = false;
        Window window = getActivity().getWindow();
        applySystemBars(window);
        if (brightnessCaptured) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.screenBrightness = originalBrightness;
            window.setAttributes(params);
            originalBrightness = -1f;
            brightnessCaptured = false;
        }
        call.resolve();
    }
}
`;
await writeFile(pluginPath, pluginSource);

const activityPath = javaRoot + "/MainActivity.java";
let activity = "";
try { activity = await readFile(activityPath, "utf8"); } catch {}
if (!activity) {
  activity = `package ${packageName};

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ZekifyDisplayPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) ZekifyDisplayPlugin.reapply(getWindow());
    }
}
`;
} else {
  if (!activity.includes("registerPlugin(ZekifyDisplayPlugin.class)")) {
    activity = activity.replace("public class MainActivity extends BridgeActivity {", "public class MainActivity extends BridgeActivity {\n    @Override\n    public void onCreate(android.os.Bundle savedInstanceState) {\n        registerPlugin(ZekifyDisplayPlugin.class);\n        super.onCreate(savedInstanceState);\n    }\n\n    @Override\n    public void onWindowFocusChanged(boolean hasFocus) {\n        super.onWindowFocusChanged(hasFocus);\n        if (hasFocus) ZekifyDisplayPlugin.reapply(getWindow());\n    }");
  }
}
await writeFile(activityPath, activity);

const resourceRoot = "android/app/src/main/res";
const resourceDirs = await readdir(resourceRoot, { withFileTypes: true });
let launcherIconFound = false;
for (const entry of resourceDirs.filter(item => item.isDirectory() && item.name.startsWith("mipmap"))) {
  const files = await readdir(resourceRoot + "/" + entry.name);
  if (files.includes("ic_launcher.png") || files.includes("ic_launcher.webp") || files.includes("ic_launcher.xml")) launcherIconFound = true;
}
if (!launcherIconFound) throw new Error("Android launcher icon was not generated.");
if (!activity.includes("registerPlugin(ZekifyDisplayPlugin.class)")) throw new Error("Native display plugin was not registered.");
console.log("Installed and verified Zekify native immersive display and brightness plugin.");
