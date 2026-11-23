# Cloudflare

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_CLOUDFLARE`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Cloudflare API v4](https://developers.cloudflare.com/api/) at `https://api.cloudflare.com/client/v4`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `api_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `dns_list` | GET | `/zones/{zoneId}/dns_records` | Official HTTP |
| `dns_create` | POST | `/zones/{zoneId}/dns_records` | `type`, `name` required; `content`, `ttl` (default 1 = automatic), `proxied` |
| `dns_update` | PUT | `/zones/{zoneId}/dns_records/{id}` | Full overwrite: `type`, `name` required; `content`, `ttl` (default 1), `proxied` |
| `dns_delete` | DELETE | `/zones/{zoneId}/dns_records/{id}` | Official HTTP |
| `zone_get` | GET | `/zones/{zoneId}` | Official HTTP |
| `cache_purge` | POST | `/zones/{zoneId}/purge_cache` | One of `purge_everything`, `files`, `tags`, `hosts`, `prefixes` |
| `worker_list` | GET | `/accounts/{accountId}/workers/scripts` | Official HTTP |
| `r2_get` | GET | `/accounts/{accountId}/r2/buckets/{bucket}/objects/{key}` | Official HTTP |
| `cert_get` | GET | `/zones/{zoneId}/ssl/certificate_packs` | Official HTTP |
| `cert_upload` | POST | `/zones/{zoneId}/custom_certificates` | PEM text `certificate` + `private_key` |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `api_token` | password | yes | yes | Cloudflare API token (preferred). Do not use the Global API Key. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Non-2xx raise a BPMN error.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.api_token`. Never logged, never returned.
- [Cloudflare API v4 docs](https://developers.cloudflare.com/api/). Terms: https://www.cloudflare.com/website-terms/.
- Cloudflare Terms. API tokens only. No dashboard scrape. Certificates and keys are PEM text.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
