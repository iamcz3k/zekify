# Zekify Updates API

Cloudflare Worker used by the Android app's one-time updates popup and Profile > Updates & Announcements screen.

## Deployment

Worker name: `zekify-updates-api`

Public endpoint: `https://zekify-updates-api.laczekstream.workers.dev`

The Worker is deployed separately from the LACZEK STREAM football API. It does not modify or depend on that service.

## Required secret

The Worker intentionally does not contain a Brevo API key.

1. In Brevo, open **Settings → SMTP & API → API keys** and create an API key for this integration.
2. In Cloudflare, open **Workers & Pages → zekify-updates-api → Settings → Variables and Secrets**.
3. Add a **Secret** named `BREVO_API_KEY` and paste the key directly into Cloudflare. Do not put it in the Android app, GitHub source, or chat.
4. Save and redeploy if Cloudflare asks.

The Worker creates a dedicated Brevo contact list named **Zekify Updates & Announcements** in folder ID `1` the first time it is used. No existing list is reused or modified.

## Endpoints

- `GET /health` — returns whether the Brevo secret is configured.
- `GET /status?email=...` — checks membership in the Zekify list.
- `POST /subscribe` — creates or updates a Brevo contact and adds it to the Zekify list. Requires `email` and `consent: true`. Optional `oldEmail` is removed from the list after the new address is added.
- `POST /unsubscribe` — removes the address from the Zekify list without deleting the Brevo contact or changing its other lists.

The API validates email addresses, applies a basic per-IP request limit, and never returns the Brevo API key.

## App configuration

The Capacitor app uses this default endpoint. It can be overridden at build time with `VITE_UPDATES_API_BASE`.
