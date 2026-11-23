# New Relic

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_NEW_RELIC`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [New Relic NerdGraph](https://docs.newrelic.com/docs/apis/nerdgraph/get-started/introduction-new-relic-nerdgraph/) at `https://api.newrelic.com`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `user_api_key` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `nrql_query` | POST | `/graphql` | Official HTTP |
| `event_ingest` | POST | `/v1/accounts/{accountId}/events` | Whole-body JSON array (`events`); each object requires `eventType`. Host `insights-collector.newrelic.com`. https://docs.newrelic.com/docs/data-apis/ingest-apis/event-api/introduction-event-api/ |
| `deploy_marker` | POST | `/v2/applications/{app_id}/deployments.json` | REST v2 EOL 2027-07-31 |
| `alert_policy_get` | GET | `/v2/alerts_policies.json` | /alerts_policies/* not in REST v2 EOL |
| `incident_get` | GET | `/v2/alerts_incidents/{incident_id}.json` | REST v2 EOL 2027-07-31 |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `user_api_key` | password | yes | yes | New Relic user API key (NerdGraph, REST v2). Header API-Key. Never logged. |
| `license_key` | password | no | yes | Ingest license key for `event_ingest` (Event API rejects user keys). Overrides the API-Key header on that call only. Never logged. |
| `accountId` | string | yes | no | Account id for `event_ingest` path and NerdGraph actor id. |
| `data_region` | select (`us` / `eu`) | no | no | Must match the key’s account region (401/403 “incorrect region” when wrong). |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.user_api_key`. Never logged, never returned.
- [New Relic NerdGraph docs](https://docs.newrelic.com/docs/apis/nerdgraph/get-started/introduction-new-relic-nerdgraph/). Terms: https://newrelic.com/termsandconditions/terms.
- Set **data_region** to `eu` for EU accounts (`api.eu.newrelic.com`, Event API `insights-collector.eu01.nr-data.net`). Default `us`.
- `nrql_query` expects a **NerdGraph GraphQL** document on INPUT `query`, not a raw NRQL string alone.
- `event_ingest`: send `events` as a JSON array on INPUT (or `events_json` text / single object — coerced). `accountId` on INPUT or config.
- **Agent tools:** expose `incident_id`, `events` or `events_json`, and `query` on the tool schema (not only `operation`); aliases `incidentId`, `events_json`.
- `nrql_query` requires non-empty INPUT `query` (NerdGraph GraphQL document). `user_api_key` in config only (header `API-Key`); 401 → check key + `data_region` us/eu.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
