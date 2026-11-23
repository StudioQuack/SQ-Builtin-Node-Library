# Braze

Official Braze REST API (cluster-specific REST endpoint). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://www.braze.com/docs/api/basics · https://www.braze.com/docs/api/endpoints/user_data/post_user_track
- **Base:** `https://{rest_endpoint}` (config `rest_endpoint`, default US-01 `rest.iad-01.braze.com`)
- **Auth:** Bearer `api_key` (REST API key). Customer owns the account.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `user.track` | POST | `/users/track` |
| `user.delete` | POST | `/users/delete` |
| `user.export` | POST | `/users/export/ids` |
| `campaign.trigger` | POST | `/campaigns/trigger/send` |
| `canvas.trigger` | POST | `/canvas/trigger/send` |
| `subscription.set` | POST | `/subscription/status/set` |
| `catalog.upsert` | PUT | `/catalogs/{catalog_name}/items` |
| `message.send` | POST | `/messages/send` |
| `trigger.listen` | ingress | BPMN message event (no socket) |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| auth secret | password | yes | Provider credential |
| `rest_endpoint` | string | no | REST host of your Braze instance (default `rest.iad-01.braze.com`) |
| `timeout_seconds` | number | no | Default 25 |

**Clusters:** set config `rest_endpoint` to your instance's REST host (host only) — e.g. `rest.iad-01.braze.com` (US-01, default) … `rest.iad-08.braze.com`, `rest.fra-01.braze.eu` / `rest.fra-02.braze.eu` (EU), `rest.au-01.braze.com` (AU). Copy it from the Braze dashboard → Settings → APIs and Identifiers. `user.delete` permanently removes profiles. `catalog.upsert` is async PUT (replace items). `trigger.listen` is ingress (Currents / webhooks correlate onto a BPMN message event).

## Ports

`input` / `output` only. Failures raise a BPMN error — never includes secrets.

## Legal

- Minimize fields sent and retained. Customer owns the vendor account and is the controller of any personal data. Never log secrets, tokens, or raw PII values.
- https://www.braze.com/legal — GDPR deletion via users.delete; caller holds messaging consent (CAN-SPAM/CASL).
- Official API only. Do not scrape the vendor dashboard.
