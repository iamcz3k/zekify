import type { CapacitorConfig } from "@capacitor/cli";
const config: CapacitorConfig = {
 appId: "com.v3x.zekify",
 appName: "Zekify",
 webDir: "dist",
 server: { androidScheme: "https" },
 plugins: {
  SplashScreen: {
   launchShowDuration: 1800,
   launchAutoHide: true,
   backgroundColor: "#000000",
   androidScaleType: "CENTER_INSIDE",
   showSpinner: false,
   splashFullScreen: true,
   splashImmersive: true
  }
 }
};
export default config;
