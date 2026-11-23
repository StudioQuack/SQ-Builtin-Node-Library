# DeepL

Official DeepL API node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_DEEPL`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** DeepL API v2 (translate) + v3 (languages + multilingual glossaries). OpenAPI spec version observed: `3.13.0`. Researched 2026-08-20.
- **Origin (this node):** Pro `https://api.deepl.com`. OfficialApiNode has a single `baseUrl` — Pro is the default.
- **Free plan host:** `https://api-free.deepl.com`. Free keys are identifiable by the `:fx` suffix. Point a later `baseUrl` override at the Free host when using a Free key; sending a Free key to `api.deepl.com` typically 403s.
- **Docs:** [Auth](https://developers.deepl.com/docs/getting-started/auth) · [Translate](https://developers.deepl.com/api-reference/translate/request-translation) · [Languages v3](https://developers.deepl.com/api-reference/languages/retrieve-languages-by-resource) · [Create glossary](https://developers.deepl.com/api-reference/multilingual-glossaries/create-a-glossary)
- **Auth:** StudioQuack secret `auth_key` → header `Authorization: DeepL-Auth-Key {key}`. Never on INPUT. Never logged.

## Scope

One node for DeepL text translation, glossary management, and language discovery. Do not scrape deepl.com. Document translation (`POST /v2/document`), Voice API, Write/rephrase/correct, and translation memories are out of scope.

Dropped: `GET /v2/languages` and `GET /v2/glossary-language-pairs` (deprecated; v3 replaces them). `POST /v2/glossaries` (monolingual v2; DeepL recommends v3).

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `translate` | POST | `/v2/translate` | Text translation (JSON body) |
| `glossary_create` | POST | `/v3/glossaries` | Create a multilingual glossary (201) |
| `glossary_list` | GET | `/v3/glossaries` | List glossaries (metadata, no entries) |
| `languages` | GET | `/v3/languages` | Supported languages (`resource` query) |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | `operation` (`translate` \| `glossary_create` \| `glossary_list` \| `languages`) overrides the instance select when set. Text only — no `file` on translate. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` . Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. Secrets redacted. |

FUNCTION ports: none. Document files are not this node.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `translate`. |
| `auth_key` | password | **yes** | `Authorization: DeepL-Auth-Key {auth_key}`. |
| `formality` | select | no | Default for `translate`: `default` \| `more` \| `less` \| `prefer_more` \| `prefer_less`. |
| `timeout_seconds` | number | no | Default `30`. Floor 1. |

Plan vs host: this package ships Pro `https://api.deepl.com`. Free: `https://api-free.deepl.com`. OfficialApiNode cannot switch hosts from a `plan` config today.

## Legal

- [DeepL Pro license / API terms](https://www.deepl.com/pro-license)
- [Developer docs](https://developers.deepl.com/docs)
- [Privacy](https://www.deepl.com/privacy)
- Free: 500 000 characters/month. Request 128 KiB. Header 16 KiB.
- Operator is the DeepL API customer. Translated text and glossary entries may contain personal data; they leave the daemon toward DeepL (EU-based provider). DPIA if the workflow handles identifiable content.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `header` / `Authorization` / prefix `DeepL-Auth-Key `.
- Do not use `multipart/form-data` on `/v2/translate` (unsupported).
- HTTP 456 = quota exceeded. 429 / 529 = rate/overload. No in-handler retry loop.
- Logs: operation, status — never `auth_key`, never source/target text.

## Unverified

- Whether `GET /v3/glossaries` pagination exists beyond a flat list.
- Live list of glossary-incompatible languages besides Thai.
