import { registerPlugin } from "@capacitor/core";

export interface ZekifyDisplayPlugin {
  setImmersive(options: { enabled: boolean }): Promise<{ enabled: boolean }>;
  setBrightness(options: { level: number }): Promise<{ level: number }>;
  restore(): Promise<void>;
}

export const ZekifyDisplay = registerPlugin<ZekifyDisplayPlugin>("ZekifyDisplay", {
  web: () => ({
    async setImmersive({ enabled }) { return { enabled }; },
    async setBrightness({ level }) { return { level }; },
    async restore() {},
  }),
});
