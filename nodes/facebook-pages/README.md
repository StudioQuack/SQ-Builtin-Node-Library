# Facebook Pages

Official Meta Graph API **v26.0** for a Facebook Page. Handler: `OfficialApiNode` via `api_spec.json`. Page feed, insights, Lead Ads forms, Page-scoped Graph GET/POST/DELETE. Instagram remains the Instagram node. **Researched:** 2026-08-20.

- **Docs:** https://developers.facebook.com/docs/graph-api/reference/page/feed/ — changelog https://developers.facebook.com/docs/graph-api/changelog/version26.0/
- **Base:** `https://graph.facebook.com/v26.0`
- **Auth secret:** `page_access_token` → `Authorization: Bearer ` (prefix includes trailing space)

## Operations

| operation | HTTP | Path |
|---|---|---|
| `graph_get` | GET | `/{path}` |
| `page_post_create` | POST | `/{page_id}/feed` |
| `page_feed_get` | GET | `/{page_id}/feed` |
| `insights_get` | GET | `/{page_id}/insights` |
| `lead_list` | GET | `/{page_id}/leadgen_forms` |
| `graph_post` | POST | `/{path}` |
| `graph_delete` | DELETE | `/{object_id}` |
| `trigger` | ingress | BPMN message start / catch — this node does not open a socket |

`page_post_create` body: `message`, `link`. `page_feed_get` query: `limit`. `insights_get` query: `metric`, `period`. `{path}` is URL-encoded — use a single object id, or the `page_*` verbs for `/feed` edges.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`page_post_create`) |
| `page_access_token` | password | yes | Page token |
| `timeout_seconds` | number | no | Default 25 |

Ports: `input` / `output` only. Success `{ status, http_status, data }`. Failure `{ error }` — never includes `page_access_token`. Lead fields in `data` are PII — do not log.

## Legal

- Platform Terms: https://developers.facebook.com/terms
- Commercial Terms: https://www.facebook.com/legal/commercial_terms
- Graph: https://developers.facebook.com/docs/graph-api/
- Insights: https://developers.facebook.com/docs/graph-api/reference/insights/
- Leadgen forms: https://developers.facebook.com/docs/graph-api/reference/page/leadgen_forms/
- Page webhooks: https://developers.facebook.com/docs/graph-api/webhooks/reference/page/

Operator needs a Meta app, Page token with the right tasks, and App Review for live. Lawful basis required for lead PII (GDPR). Honor Platform / Page usage headers; do not tight-loop on 429. Do not call Commerce Order Management (removed on v26.0+). **Official API only — do not scrape facebook.com or use session cookies.** Customer owns the Meta app and Page token. `trigger` is ingress, not `subscribed_apps`.
