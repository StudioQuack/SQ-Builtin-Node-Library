# OpenThesaurus

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/openthesaurus/`
- **Researched:** 2026-08-20
- **Official API:** OpenThesaurus HTTP JSON search (site version 1.3.209 as of research date)
- **Docs:** https://www.openthesaurus.de/about/api · Licence: https://www.openthesaurus.de/about · Download/licence: https://www.openthesaurus.de/about/download
- **Auth:** none

## Scope

One node for the official **German** thesaurus JSON search. Operation `synonyms` only. GET `https://www.openthesaurus.de/synonyme/search` with `format=application/json`. Do not scrape HTML search pages. Do not call Wikipedia/Wiktionary search (explicitly unsupported). XML (`format=text/xml`) is documented; this node uses JSON only.

Live check 2026-08-20: `GET /synonyme/search?q=test&format=application/json` returns `metaData.apiVersion: "0.2"` and `synsets[]`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `synonyms` | GET | `/synonyme/search?q={q}&format=application/json` | German synonym groups for a query word |

Origin: `https://www.openthesaurus.de`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `synonyms` | no | Only `synonyms` |
| `user_agent` | string | yes | empty | no | Must contain a contact homepage or email (API rule 2). Example: `StudioQuack/1.0 (ops@example.com)`. Empty → `{ error }` no HTTP. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — query + flags | No secrets |
| `output` | OUTPUT | object — `{ status, synsets, … }` or `{ error }` | Failures never include secrets (none) |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = synonyms`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `q` | string | yes | German query word. Trim. Empty → `{ error }` no HTTP. UTF-8 URL-encode (e.g. `hören` → `h%C3%B6ren`). Alias `word`. |
| `similar` | boolean | no | `similar=true` — up to five similarly spelled words (`distance` = Levenshtein) |
| `substring` | boolean | no | `substring=true` — up to ten substring matches |
| `substringFromResults` | number | no | Offset for substring hits (with `substring`) |
| `substringMaxResults` | number | no | Count of substring hits; default 10, max **250** |
| `startswith` | boolean | no | `startswith=true` — prefix matches |
| `startsWithFromResults` | number | no | Offset for prefix hits |
| `startsWithMaxResults` | number | no | Count cap for prefix hits |
| `supersynsets` | boolean | no | Include hypernyms per synset |
| `subsynsets` | boolean | no | Include hyponyms per synset |
| `baseform` | boolean | no | JSON only: lemma if the query is inflected (`Krankenhäuser` → `Krankenhaus`) |

Boolean true → send `true` as the query value. Omit false/absent params.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `synsets` | array | success | `{ id, categories, terms: [{ term, level? }] }` |
| `similarTerms` | array | `similar=true` | Provider similar-word list when present |
| `substringTerms` | array | substring/prefix | Provider extra lists when present |
| `baseforms` | array | `baseform=true` | Lemma list when present |
| `meta` | object | success | `apiVersion`, `license`, `source`, `copyright` from `metaData` (pass through; include `source` for attribution) |
| `attribution` | string | success | `https://www.openthesaurus.de` (no `nofollow`) |
| `error` | string | failure | Actionable |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout / rate-limit | 60 on 429; 30 on 5xx/timeout |

## Legal

- API rules: https://www.openthesaurus.de/about/api
  1. Visible link to www.openthesaurus.de **without `nofollow`**. Background-only use: imprint credit is enough.
  2. Every request **User-Agent must include contact** (homepage or email).
  3. **> 60 requests/minute/IP → error.** Bulk via database download, not the API.
  4. Long-term users should email `feedback@openthesaurus.de` first (format-change notice).
- Data licence (operator chooses one): **CC BY-SA 4.0** or **GNU LGPL 2.1** — https://www.openthesaurus.de/about and https://www.openthesaurus.de/about/download. Redistribute only under that licence **and** with a real link to openthesaurus.de.
- Site software is AGPL (GitHub); this node calls the public HTTP API only.
- German-language thesaurus. Umlauts are folded with ASCII equivalents (`tur` matches `Tür`).
- Do not scrape HTML.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Accept: application/json`, `User-Agent: {user_agent}` (must include contact).
- Query: always `format=application/json`.
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- Do not auto-retry (rate-limit is per IP; retry loops make it worse).
- Logs: operation, status, synset count, sizes — not the query word beyond length.
- Site warning in `metaData.warning` asks serious users to contact feedback@ — surface `meta.warning` on output; do not strip it.
