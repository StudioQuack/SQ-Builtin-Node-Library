# Doodle

- **Status:** planned — **DO NOT BUILD**
- **Catalog:** `docs/catalog/productivity-collaboration.md`
- **Folder when built:** `nodes/doodle/` — **do not create this folder**
- **Researched:** 2026-08-20
- **Official API:** none published for new integrations (legacy Enterprise only, undocumented)
- **Docs:** https://help.doodle.com/en/articles/9457023-doodle-api-deprecation
- **Auth:** n/a for new work — catalog `oauth2` / `api_key` are **not** a current public surface

## Scope

**DO NOT BUILD** a Doodle node for new StudioQuack integrations.

Fetched 2026-08-20:

1. **Doodle Help Center (2024-06-11), Kimberly Ford:** [Doodle API deprecation](https://help.doodle.com/en/articles/9457023-doodle-api-deprecation) — verbatim: *“Doodle no longer supports new API.”* Enterprise Admin Console was to be updated. Existing Enterprise API setups may email **auth-setup@doodle.com**; that is support for **already configured** accounts, not a public developer programme.
2. **https://developer.doodle.com** — still resolves, but the page is the **consumer marketing site** (scheduling product copy). There is **no** API reference, no OpenAPI, no auth docs, no base URL.
3. No current official REST catalog (poll create/get/list/close, participant list, webhooks) is published by Doodle.

Do **not** invent `poll_create` (or any other verb) from blogs, archived SDKs, or unofficial GitHub OpenAPI. Do **not** scrape doodle.com HTML. Do **not** ship an unofficial wrapper.

**Partner / Enterprise legacy:** the Help Center acknowledges that some Enterprise accounts already had API access. That remaining surface is **not documented on a public official URL** as of this research date. Until Doodle publishes a current developer contract (paths, auth, ToS for API use), StudioQuack must not implement against hearsay. If a customer later produces a written Doodle partner spec, treat it as a **new** research pass — not this file.

Catalog seed verbs (`poll_create`, `poll_get`, `poll_list`, `poll_close`, `participant_list`, `listen`) are **dropped**.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| — | — | — | **None.** No official public operations to implement. |

Retired / unofficial verbs are omitted, not listed as "later".

## Configuration parameters

Do not add a node definition. If a designer stub is required for catalog completeness, show a disabled node: no secrets collected, no HTTP.

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | n/a | — | no | Unused — no verbs |
| `timeout_seconds` | number | n/a | `25` | no | Unused |

Catalog `oauth_client_id` / `oauth_client_secret` / `api_key` are **not** bound — there is no official token URL to use them against.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Unused |
| `output` | OUTPUT | object — `{ error }` only | If a stub handler is ever invoked: `{ "error": "Doodle public API is closed to new integrations (Help Center 2024-06-11). Do not call Doodle over HTTP from this node." }` — **no network**. |

FUNCTION ports: none. No `file` ports.

## Input fields (by operation)

None. There are no operations.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `error` | string | always if invoked | Fixed do-not-build message; no HTTP body |

## Legal

- Deprecation (authoritative): https://help.doodle.com/en/articles/9457023-doodle-api-deprecation
- Product GTC (web product, not an API licence): https://doodle.com/en/general-terms-and-conditions/
- Privacy / legal hub: https://doodle.com/en/legal-notice/
- Enterprise MSA (customers with a signed order): https://doodle.com/en/master-services-agreement/
- **Quota:** none published — there is no public API to quota.
- **PII:** Doodle polls contain participant names and emails. A scrape or unofficial client would process personal data without a documented processor path — forbidden.
- Official-API-first: no scrape, no unofficial Discord/web wrapper, no reconstructed poll JSON from browser traffic.

## Implementation notes

- **Do not** add `nodes/doodle/`.
- **Do not** call any guessed host (`api.doodle.com`, `doodle.com/api`, etc.).
- If product later requires a placeholder in the designer, the handler returns `{ error }` with **zero** outbound requests (same pattern as unofficial nodes that refuse before HTTP).
- Timeouts, size caps, and retry do not apply — there is no client.
- Logs: if a stub exists, log only `operation` + `do_not_build` — never emails or poll titles.
