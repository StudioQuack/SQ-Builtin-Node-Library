# Hunter

Official [Hunter REST API v2](https://hunter.io/api-documentation/v2) at `https://api.hunter.io`. Handler: `OfficialApiNode` via `api_spec.json`. Domain search, email finder, email verifier only.

- **Docs:** https://hunter.io/api-documentation/v2
- **Base:** `https://api.hunter.io`
- **API:** v2
- **Auth:** header `X-API-KEY` (`api_key`). **Do not** put the key on the query string.

## Operations

| operation | HTTP | Path | Requirements |
|---|---|---|---|
| `domain.search` | GET | `/v2/domain-search` | `domain` **or** `company` (upstream 400 if both missing) |
| `email.finder` | GET | `/v2/email-finder` | domain/company + name (`first_name`/`last_name`, `full_name`, or `linkedin_handle`) |
| `email.verifier` | GET | `/v2/email-verifier` | `email` |

### `domain.search` filters (validated locally when set)

| Parameter | Allowed values |
|---|---|
| `limit` | 1–100 |
| `offset` | 0–10000 |
| `type` | `personal` \| `generic` |
| `seniority` | Comma list: `junior`, `senior`, `executive` |
| `department` | Comma list: `executive`, `it`, `finance`, `management`, `sales`, `legal`, `support`, `hr`, `marketing`, `communication`, `education`, `design`, `health`, `operations`, `product`, `research`, `consulting`, `administrative`, `procurement` |
| `required_field` | Comma list: `full_name`, `position`, `phone_number` |

Malformed domains may return HTTP 200 with `emails: []` (Hunter does not always 400).

### Response shapes

- **`domain.search`:** `data.emails[]` (empty array when none).
- **`email.finder`:** HTTP 200 with no match may omit the `email` key — check presence before use (`sources: []`).
- **`email.verifier`:** prefer `status` (`valid`, `accept_all`, `invalid`, `disposable`, …). Hunter still returns deprecated `result` (`deliverable` / `risky` / …) with `_deprecation_notice` in some payloads — migrate to `status`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `api_key` | password | yes | Header `X-API-KEY` |
| `timeout_seconds` | number | no | Default 25 |

Test key `test-api-key` is documented by Hunter (dummy payloads). Allow it.

## Ports

`input` / `output`. Success: `{ status: "ok", http_status, data }`. Hunter errors use structured JSON (`id`, `code`, `details`) in the HTTP body. Secrets redacted.

## Legal

- Terms (API §4): https://hunter.io/terms-of-service
- Privacy: https://hunter.io/privacy-policy · GDPR: https://help.hunter.io/en/articles/1890029-gdpr-compliance · DPA: https://hunter.io/data-processing-agreement
- Operator is an independent controller for names/emails obtained via the API (typically legitimate interest for B2B prospecting, plus suppression). StudioQuack does not supply that basis.
- Rate (published): Domain Search / Email Finder 15 rps / 500 rpm; Verifier 10 rps / 300 rpm.
- Official API only. Do not scrape hunter.io HTML. Do not build a redistributed email database from this node.
