import { registerPlugin } from "@capacitor/core";

export interface ZekifyDisplayPlugin {
  setImmersive(options: { enabled: boolean }): Promise<{ enabled: boolean }>;
  setBrightness(options: { level: number }): Promise<{ level: number }>;
  restore(): Promise<void>;
}

export const ZekifyDisplay = registerPlugin<ZekifyDisplayPlugin>("ZekifyDisplay", {
  web: async () => ({
    async setImmersive(options: { enabled: boolean }) { return { enabled: options.enabled }; },
    async setBrightness(options: { level: number }) { return { level: options.level }; },
    async restore() {},
  }),
});
