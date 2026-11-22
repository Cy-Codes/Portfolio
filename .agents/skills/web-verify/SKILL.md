---
name: web-verify
description: >-
  Verifies webApp (wasmJs), adminWebApp, /read/{slug} HTML Reader View, and SEO
  responses in a live Chrome browser via the Chrome DevTools plugin. Use when testing
  Wasm web UI changes, adaptive breakpoints, URL routing, or HTML SEO output.
---
# Live Web & Wasm Verification (Chrome DevTools)

Use the **Chrome DevTools** MCP tools (follow the `chrome-devtools` skill) to verify live rendering, browser history, SEO metadata, and adaptive breakpoints.

## Prerequisites

Ensure the public (and optionally admin) server is serving locally:
- Start local stack if not running: `./scripts/up.sh local --seed`
- Public site + Wasm shell: `http://127.0.0.1:8080`
- Health readiness check: `http://127.0.0.1:8080/health/ready` must return `200` (`"READY"`).

## Verification Checklist

1. **Console & Network Cleanliness:**
   - Navigate to `http://127.0.0.1:8080/` and confirm `#portfolio-root` mounts with zero uncaught JS/Wasm console errors and zero unexpected `4xx`/`5xx` API responses.
2. **Adaptive Breakpoint Sweep (M3 Size Classes):**
   - Resize the browser viewport across all 5 tiers and confirm navigation chrome adapts cleanly with no clipped text or overflow:
     - `Compact` (`390x844`): bottom bar
     - `Medium` (`700x900`): navigation rail
     - `Expanded` (`1024x900`): navigation rail + dual panes
     - `Large` (`1366x900`): persistent drawer
     - `Extra-Large` (`1720x1000`): persistent drawer + capped reading measure
3. **`WebHistoryController` & Deep Links:**
   - Navigate between Profile, Projects (`/projects/<slug>`), WriteUps (`/writeups/<slug>`), and Resume/Docs (`/resume`), then test browser **Back** and **Forward** buttons to verify URL and screen synchronization.
   - Directly open a deep link such as `http://127.0.0.1:8080/projects` or `/writeups/<slug>` and confirm it renders the matching destination.
4. **HTML Reader View & SEO (`/read/{slug}`, `/sitemap.xml`, `/robots.txt`):**
   - Open `/read/<published-slug>` and inspect `<head>` for `<title>`, `<meta name="description">`, `rel="canonical"`, `og:*`, and `<script type="application/ld+json">` (`TechArticle`).
   - Confirm fenced `demo:<id>` blocks render the progressive-enhancement `.demo-callout` link to `/writeups/<slug>`, and raw HTML/script tags are escaped.
5. **Admin Web App (if changed):**
   - Open `adminWebApp`, verify sign-in flow against `http://127.0.0.1:8081` (`./scripts/dev-token.sh owner`), live Markdown + `DemoRegistry` preview, and zero console errors.
