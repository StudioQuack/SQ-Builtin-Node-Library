# Travis CI

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/travis-ci/`
- **Researched:** 2026-08-20
- **Official API:** Travis CI API **V3** (hypermedia REST)
- **Docs:** https://docs.travis-ci.com/user/developer/ · explorer https://developer.travis-ci.com/
- **Auth:** api_key — secret binding: `apiToken` (header `Authorization: token {apiToken}`)
- **Liveness (2026-08-20):** `GET https://api.travis-ci.com/` with `Travis-API-Version: 3` returned **HTTP 200** and a V3 home document (`@type: home`). `GET https://api.travis-ci.org/` returned **HTTP 404**. **`.org` is dead; do not call it.**

## Scope

One node for Travis CI **com** (and optional Enterprise base URL). Operations: get/list/cancel/restart/trigger builds via API V3.

**Dropped:** API V2 / V2.1 (`Accept: application/vnd.travis-ci.2.1+json`). Travis documented V2 deprecation years ago; V3 is the current explorer surface. **Dropped host:** `api.travis-ci.org` (org shutdown completed 2021; confirmed 404 on 2026-08-20). Catalog `orgOrCom` → implement as `host` default `com` only.

Do not scrape travis-ci.com HTML.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `build_get` | GET | `/build/{build.id}` | One build by numeric id |
| `build_list` | GET | `/repo/{repository.slug}/builds` | Builds for a repo (slug URL-encoded, `/` → `%2F`) |
| `build_cancel` | POST | `/build/{build.id}/cancel` | Cancel a running build |
| `build_restart` | POST | `/build/{build.id}/restart` | Restart a completed/canceled build |
| `build_trigger` | POST | `/repo/{repository.slug}/requests` | Create a request (API trigger). Body `{ "request": { "branch", "message", "config"? } }` |

Also documented but **not** catalogued: `POST /build/{id}/priority` (omit). List-for-current-user `GET /builds` is optional extra; prefer repo-scoped `build_list`.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `build_get` | no | Chooses the row above |
| `apiToken` | password | yes | empty | yes | Travis API token (`travis token --pro`). Header `Authorization: token …`. Never on INPUT. |
| `host` | select | no | `com` | no | `com` → `https://api.travis-ci.com`. Do not offer `org`. |
| `baseUrl` | string | no | empty | no | Travis CI **Enterprise** API origin (e.g. `https://travis.example.com/api`). If set, overrides `host`. HTTPS required. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout. Floor 1. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo `apiToken` |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = build_get` | `build_cancel` | `build_restart`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `build_id` | number | yes | Travis build id |

### `operation = build_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repo_slug` | string | yes | `owner/name` (handler encodes `/` as `%2F`) |
| `limit` | number | no | Page size (default 25, cap 100) |
| `offset` | number | no | Pagination offset |
| `state` | string | no | Filter `build.state` (e.g. `passed`, `failed`, `started`) |
| `branch` | string | no | Query `branch.name` |
| `event_type` | string | no | e.g. `push`, `pull_request`, `api` |

### `operation = build_trigger`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `repo_slug` | string | yes | `owner/name` |
| `branch` | string | no | Default `master` only if omitted **and** caller intends default branch — prefer requiring `branch`. If blank → `{ error }` no HTTP. |
| `message` | string | no | Request message |
| `sha` | string | no | Commit SHA if Travis accepts `request.sha` |
| `config` | object | no | Overrides keys in `.travis.yml` (`request.config`) |

`request.token` (GitHub webhook token) is **DEPRECATED** on the V3 create-request body — do not send it.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `build` | object | get/cancel/restart | id, number, state, duration, event_type, started_at, finished_at, branch, commit (sha/message) |
| `builds` | array | `build_list` | Same summary fields |
| `@pagination` | object | `build_list` | Travis pagination object if present |
| `request` | object | `build_trigger` | Pending request id, remaining_requests, repository slug |
| `error` | string | failure | No token |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 30–60 with jitter |

## Legal

- API overview: https://docs.travis-ci.com/user/developer/
- V3 explorer: https://developer.travis-ci.com/
- Authentication: https://developer.travis-ci.com/authentication
- Build resource: https://developer.travis-ci.com/resource/build
- Requests (trigger): https://developer.travis-ci.com/resource/requests
- `.org` shutdown: https://www.travis-ci.com/blog/2021-05-07-orgshutdown/ — do not target org.
- Operator must hold a Travis account/token and comply with Travis CI terms for their plan. Build logs may include personal data.
- Rate limits are not a single published number; honor 429.

## Implementation notes

- JDK HTTP only. TLS on. `Redirect.NEVER`.
- Every request headers: `Travis-API-Version: 3`, `Authorization: token {apiToken}`, `User-Agent: StudioQuack/1.0`, `Accept: application/json`, `Content-Type: application/json` on POST.
- Origin: `baseUrl` if set, else `https://api.travis-ci.com`. **Never** `https://api.travis-ci.org`.
- Empty token → `{ error: "Missing Travis API token. Bind apiToken in node parameters." }` — no HTTP.
- Body cap **8 MiB**. 429: retry up to 3 times with jitter then `{ error, http_status: 429 }`.
- Logs: operation, build id / repo slug, status — never token, never `.travis.yml` secrets inside `config`.
- Failures → `{ error }` on OUTPUT.
