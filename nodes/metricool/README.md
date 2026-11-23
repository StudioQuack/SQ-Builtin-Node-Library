# Metricool

Official Metricool REST API 2.0 (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_METRICOOL`). Dispatched by `OfficialApiNode` from `api_spec.json`.

- **Docs:** https://app.metricool.com/resources/apidocs/index.html
- **OpenAPI:** https://app.metricool.com/api/swagger.json
- **Help:** https://help.metricool.com/api-access-export-your-metricool-data-to-other-tools-and-automate-tasks-x8ln5
- **Base:** `https://app.metricool.com/api`
- **Auth:** header `X-Mc-Auth` = `api_token` (Account Settings → API `userToken`). Never on INPUT.

Advanced/Custom plan required. Every call also sends query `userId` and `blogId` (config shortCodes match those names so `queryFrom` maps).

## Operations

| operation | HTTP | Path | Query |
|---|---|---|---|
| `post_schedule` | POST | `/v2/scheduler/posts` | `userId`, `blogId` |
| `analytics_get` | GET | `/v2/analytics/aggregation` | `network`, `metric`, `from`, `to`, `userId`, `blogId` |
| `inbox_list` | GET | `/v2/inbox/conversations` | `provider`, `userId`, `blogId` |

Swagger `ScheduledPost` requires `providers`, `publicationDate`, `text`. Send `publicationDate` as `{ dateTime, timezone }` (e.g. `Europe/Madrid`).

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_token` | password | yes | `X-Mc-Auth` |
| `userId` | string | no | Query `userId` |
| `blogId` | string | no | Query `blogId` (brand) |
| `timeout_seconds` | number | no | Default 30 |

## Ports

One INPUT `input`, one OUTPUT `output`. Optional `media` is type `file`. Failures: `{status,http_status,data}` — never includes `api_token`.

`OfficialApiNode` posts `bodyFrom` as JSON. Presigned S3 (`PUT /v2/media/s3/upload-transactions`) is not a separate operation in this package.

## Legal

- Help / API access: https://help.metricool.com/api-access-export-your-metricool-data-to-other-tools-and-automate-tasks-x8ln5
- Basic integration: https://help.metricool.com/basic-guide-for-api-integration-r97af
- Operator must hold an eligible plan and accept Metricool product terms. Inbox and analytics include social-network PII.
- Numeric HTTP rate limits were not published in swagger/help fetched 2026-08-20; honor 429 and the in-product API usage monitor. Destination-network ToS still apply. Official API only.
