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
const competitionPattern = /(league|liga|ligue|division|serie[a-z0-9]*|cup|copa|champions|championship|premier|bundesliga|eredivisie|superlig|tournament|confederation|afcon|uefa|fifa|concacaf|conmebol|caf|ofc|vleague|jleague|kleague|allsvenskan|eliteserien|premiership|supercup|proleague|nationalcup|aleague|nwsl|majorleaguesoccer|^mls$)/i;
const teams = {};
const competitions = {};
const teamOwners = new Map();
const competitionOwners = new Map();
const ambiguousTeams = new Set();
const ambiguousCompetitions = new Set();
const files = [];

async function walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await walk(full);
    else if (entry.isFile() && entry.name.toLowerCase().endsWith(".svg")) files.push(full);
  }
}

function addIdentity(index, owners, ambiguous, countryKey, nameKey, entry) {
  index[countryKey + "::" + nameKey] = entry;
  if (ambiguous.has(nameKey)) return;
  const owner = owners.get(nameKey);
  if (!owner) {
    owners.set(nameKey, countryKey);
    index[nameKey] = entry;
  } else if (owner !== countryKey) {
    delete index[nameKey];
    ambiguous.add(nameKey);
  }
}

await walk(sourceRoot);
let copied = 0;
let teamCount = 0;
let competitionCount = 0;
const copiedFiles = new Set();
const teamAssets = new Set();
const competitionAssets = new Set();

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
  if (!content.toLowerCase().includes("<svg")) continue;
  if (/<script\b|<foreignObject\b|<!DOCTYPE|<!ENTITY|\son[a-z]+\s*=|(?:href|xlink:href)\s*=\s*["'](?:https?:|\/\/|data:|javascript:)/i.test(content)) continue;

  if (!copiedFiles.has(filename)) {
    await writeFile(destination, content);
    copiedFiles.add(filename);
    copied++;
  }

  const assetPath = "football/badges/" + filename;
  const entry = { path: assetPath, name, country, source: "JoseArroyave/football-logos" };
  const isCompetition = countryKey === "international" || competitionPattern.test(nameKey);

  if (isCompetition) {
    addIdentity(competitions, competitionOwners, ambiguousCompetitions, countryKey, nameKey, entry);
    if (!competitionAssets.has(filename)) { competitionAssets.add(filename); competitionCount++; }
  } else {
    addIdentity(teams, teamOwners, ambiguousTeams, countryKey, nameKey, entry);
    const aliasName = name.replace(/\b(association football club|football club|athletic club|sporting club|fc|afc|cf|sc|ac|fk|sv|bk|cd|ud|rcd)\b/gi, " ");
    const aliasKey = safeSlug(aliasName);
    if (aliasKey && aliasKey !== nameKey) addIdentity(teams, teamOwners, ambiguousTeams, countryKey, aliasKey, entry);
    if (!teamAssets.has(filename)) { teamAssets.add(filename); teamCount++; }
  }
}

if (teamCount < 3000) throw new Error("Expected at least 3,000 bundled team badges, found " + teamCount);
if (competitionCount < 25) throw new Error("Expected at least 25 bundled competition badges, found " + competitionCount);

const index = {
  schema_version: 2,
  updated_at: new Date().toISOString(),
  source: {
    repository: "https://github.com/JoseArroyave/football-logos",
    license_claim: "Personal and commercial use stated in upstream README; club and competition marks remain trademarks of their owners."
  },
  bundled_badge_assets: copied,
  bundled_team_badges: teamCount,
  bundled_competition_badges: competitionCount,
  teams,
  competitions
};

await writeFile(indexPath, JSON.stringify(index, null, 2) + "\n");
console.log("Prepared " + teamCount + " team badges and " + competitionCount + " competition badges (" + copied + " unique SVG assets).");
