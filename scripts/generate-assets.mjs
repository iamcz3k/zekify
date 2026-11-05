import sharp from "sharp";
import { mkdir, readFile } from "node:fs/promises";
await mkdir("assets",{recursive:true});
const icon=await readFile("assets/zekify-ball.svg");
const splash=await readFile("assets/zekify-splash.svg");
await sharp(icon).resize(1024,1024).png().toFile("assets/icon.png");
await sharp(splash).resize(2732,2732).png().toFile("assets/splash.png");
console.log("Generated matte-black football app icon and splash artwork.");
