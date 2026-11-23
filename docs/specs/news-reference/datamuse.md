# Datamuse

- **Status:** planned
- **Catalog:** `docs/catalog/news-reference.md`
- **Folder when built:** `nodes/datamuse/`
- **Researched:** 2026-08-20
- **Official API:** Datamuse API v1.1 (`api.datamuse.com`)
- **Docs:** https://www.datamuse.com/api/
- **Auth:** none today. **From 2026-08-20 docs: an API key is required starting 1 January 2027** (100 000 requests/day per key). Optional/future secret: `api_key`.

## Scope

One node for Datamuse lexical search: `/words` (constrained word finding) and `/sug` (autocomplete). Read-only HTTPS JSON. Do not scrape datamuse.com or OneLook HTML. Do not vendor a Java SDK (Datamuse4J exists; ignore it — JDK HTTP only).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `words` | GET | `/words` | Words matching meaning / spelling / sound / related-word constraints |
| `suggest` | GET | `/sug` | Autocomplete / prefix suggestions (`s`) |

Base origin: `https://api.datamuse.com`. HTTP is documented; **use HTTPS only**.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `words` | no | Chooses the row above |
| `api_key` | password | no | empty | yes | **Optional until 2027-01-01; required on/after that date** once Datamuse publishes the wire format. Until then, if set, attach as query `key` **only if** live docs name that param — otherwise skip the HTTP attachment and still store the secret for the cutover. Never on INPUT. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1 s. |
| `default_max` | number | no | `100` (`words`) / `10` (`suggest`) | no | Default `max` when input omits it. Clamp 1–1000. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — constraint fields | No secrets |
| `output` | OUTPUT | object — `{ status, words }` or `{ error }` | Failures never include `api_key` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = words`

At least one constraint among `ml`, `sl`, `sp`, or any `rel_*` must be present (Datamuse allows all optional, but an unconstrained call is useless and abusive). If all empty → `{ error }` no HTTP.

| Field | Type | Required | Purpose |
|---|---|---|---|
| `ml` | string | no | Means-like |
| `sl` | string | no | Sounds-like |
| `sp` | string | no | Spelled-like / wildcard (`*`, `?`) |
| `rel_jja` | string | no | Popular nouns modified by this adjective |
| `rel_jjb` | string | no | Popular adjectives for this noun |
| `rel_syn` | string | no | Synonyms (WordNet) |
| `rel_trg` | string | no | Triggers |
| `rel_ant` | string | no | Antonyms |
| `rel_spc` | string | no | Kind-of (hypernyms) |
| `rel_gen` | string | no | More-general-than (hyponyms) |
| `rel_com` | string | no | Comprises (holonyms) |
| `rel_par` | string | no | Part-of (meronyms) |
| `rel_bga` | string | no | Frequent followers |
| `rel_bgb` | string | no | Frequent predecessors |
| `rel_hom` | string | no | Homophones |
| `rel_cns` | string | no | Consonant match |
| `v` | string | no | Vocabulary id. Omit = English ~550k. `es` = Spanish books vocab. |
| `topics` | string | no | ≤ 5 topic words, space or comma delimited |
| `lc` | string | no | Left context (one word) |
| `rc` | string | no | Right context (one word) |
| `max` | number | no | 1–1000. Default 100. |
| `md` | string | no | Metadata flags concatenated, no delimiter: `d` defs, `p` POS, `s` syllables, `r` pronunciation, `f` frequency |
| `ipa` | boolean | no | `true` → `ipa=1` (IPA instead of Arpabet when `md` includes `r`) |
| `qe` | string | no | Query echo: name of another param (`sp`, `ml`, …) |

Map `rel_*` input fields to query params `rel_jja`, `rel_jjb`, … as documented.

### `operation = suggest`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `s` | string | yes | Prefix hint. Trim. Empty → `{ error }` no HTTP. Alias `query`. |
| `max` | number | no | 1–1000. Default 10. |
| `v` | string | no | Same vocabulary id as `/words` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `words` | array | success | Provider list: `{ word, score, …tags/defs when md requested }`. Empty list is success. |
| `error` | string | failure | Actionable, no key |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | 60 on 429; 30 on 5xx/timeout |

## Legal

- API contract / limits: https://www.datamuse.com/api/
- Until **2027-01-01**: no key required; **100 000 requests/day**; beyond that may be rate-limited without notice.
- From **2027-01-01**: API key required; **100 000 requests/day per key**. Current users without a key must request one via Datamuse’s feedback form (as stated on the API page).
- Attribution appreciated for publicly available apps.
- Privacy: query logs discarded end of day; long-term they keep request counts by category.
- Definitions (`md=d`) come from Wiktionary and WordNet — those licences apply to definition text.
- Do not scrape. Lexical use only; no bulk dump via this node (use `max` ≤ 1000 per call).

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- URL-encode all query values. Wildcards in `sp` must still be encoded (`*` → `%2A`).
- Response body cap **1_000_000** bytes. Timeout default 25 s (floor 1).
- ⚠️ verify (2027): Datamuse announced the key requirement but **did not document the query/header name** on 2026-08-20. Implement `api_key` as a config secret now; attach it only after the live docs specify the scheme. Do not guess `Authorization: Bearer` vs `?key=`.
- 429 → `retry_after_seconds: 60`. 5xx / timeout → 30. No auto-retry.
- Logs: operation, status, result count, sizes — not query strings (may be PII-ish in `ml`/`topics`).
