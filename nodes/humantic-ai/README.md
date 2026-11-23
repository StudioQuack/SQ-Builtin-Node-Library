# Humantic AI

Official Humantic AI Data API v1.0 (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HUMANTIC_AI`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Docs (2026-08-20):** [https://api.humantic.ai/](https://api.humantic.ai/)
- **Create:** `GET https://api.humantic.ai/v1/user-profile/create?apikey=…&id=…`
- **Fetch:** `GET https://api.humantic.ai/v1/user-profile/?apikey=…&id=…`
- **Auth:** query parameter `apikey` (official name). OfficialApiNode `auth.type=query` / `configKey=apikey`.

Create GET accepts LinkedIn profile URL or email as `id`. Document and free-form text creates are official **POST multipart** (`document` / `text`); OfficialApiNode JSON-encodes POST bodies, so this package uses the documented GET create for URL/email only.

Fetch after LinkedIn/email create: wait 30–45s; if `metadata.analysis_status` is `IN_PROGRESS`, wait 15s and `get_profile` again.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create_profile` | GET | `/v1/user-profile/create` |
| `get_profile` | GET | `/v1/user-profile/` |

## Ports

`input` / `output` only.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default `create_profile` |
| `apikey` | password | **yes** | Query `apikey` |
| `timeout_seconds` | number | no | Default 45 |

## Legal

- Operator accepts Humantic terms on [api.humantic.ai](https://api.humantic.ai/) / [humantic.ai](https://dev.humantic.ai/solutions/personality-ai-api)
- **Profiling.** DISC / Big Five and hiring/sales personas are inferences about a person. The customer is the controller. Provide notice and a lawful basis (GDPR Arts. 6 and 9 / 22 as applicable) **before** creating a profile. Do **not** use this node for covert HR screening, secret candidate scoring, or profiling people who have not been told.
- Humantic documents opt-out (`metadata` status 61 / 447): do not retry those ids.
- LinkedIn URLs: only send profile URLs the customer is allowed to process; do not scrape LinkedIn HTML.
- Prefer `stateless=true` when sending text/documents (POST path, later) so Humantic does not retain the source.
- Logs: operation, status, analysis_status, sizes — never `apikey`, never personality scores, never emails or LinkedIn URLs in full.
