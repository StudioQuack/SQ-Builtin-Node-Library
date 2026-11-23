# Dictionary API

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/dictionary-api/`
- **Researched:** 2026-08-20
- **Official API:** Merriam-Webster Dictionary API v3 — Collegiate® JSON (primary). Optional community REST: dictionaryapi.dev v2.
- **Docs:** https://www.dictionaryapi.com/ · Collegiate JSON: https://www.dictionaryapi.com/products/api-collegiate-dictionary · JSON field guide: https://www.dictionaryapi.com/products/json · Community: https://dictionaryapi.dev/
- **Auth:** api_key — secret binding: `api_key` (MW query `key`). None when `backend = dictionaryapi_dev`.

## Scope

One node that looks up English definitions. **Primary backend is the official Merriam-Webster Collegiate Dictionary JSON API.** Commercial operators must use MW under a commercial licence.

Optional second backend: `https://api.dictionaryapi.dev/api/v2/entries/en/{word}` — a **documented community HTTP JSON API**, not an HTML scraper. Relative to a dictionary publisher it is **unofficial/community**. Flag it in the designer. Default `backend` is `merriam_webster`.

**Drop `random`.** Neither MW Collegiate nor dictionaryapi.dev documents a random-word endpoint as of 2026-08-20.

Do not scrape merriam-webster.com, dictionary.com, or any dictionary website HTML. Do not call unpublished MW products beyond Collegiate unless a later spec adds them on this same node.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `define` | GET | MW: `/api/v3/references/collegiate/json/{word}?key={key}` · community: `/api/v2/entries/en/{word}` | Headword lookup |

MW origin: `https://www.dictionaryapi.com`. Community origin: `https://api.dictionaryapi.dev`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `define` | no | Only `define` |
| `backend` | select | no | `merriam_webster` | no | `merriam_webster` (default, official) \| `dictionaryapi_dev` (community; designer must label unofficial) |
| `api_key` | password | if MW | empty | yes | MW `key` query param. Required when `backend = merriam_webster`. Ignored for `dictionaryapi_dev`. Never on INPUT. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

When `backend = dictionaryapi_dev`, treat as an opt-in community path (LEGAL.md unofficial pattern): off unless the operator selected it on this instance.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `word` | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = define`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `word` | string | yes | Headword. Trim. Empty → `{ error }` no HTTP. Path-encode (UTF-8). Reject `/`, `?`, `#` after encoding check. Alias `q`. |

No `random` field.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx with entries | `ok` |
| `backend` | string | always on success | Echo `merriam_webster` or `dictionaryapi_dev` |
| `word` | string | success | Looked-up headword |
| `entries` | array | MW entries or community entries | Normalized senses (see below) |
| `suggestions` | array of string | MW 200 with suggestion list (no entries) | MW returns a JSON array of **strings** when the headword is unknown |
| `shortdef` | array of string | MW entry objects | MW `shortdef` when present |
| `attribution` | string | MW success | Operator-facing reminder: display Merriam-Webster logo per brand guidelines |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

**MW parse rule:** if the JSON root is an array of **strings**, map to `suggestions` and `status: ok` (HTTP 200 is normal for “not found, did you mean”). If the array contains objects with `meta` / `hwi` / `def`, map to `entries`.

**Normalized entry (minimum):** `id` (MW `meta.id` or community `word`), `fl` / `part_of_speech`, `shortdef` or community `meanings[].definitions[].definition`, `offensive` (MW `meta.offensive` when present). Cap nested `sseq` depth; do not dump the entire MW XML-in-JSON token soup into logs.

Community 404 JSON (`title`: No Definitions Found) → `{ error }` with that title, `http_status: 404`.

## Legal

- MW Terms (non-commercial licence): https://www.dictionaryapi.com/info/terms-of-service
- MW FAQ: https://www.dictionaryapi.com/info/frequently-asked-questions
- MW brand guidelines (logo **required** on every app that uses the API): https://www.dictionaryapi.com/info/branding-guidelines
- **Free MW:** non-commercial only; **≤ 1000 queries/day/key**; **max two reference APIs**. Commercial (ads or paid product) requires a licence — contact MW. ToS also forbids submitting automated/recorded queries unless MW approved in writing — a workflow node is automated; **commercial and high-volume operators must have a licence / written approval**.
- Definitions, etymologies, audio, and illustrations are copyright Merriam-Webster. Do not strip proprietary notices.
- Logo: unmodified, approved sizes 50 / 100 / 125 px (or official `.ai`), ® visible. Do not imply MW endorsement (ToS trademarks clause).
- **dictionaryapi.dev:** community project (GitHub `meetDeveloper/freeDictionaryAPI`). Not a publisher API. No MW licence, no MW logo obligation, **no commercial warranty**. Prefer MW for production/commercial. Do not scrape dictionary websites as a fallback.
- Redistribution of MW text follows the MW licence, not StudioQuack’s.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- MW URL: `GET https://www.dictionaryapi.com/api/v3/references/collegiate/json/{encodedWord}?key={api_key}`
- Community URL: `GET https://api.dictionaryapi.dev/api/v2/entries/en/{encodedWord}` — no key.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- MW empty `api_key` → `{ error: "Missing Merriam-Webster API key. Bind api_key in node parameters." }` — no HTTP.
- Do not log the request URI (it contains `key=`). Log operation, backend, status, entry count, body size.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- Do not fetch MW audio/illustration files in this node (those are separate URLs in the JSON). Return the documented audio filename fields only; operator can retrieve media with HTTP Request subject to MW image/audio rules.
- 429 → `retry_after_seconds: 60` (honor `Retry-After`). 5xx / timeout → 30. No auto-retry.
- Unknown `backend` → `{ error }` no HTTP.
