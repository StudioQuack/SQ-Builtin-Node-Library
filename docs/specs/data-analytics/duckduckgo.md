# DuckDuckGo

- **Status:** planned
- **Catalog:** `docs/catalog/data-analytics.md`
- **Folder when built:** `nodes/duckduckgo/`
- **Researched:** 2026-08-20
- **Official API:** DuckDuckGo Instant Answer API (unversioned public GET)
- **Docs:** https://duckduckgo.com/api
- **Auth:** none

## Scope

Calls the **official Instant Answer** endpoint only. It returns zero-click abstracts, definitions, related topics, infobox-ish fields, and `!bang` redirects. **It is not a full SERP** and DuckDuckGo states they do not have rights to syndicate organic link listings. Do **not** scrape `duckduckgo.com` HTML, do not use unofficial HTML/SERP wrappers, and do not treat empty Instant Answers as a license to scrape.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `instant_answer` | GET | `https://api.duckduckgo.com/` | Topic summary / definition / bang redirect |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `instant_answer` | no | Chooses the row above |
| `app_name` | string | yes | `studioquack` | no | Sent as `t` (app name) so DDG can identify the client. No PII. |
| `timeout_seconds` | number | no | `20` | no | Connect + request timeout |
| `no_html` | boolean | no | `true` | no | Default `no_html=1` (strip markup from text) |
| `skip_disambig` | boolean | no | `false` | no | Default `skip_disambig` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `{ query, … }` | |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = instant_answer`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q`. Empty → error, no HTTP. |
| `no_html` | boolean | no | Override instance `no_html` (`1`/`0`) |
| `no_redirect` | boolean | no | `1` = do not follow `!bang` HTTP redirects; return `Redirect` |
| `skip_disambig` | boolean | no | `1` = skip Type D disambiguation |

Always send `format=json`. Do not use JSONP `callback`.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `heading` | string | 2xx | `Heading` |
| `abstract` | string | 2xx | `AbstractText` (prefer over HTML `Abstract`) |
| `abstract_url` | string | 2xx | `AbstractURL` |
| `answer` | string | 2xx | Instant `Answer` when present |
| `answer_type` | string | 2xx | `AnswerType` |
| `type` | string | 2xx | `Type` (A/D/C/N/E) |
| `redirect` | string | bang | `Redirect` |
| `related_topics` | array | 2xx | Normalized `{ text, first_url }` from `RelatedTopics` (flatten nested Topics) |
| `results` | array | 2xx | Official-site style `Results` links only (not a SERP) |
| `image` | string | 2xx | `Image` URL if present |
| `error` | string | failure | |

Empty abstracts are **success** (many queries have no Instant Answer). Do not fall back to HTML search.

## Legal

- Official API page: https://duckduckgo.com/api
- Partnerships / syndication limits: https://help.duckduckgo.com/company/partnerships
- Attribution to DuckDuckGo when Instant Answers are displayed.
- Not a full-results API; organic listings must not be scraped.

## Implementation notes

- Query: `q`, `format=json`, `t={app_name}`, `no_html=1` by default, optional `no_redirect`, `skip_disambig`, `pretty=0`.
- `User-Agent: StudioQuack/1.0 (+https://studioquack.com; {app_name})`.
- Body cap 1 MiB. Timeout 20 s. TLS verified.
- Some responses have historically been `application/x-javascript`; parse as JSON anyway if the body is a JSON object.
- 429/5xx mapping as Brave spec. No inner retry.
