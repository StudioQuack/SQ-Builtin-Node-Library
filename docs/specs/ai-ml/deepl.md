# DeepL

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** `nodes/deepl/`
- **Researched:** 2026-08-20
- **Official API:** DeepL API v2 (translate) + v3 (languages + multilingual glossaries). OpenAPI spec version observed: `3.13.0`
- **Docs:** [Auth](https://developers.deepl.com/docs/getting-started/auth) · [Translate](https://developers.deepl.com/api-reference/translate/request-translation) · [Languages v3](https://developers.deepl.com/api-reference/languages/retrieve-languages-by-resource) · [Create glossary](https://developers.deepl.com/api-reference/multilingual-glossaries/create-a-glossary) · [Usage limits](https://developers.deepl.com/docs/resources/usage-limits)
- **Auth:** api_key — secret binding: `auth_key` (header `Authorization: DeepL-Auth-Key {key}`)

## Scope

One node for DeepL **text translation, glossary management, and language discovery**. Host is selected by config `plan`: Free → `https://api-free.deepl.com`, Pro → `https://api.deepl.com`. Free keys are identifiable by the `:fx` suffix.

Do not scrape deepl.com. Do not call the consumer website. Document translation (`POST /v2/document`), Voice API, Write/rephrase/correct, and translation memories are **out of scope** (not catalog operations).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `translate` | POST | `/v2/translate` | Text translation (JSON body) |
| `glossary_create` | POST | `/v3/glossaries` | Create a multilingual glossary (201) |
| `glossary_list` | GET | `/v3/glossaries` | List glossaries (metadata, no entries) |
| `languages` | GET | `/v3/languages?resource={resource}` | Supported languages for a resource |

**Dropped:** `GET /v2/languages` and `GET /v2/glossary-language-pairs` (deprecated; v3 replaces them). `POST /v2/glossaries` (monolingual v2; DeepL recommends v3). Document upload/status/download, `/v2/write`, `/v2/voice`, style rules, TMX memories.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `translate` | no | Chooses the row above |
| `auth_key` | password | yes | empty | **yes** | DeepL authentication key. Header only. Never on INPUT |
| `plan` | select | yes | `pro` | no | `free` \| `pro`. Sets origin. If key ends with `:fx` and `plan=pro` (or the reverse), fail with `{ error }` before HTTP |
| `formality` | select | no | omit | no | Default for `translate`: `default` \| `more` \| `less` \| `prefer_more` \| `prefer_less` |
| `timeout_seconds` | number | no | `30` | no | Connect+read. Floor 1 |

Origin: `plan=free` → `https://api-free.deepl.com`; `plan=pro` → `https://api.deepl.com`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include `auth_key` |

FUNCTION ports: none. No `file` on translate (text only). Document files are not this node.

## Input fields (by operation)

### `operation = translate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `text` | string or array | yes | Body `text` as JSON array of UTF-8 strings. A single string is wrapped as one-element array. Empty → `{ error }` no HTTP |
| `target_lang` | string | yes | e.g. `DE`, `EN-US`, `EN-GB`, `PT-BR`, `ZH-HANS` |
| `source_lang` | string | no | Required if `glossary_id` is set (glossaries cannot use auto-detect) |
| `formality` | string | no | Overrides config |
| `glossary_id` | string | no | Apply a glossary; `source_lang` required |
| `preserve_formatting` | boolean | no | Body flag |
| `split_sentences` | string | no | `0` \| `1` \| `nonewlines` as documented |
| `tag_handling` | string | no | `xml` \| `html` |
| `context` | string | no | Unbilled context string |
| `show_billed_characters` | boolean | no | Ask for `billed_characters` in the response |

Request size cap (provider): **128 KiB** total; header **16 KiB**. Handler must refuse oversized `text` before HTTP.

### `operation = glossary_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | Glossary name (≤ 1024 UTF-8 bytes) |
| `source_lang` | string | yes* | First dictionary source (lowercase, e.g. `en`) |
| `target_lang` | string | yes* | First dictionary target (e.g. `de`) |
| `entries` | string | yes* | TSV (`Hello\tGuten Tag`) or CSV per `entries_format` |
| `entries_format` | string | no | `tsv` (default) \| `csv` |
| `dictionaries` | array | no | Full v3 `dictionaries` array. If set, overrides the single-pair fields above |

Provider: max glossary **10 MiB**, 1000 glossaries/account, Thai not supported for glossaries (⚠️ confirm live language table).

### `operation = glossary_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Lists account glossaries |

### `operation = languages`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `resource` | string | no | Default `translate_text`. Allowed: `translate_text` \| `glossary` (other resources exist but are out of catalog scope) |
| `include` | string or array | no | `beta` and/or `external` as documented |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx / 201 | `ok` |
| `translations` | array | `translate` | `{ text, detected_source_language, billed_characters? }` |
| `glossary_id` | string | `glossary_create` | New id |
| `name` | string | glossary ops | Glossary name |
| `dictionaries` | array | `glossary_create` | Per-pair `source_lang`, `target_lang`, `entry_count` |
| `glossaries` | array | `glossary_list` | Metadata list from DeepL |
| `languages` | array | `languages` | Provider array (codes, names, `usable_as_source` / `usable_as_target`, features) |
| `error` | string | failure | Includes HTTP 456 quota exceeded |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 529 / 5xx | 60 on 429/529; 30 on 5xx |

DeepL 456 = quota exceeded (Free 500 000 characters/month). Map to `{ error, http_status: 456 }`.

## Legal

- API terms of service (OpenAPI `termsOfService`): https://www.deepl.com/pro-license
- Developer docs: https://developers.deepl.com/docs
- Privacy: https://www.deepl.com/privacy
- Free: **500 000 characters/month**. Request **128 KiB**. Header **16 KiB**.
- Operator is the DeepL API customer. Translated text and glossary entries may contain personal data; they leave the daemon toward DeepL (EU-based provider). DPIA if the workflow handles identifiable content.
- Attribution: follow DeepL’s current API terms for displaying translations in a product UI (do not invent a badge DeepL does not require).

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Header: `Authorization: DeepL-Auth-Key {auth_key}`, `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`.
- Empty `auth_key` → `{ error: "Missing DeepL auth_key. Bind auth_key in node parameters." }` — no HTTP.
- Enforce `plan` vs `:fx` key suffix to avoid sending a Free key to `api.deepl.com` (typically 403).
- Do not use `multipart/form-data` on `/v2/translate` (unsupported).
- Response body cap **1_000_000** bytes. Timeout default 30 s.
- 429 / 529 → `{ error, retry_after_seconds: 60 }` (honor `Retry-After`). No in-handler retry loop.
- Logs: operation, status, character counts — never `auth_key`, never source/target text.

## Unverified

- Whether `GET /v3/glossaries` pagination exists beyond a flat list (docs show a list; confirm if accounts with hundreds of glossaries need a cursor).
- Live list of glossary-incompatible languages besides Thai.
