# Instapage

Instapage **form-submit webhooks**. Handler: `OfficialApiNode` via `api_spec.json`. This package is **ingress-only**: the sole operation is `trigger.listen` (`kind: ingress`). OfficialApiNode does not open a socket — paste the StudioQuack BPMN message-event URL in Instapage (Settings → Integrations / page webhook).

- **Webhook help:** https://help.instapage.com/hc/en-us/articles/360001722428-Integrating-with-Webhooks
- **Public API (not called by this node):** https://devdocs.instapage.com/ · https://api.instapage.com · changelog newest entry 2026-06-10
- **Auth:** none on this node (webhook optional bearer is configured in Instapage toward the StudioQuack ingress key, not here)

A Public API with Bearer personal token exists (pages, submissions). This OfficialApiNode ships **webhook-only** so operators are not pointed at builder scrape. Do **not** scrape the page builder, published HTML, or heatmaps. Page JSON import/export is out of catalog scope.

Instapage rejects localhost webhook URLs. Their handler SLA is **20 seconds**.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `trigger.listen` | n/a | **ingress** — form-submit POST (`ip`, `page_id`, `page_name`, `page_url`, mapped fields) correlates onto a BPMN message event |

## Config

None — the node is ingress-only and makes no outbound call, so it has no credentials or timeout.

## Ports

`input` / `output` only. Selecting `trigger.listen` returns the standard OfficialApiNode ingress message (use a message start / catch / boundary event).

## Legal

- Public API help: https://help.instapage.com/hc/en-us/articles/27634951739287-Public-API
- Lead `fields` are personal data. Customer owns the Instapage account.
- Official webhook/API only. Do not crawl builder or published landing HTML.
