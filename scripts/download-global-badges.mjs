import fs from "node:fs/promises";
import path from "node:path";
import sharp from "sharp";

const BASE = "https://football-logos.cc";
const ROOT = path.resolve("native-android/assets/football/badges");
const RESERVED = new Set(["all", "national-teams", "tournaments", "collections", "map", "new", "request", "submit", "dls", "kit-brands"]);
const PAGE_CONCURRENCY = Number(process.env.BADGE_PAGE_CONCURRENCY || 8);
const IMAGE_CONCURRENCY = Number(process.env.BADGE_IMAGE_CONCURRENCY || 12);
const MIN_BADGES = Number(process.env.MIN_BADGES || 4000);
const MAX_BADGES = Number(process.env.MAX_BADGES || 0);

function decodeHtml(value) {
  return value.replace(/&amp;/g, "&").replace(/&#39;|&#x27;/gi, "'")
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&nbsp;/g, " ")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
}
function plainText(value) {
  return decodeHtml(value.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim());
}
function normalize(value) {
  return value.normalize("NFD").replace(/\p{Diacritic}/gu, "")
    .toLowerCase().replace(/[^a-z0-9]+/g, "");
}
async function request(url, binary = false, attempt = 0) {
  const response = await fetch(url, {
    headers: {
      "User-Agent": "Zekify/3.8.0 (free, ad-free, non-commercial football fan project; badge source attribution included)",
      "Accept": binary ? "image/png,image/*;q=0.9,*/*;q=0.5" : "text/html,application/xhtml+xml"
    },
    signal: AbortSignal.timeout(binary ? 20000 : 30000)
  });
  if ((response.status === 429 || response.status >= 500) && attempt < 4) {
    const retryAfter = Number(response.headers.get("retry-after") || 0);
    const delay = retryAfter > 0 ? Math.min(retryAfter * 1000, 15000) : 700 * (2 ** attempt);
    await new Promise(resolve => setTimeout(resolve, delay));
    return request(url, binary, attempt + 1);
  }
  if (!response.ok) throw new Error("HTTP " + response.status + " for " + url);
  return binary ? Buffer.from(await response.arrayBuffer()) : response.text();
}
async function mapLimit(items, limit, worker) {
  const output = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (true) {
      const index = cursor++;
      if (index >= items.length) return;
      try { output[index] = await worker(items[index], index); }
      catch (error) { output[index] = { error: String(error) }; }
    }
  });
  await Promise.all(workers);
  return output;
}
function discoverCountryPages(html) {
  const slugs = new Set();
  for (const match of html.matchAll(/href\s*=\s*["']\/([a-z0-9-]+)\/["']/gi)) {
    const slug = match[1].toLowerCase();
    if (!RESERVED.has(slug)) slugs.add(slug);
  }
  if (slugs.size < 100) throw new Error("Country discovery returned only " + slugs.size + " pages");
  return [...slugs].map(slug => BASE + "/" + slug + "/");
}
function imageUrls(html) {
  const found = [];
  const pattern = /https:\/\/assets\.football-logos\.cc\/logos\/[a-z0-9-]+\/256x256\/[a-z0-9._-]+\.png/gi;
  for (const match of html.matchAll(pattern)) found.push(match[0]);
  return [...new Set(found)];
}
function slugFromImage(url) {
  const file = new URL(url).pathname.split("/").pop() || "";
  return file.replace(/\.[a-f0-9]{8}\.png$/i, "").replace(/\.png$/i, "");
}
function countryFromImage(url, fallback) {
  const match = new URL(url).pathname.match(/\/logos\/([^/]+)\//);
  return match?.[1] || fallback;
}
function isCompetitionHeading(value) {
  return /(league|cup|tournament|division|premier|serie|liga|championship|shield|superliga|competition|federation|national league)/i.test(value);
}
function parseSection(section, pageUrl, records) {
  const headings = [...section.matchAll(/<h3\b[^>]*>([\s\S]*?)<\/h3>/gi)].map(m => plainText(m[1]));
  const urls = imageUrls(section);
  if (!urls.length) return;
  const h2 = plainText(section.match(/<h2\b[^>]*>([\s\S]*?)<\/h2>/i)?.[1] || "");
  const pageType = new URL(pageUrl).pathname.split("/").filter(Boolean)[0] || "";
  const tournamentPage = pageType === "tournaments";
  const nationalPage = pageType === "national-teams";
  for (let i = 0; i < urls.length; i++) {
    const url = urls[i];
    const slug = slugFromImage(url);
    const name = headings[i] || slug.replace(/[-_]+/g, " ");
    const country = countryFromImage(url, pageType);
    const category = tournamentPage || (!nationalPage && i === 0 && isCompetitionHeading(h2))
      ? "competitions" : "teams";
    records.push({ category, name, slug, country, sourceUrl: url });
  }
}
function parsePage(html, pageUrl) {
  const records = [];
  const sections = [...html.matchAll(/<section\b[^>]*>[\s\S]*?<\/section>/gi)].map(m => m[0]);
  if (sections.length) {
    for (const section of sections) parseSection(section, pageUrl, records);
  } else {
    parseSection(html, pageUrl, records);
  }
  return records;
}
function addIndexEntry(target, key, assetPath) {
  const clean = normalize(key);
  if (clean && !target[clean]) target[clean] = assetPath;
}
function makeSelfTest() {
  const html = '<section><h2>English Premier League</h2><h3>Premier League</h3><h3>Manchester United</h3><img src="https://assets.football-logos.cc/logos/england/256x256/english-premier-league.fd484e52.png"><img src="https://assets.football-logos.cc/logos/england/256x256/manchester-united.7ab9d343.png"></section>';
  const records = parsePage(html, BASE + "/england/");
  if (records.length !== 2 || records[0].category !== "competitions" ||
      records[1].category !== "teams" || normalize(records[1].name) !== "manchesterunited") {
    throw new Error("Badge parser self-test failed: " + JSON.stringify(records));
  }
  if (normalize("Côte d'Ivoire") !== "cotedivoire") throw new Error("Unicode normalization self-test failed");
  console.log("Global badge parser self-test passed.");
}
if (process.argv.includes("--self-test")) {
  makeSelfTest();
  process.exit(0);
}

await fs.mkdir(ROOT, { recursive: true });
console.log("Discovering football logo countries...");
const allHtml = await request(BASE + "/all/");
const countryPages = discoverCountryPages(allHtml);
const pages = [...countryPages, BASE + "/national-teams/", BASE + "/tournaments/"];
console.log("Discovered " + countryPages.length + " country pages plus national teams and tournaments.");

const pageResults = await mapLimit(pages, PAGE_CONCURRENCY, async pageUrl => {
  const html = await request(pageUrl);
  return { pageUrl, records: parsePage(html, pageUrl) };
});
const allRecords = [];
let failedPages = 0;
for (const result of pageResults) {
  if (!result || result.error) { failedPages++; continue; }
  allRecords.push(...result.records);
}
const unique = new Map();
for (const record of allRecords) {
  if (record.slug && record.sourceUrl && !unique.has(record.sourceUrl)) unique.set(record.sourceUrl, record);
}
let records = [...unique.values()];
if (MAX_BADGES > 0) records = records.slice(0, MAX_BADGES);
console.log("Found " + records.length + " unique badge assets; failed pages: " + failedPages);
if (records.length < MIN_BADGES) {
  throw new Error("Global badge coverage too small: found " + records.length + ", expected at least " + MIN_BADGES);
}

const index = {
  schema_version: 2,
  generated_at: new Date().toISOString(),
  source: "https://football-logos.cc/",
  source_usage: "Non-commercial informational/editorial/fan-project use only. Club, league and association marks remain owned by their respective rights holders.",
  teams: {},
  competitions: {}
};
const downloads = records.map(record => {
  const category = record.category === "competitions" ? "competitions" : "teams";
  const country = normalize(record.country || "global") || "global";
  const slug = normalize(record.slug) || "badge";
  const fileName = (category === "teams" ? "team-" : "competition-") + country + "-" + slug + ".webp";
  const assetPath = "football/badges/" + fileName;
  const target = category === "teams" ? index.teams : index.competitions;
  addIndexEntry(target, record.name, assetPath);
  addIndexEntry(target, record.slug, assetPath);
  if (record.country && record.country !== "tournaments" && record.country !== "national-teams") {
    addIndexEntry(target, record.country + "::" + record.name, assetPath);
    addIndexEntry(target, record.country + "::" + record.slug, assetPath);
  }
  return { ...record, assetPath, fileName };
});

const existing = await fs.readdir(ROOT);
for (const file of existing) if (file.endsWith(".webp")) await fs.unlink(path.join(ROOT, file));

const downloaded = await mapLimit(downloads, IMAGE_CONCURRENCY, async item => {
  const pngUrl = item.sourceUrl.replace("/256x256/", "/128x128/");
  const png = await request(pngUrl, true);
  if (png.length < 100 || png[0] !== 0x89 || png[1] !== 0x50 || png[2] !== 0x4e || png[3] !== 0x47) {
    throw new Error("Invalid PNG: " + pngUrl);
  }
  const webp = await sharp(png).resize(128, 128, {
    fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 }
  }).webp({ quality: 82, alphaQuality: 90, effort: 4 }).toBuffer();
  await fs.writeFile(path.join(ROOT, item.fileName), webp);
  return item;
});

const good = downloaded.filter(Boolean);
const failedImages = downloaded.length - good.length;
const validFiles = new Set(await fs.readdir(ROOT));
for (const collection of [index.teams, index.competitions]) {
  for (const [key, assetPath] of Object.entries(collection)) {
    if (!validFiles.has(path.basename(assetPath))) delete collection[key];
  }
}
index.counts = {
  bundled_team_badges: new Set(Object.values(index.teams)).size,
  bundled_competition_badges: new Set(Object.values(index.competitions)).size,
  downloaded_assets: good.length,
  failed_assets: failedImages,
  failed_country_pages: failedPages
};
if (good.length < MIN_BADGES) throw new Error("Only " + good.length + " badge files downloaded; minimum is " + MIN_BADGES);

await fs.writeFile(path.join(ROOT, "index.json"), JSON.stringify(index));
const attribution = [
  "Football badge artwork source: https://football-logos.cc/",
  "Please credit and link to football-logos.cc when using its logo collection.",
  "",
  "The source describes use as informational, editorial and fan-based, including non-commercial fan projects.",
  "Zekify is distributed free and without advertising. This bundled collection must not be used for commercial products, merchandise, branding, promotion, or to imply affiliation or endorsement.",
  "",
  "All club, league, federation and association names and marks remain the intellectual property of their respective owners.",
  "football-logos.cc is an independent source and is not affiliated with those rights holders.",
  "Logo usage terms: https://football-logos.cc/license/"
].join("\n");
await fs.writeFile(path.join(ROOT, "FOOTBALL_LOGOS_ATTRIBUTION.txt"), attribution);
console.log("Bundled " + good.length + " optimized WebP logos.");
console.log("Teams: " + index.counts.bundled_team_badges + "; competitions: " + index.counts.bundled_competition_badges);
