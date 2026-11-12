const LIST_NAME = "Zekify Updates & Announcements";
const WINDOW_MS = 15 * 60 * 1000;
const MAX_REQUESTS = 8;
const hits = new Map();

function reply(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Cache-Control": "no-store",
    },
  });
}

function limited(request) {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const now = Date.now();
  const previous = hits.get(ip) || [];
  const recent = previous.filter((time) => now - time < WINDOW_MS);
  if (recent.length >= MAX_REQUESTS) return true;
  recent.push(now);
  hits.set(ip, recent);
  if (hits.size > 2000) {
    for (const [key, values] of hits) {
      if (!values.length || now - values[values.length - 1] >= WINDOW_MS) hits.delete(key);
    }
  }
  return false;
}

async function brevo(env, path, method = "GET", body) {
  if (!env.BREVO_API_KEY) throw new Error("UPDATES_NOT_CONFIGURED");
  const response = await fetch("https://api.brevo.com/v3" + path, {
    method,
    headers: {
      "api-key": env.BREVO_API_KEY,
      "Accept": "application/json",
      "Content-Type": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const raw = await response.text();
  let data = {};
  try { data = raw ? JSON.parse(raw) : {}; } catch { data = {}; }
  if (!response.ok) {
    const error = new Error(data.message || data.code || "Brevo request failed");
    error.status = response.status;
    throw error;
  }
  return data;
}

async function getListId(env) {
  const result = await brevo(env, "/contacts/lists?limit=50&offset=0");
  const lists = Array.isArray(result.lists) ? result.lists : [];
  const existing = lists.find((list) => list.name === LIST_NAME);
  if (existing) return existing.id;
  const created = await brevo(env, "/contacts/lists", "POST", {
    folderId: Number(env.BREVO_FOLDER_ID || 1),
    name: LIST_NAME,
  });
  if (!created.id) throw new Error("Could not create the Zekify subscriber list.");
  return created.id;
}

function validEmail(value) {
  return typeof value === "string" && value.length <= 254 &&
    /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

async function contactStatus(env, email, listId) {
  try {
    const contact = await brevo(env, "/contacts/" + encodeURIComponent(email));
    const ids = Array.isArray(contact.listIds) ? contact.listIds.map(Number) : [];
    return ids.includes(Number(listId));
  } catch (error) {
    if (error.status === 404) return false;
    throw error;
  }
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return reply({ success: true });
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET") {
      return reply({ success: true, configured: Boolean(env.BREVO_API_KEY) });
    }
    if (limited(request)) return reply({ success: false, message: "Too many requests. Please wait and try again." }, 429);
    if (!["GET", "POST"].includes(request.method)) return reply({ success: false, message: "Method not allowed." }, 405);

    try {
      if (url.pathname === "/status" && request.method === "GET") {
        const email = (url.searchParams.get("email") || "").trim().toLowerCase();
        if (!validEmail(email)) return reply({ success: false, message: "Enter a valid email address." }, 400);
        const listId = await getListId(env);
        const subscribed = await contactStatus(env, email, listId);
        return reply({ success: true, subscribed });
      }

      if (url.pathname === "/subscribe" && request.method === "POST") {
        const data = await request.json();
        if (data.website) return reply({ success: true, message: "Subscription saved." });
        const email = String(data.email || "").trim().toLowerCase();
        const oldEmail = String(data.oldEmail || "").trim().toLowerCase();
        if (!validEmail(email)) return reply({ success: false, message: "Enter a valid email address." }, 400);
        if (data.consent !== true) return reply({ success: false, message: "Please confirm that you want to receive Zekify updates." }, 400);
        const listId = await getListId(env);
        await brevo(env, "/contacts", "POST", {
          email,
          listIds: [Number(listId)],
          emailBlacklisted: false,
          updateEnabled: true,
        });
        if (validEmail(oldEmail) && oldEmail !== email) {
          await brevo(env, "/contacts/" + encodeURIComponent(oldEmail), "PUT", {
            unlinkListIds: [Number(listId)],
          }).catch(() => null);
        }
        return reply({ success: true, message: "You're subscribed to Zekify updates." });
      }

      if (url.pathname === "/unsubscribe" && request.method === "POST") {
        const data = await request.json();
        const email = String(data.email || "").trim().toLowerCase();
        if (!validEmail(email)) return reply({ success: false, message: "Enter a valid email address." }, 400);
        const listId = await getListId(env);
        await brevo(env, "/contacts/" + encodeURIComponent(email), "PUT", {
          unlinkListIds: [Number(listId)],
        }).catch((error) => {
          if (error.status !== 404) throw error;
        });
        return reply({ success: true, message: "You have been unsubscribed from Zekify updates." });
      }

      return reply({ success: false, message: "Not found." }, 404);
    } catch (error) {
      if (error.message === "UPDATES_NOT_CONFIGURED") {
        return reply({ success: false, message: "Zekify email updates are being configured. Try again later." }, 503);
      }
      const status = error.status === 400 ? 400 : error.status === 429 ? 429 : 502;
      return reply({ success: false, message: status === 400 ? "Brevo could not process that email address." : "The email service is temporarily unavailable. Try again shortly." }, status);
    }
  },
};
