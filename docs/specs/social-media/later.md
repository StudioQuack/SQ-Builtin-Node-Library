# Later

- **Status:** planned — **DO NOT BUILD** catalog scheduler ops
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/later/` (do not create until a **scheduler** API is published, or a **separate** Influence Reporting node is catalogued)
- **Researched:** 2026-08-20
- **Official API:** Later Influence™ **Reporting API v2** only (`https://reporting.api.later.com`) — **not** visual-calendar posting
- **Docs:** https://docs.reporting.api.later.com/ — Getting started https://docs.reporting.api.later.com/getting-started — Auth https://docs.reporting.api.later.com/authentication — Reference https://docs.reporting.api.later.com/api-reference
- **Auth:** n/a for catalog ops. Influence Reporting uses `clientId` + `clientSecret` → JWT (`POST /oauth/token`), then `Authorization: Bearer`.

## Scope

**DO NOT BUILD** catalog operations `post_schedule`, `post_list`, and `media_upload`.

The catalog row describes Later.com as a **visual scheduler**. Fetches on 2026-08-20 found **no official public API** for calendar create/list/upload:

| URL fetched | Result |
|---|---|
| https://later.com | Marketing site (influencer + social management). Scheduling is a **product** feature, not a documented HTTP API. |
| https://later.com/developers and https://later.com/developers/ | **404** |
| https://developers.later.com and https://developers.later.com/ | Fetch **timeout / not a usable docs portal** |
| https://app.later.com/developers | “Browser not supported” interstitial — **no API reference** |
| https://help.later.com (and scheduling help articles such as “Schedule & Copy Posts With Later on the Web”) | **UI** Post Builder on `app.later.com`. Publishing is described as Later calling **destination** official APIs after the user schedules in the app — not a Later-hosted posting API for third-party developers. |
| https://docs.reporting.api.later.com/ | **Later Influence™ Reporting API** v2.0.0. Base `https://reporting.api.later.com`. Campaign/creator **analytics** (`GET /v2/instances`, `GET /v2/campaigns/performance`, JWT from `POST /oauth/token`). Server-side only (no CORS). |

Third-party blogs describe `https://api.later.com/v2/posts` (Bearer, schedule JSON). **Do not trust or implement those paths.** They are not on Later’s developer docs. Official-API-first: no unofficial calendar client.

Do **not** silently retarget this catalog row to Influence Reporting. Reporting is a **different product**. If StudioQuack later wants influencer campaign metrics, add a **new** catalog node (e.g. Later Influence Reporting) with Reporting API ops — not `post_schedule`.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| — | — | — | **None** for the catalog scheduler verbs. Handler must not call `api.later.com`. |

Retired / unofficial verbs are omitted, not listed as “later”.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | — | no | Do not ship until an official scheduler surface exists |
| `api_key` | password | n/a | empty | yes | Catalog listed this; **there is no documented scheduler key**. Do not collect it for unofficial hosts. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Unused while DO NOT BUILD |
| `output` | OUTPUT | object — `{ error }` | If a stub is ever compiled, always `{ error }` explaining no official scheduler API |

FUNCTION ports: none.

## Input fields (by operation)

None. Catalog ops are blocked.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `error` | string | always if invoked | `Later visual scheduler has no official public posting API (researched 2026-08-20). Do not call unofficial calendar hosts.` |
| `http_status` | number | n/a | No provider call |

## Legal

- Influence Reporting overview: https://docs.reporting.api.later.com/
- Influence help (Reporting API, not scheduler): https://help-influence.later.com/hc/en-us/articles/20462313325207-Later-Influence-API-Integrations
- Later consumer help (UI scheduling / partner platform APIs): https://help.later.com/hc/en-us/articles/1500010572781-Schedule-Copy-Posts-With-Later-on-the-Web — https://help.later.com/hc/en-us/articles/29780709560343-Third-Party-Partner-Data-Safety-Compliance
- Calling reverse-engineered `api.later.com` calendar routes would be unofficial, ToS-risky, and against this library’s official-API-first policy ([LEGAL.md](../../../LEGAL.md)).
- Influence Reporting credentials are high-value secrets; if a **separate** node is catalogued later, bind `client_id` / `client_secret` in config only, JDK HTTP, server-side, `{ error }` mapping for 401/403 (`ANL_00403` etc.).

## Implementation notes

- **Do not implement** `nodes/later/` for `post_schedule` / `post_list` / `media_upload`.
- Do not copy unofficial request shapes from blogs.
- Revisit only if Later publishes a documented scheduler/developer API on an official `later.com` / `docs.*later.com` host.
- Optional future node (not this row): Influence Reporting `GET /v2/campaigns/performance` with JWT from `POST https://reporting.api.later.com/oauth/token`.
