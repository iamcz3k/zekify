import { execFileSync } from "node:child_process";
import { mkdir, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import path from "node:path";

const sourceUrl = "https://github.com/JoseArroyave/football-logos.git";
const checkout = "/tmp/zekify-football-logos";
const sourceRoot = path.join(checkout, "logos");
const outputRoot = "native-android/assets/football/badges";
const indexPath = path.join(outputRoot, "index.json");

await rm(checkout, { recursive: true, force: true });
execFileSync("git", ["clone", "--depth=1", "--filter=blob:none", "--sparse", sourceUrl, checkout], { stdio: "inherit" });
execFileSync("git", ["-C", checkout, "sparse-checkout", "set", "logos"], { stdio: "inherit" });
await mkdir(outputRoot, { recursive: true });

const normalize = value => value.toLocaleLowerCase("en").normalize("NFC").replace(/[^\p{L}\p{N}]/gu, "");
const safeSlug = value => normalize(value) || "unknown";
const teams = {};
const globalNameOwners = new Map();
const ambiguousGlobalNames = new Set();
const files = [];
async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full);
    else if (entry.isFile() && entry.name.toLowerCase().endsWith(".svg")) files.push(full);
  }
}
await walk(sourceRoot);
let copied = 0;
const copiedFiles = new Set();
for (const file of files) {
  const relative = path.relative(sourceRoot, file);
  const parts = relative.split(path.sep);
  if (parts.length < 2) continue;
  const country = parts[0];
  const name = path.basename(file, path.extname(file));
  const countryKey = safeSlug(country);
  const nameKey = safeSlug(name);
  const info = await stat(file);
  if (info.size === 0 || info.size > 256 * 1024) continue;
  const filename = countryKey + "--" + nameKey + ".svg";
  const destination = path.join(outputRoot, filename);
  const content = await readFile(file, "utf8");
  if (!content.includes("<svg")) continue;
  if (!copiedFiles.has(filename)) {
    await writeFile(destination, content);
    copiedFiles.add(filename);
    copied++;
  }
  const assetPath = "football/badges/" + filename;
  const entry = { path: assetPath, name, country, source: "JoseArroyave/football-logos" };
  teams[countryKey + "::" + nameKey] = entry;
  if (!ambiguousGlobalNames.has(nameKey)) {
    const owner = globalNameOwners.get(nameKey);
    if (!owner) { globalNameOwners.set(nameKey, countryKey); teams[nameKey] = entry; }
    else if (owner !== countryKey) { delete teams[nameKey]; ambiguousGlobalNames.add(nameKey); }
  }
  const aliasName = name.replace(/\\b(association football club|football club|athletic club|sporting club|fc|afc|cf|sc|ac|fk|sv|bk|cd|ud|rcd)\\b/gi, " ");
  const aliasKey = safeSlug(aliasName);
  if (aliasKey && aliasKey !== nameKey) {
    teams[countryKey + "::" + aliasKey] = entry;
    if (!ambiguousGlobalNames.has(aliasKey)) {
      const owner = globalNameOwners.get(aliasKey);
      if (!owner) { globalNameOwners.set(aliasKey, countryKey); teams[aliasKey] = entry; }
      else if (owner !== countryKey) { delete teams[aliasKey]; ambiguousGlobalNames.add(aliasKey); }
    }
  }
}
if (copied < 3000) throw new Error("Expected at least 3,000 bundled team badges, found " + copied);
const index = {
  schema_version: 1,
  updated_at: new Date().toISOString(),
  source: { repository: "https://github.com/JoseArroyave/football-logos", license_claim: "Personal and commercial use stated in upstream README; club marks remain trademarks of their owners." },
  bundled_team_badges: copied,
  teams,
  competitions: {}
};
await writeFile(indexPath, JSON.stringify(index, null, 2) + "\n");
console.log("Prepared " + copied + " country-qualified team badges.");
