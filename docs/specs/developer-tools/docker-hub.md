# Docker Hub

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/docker-hub/`
- **Researched:** 2026-08-20
- **Official API:** Docker Hub HTTP API **v2** (`https://hub.docker.com/v2`)
- **Docs:** https://docs.docker.com/docker-hub/api/latest/ · OpenAPI https://docs.docker.com/reference/api/hub/latest.yaml
- **Auth:** PAT exchanged for JWT — secret binding: `pat`

## Scope

One node for **Docker Hub** (the hosted registry **management** API). Authenticate with a Personal Access Token, exchange it for a short-lived JWT, then call Hub REST.

**This is not Docker Engine.** Never connect to `unix:///var/run/docker.sock`, `localhost:2375`, or the Engine/Moby API. Never call `index.docker.io` Registry HTTP API unless a future catalog row is explicitly Registry — this node is Hub `hub.docker.com/v2` only. No Hub HTML scrape.

### Re-verified drops (2026-08-20)

| Catalog verb | Finding | Action |
|---|---|---|
| `tag_delete` | Official Hub OpenAPI lists **GET/HEAD** on `/v2/namespaces/{ns}/repositories/{repo}/tags/{tag}` only. Advanced Image Management delete routes were **retired** (`/images`, `/delete-images`). Tag delete in product docs is **Hub UI**. | **Dropped.** Do not call undocumented DELETE. |
| `webhook_set` | Official Hub OpenAPI has **no** webhook CRUD. Webhooks are created in the Hub **Webhooks** tab. | **Dropped.** |
| `listen` | Hub POSTs push events to a URL the operator pastes in the UI. | Keep as **ingress documentation + optional poll** of `tag_list` (limitation). |

File payloads: N/A (image **metadata** only; no image blob upload/download).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| *(auth, internal)* | POST | `/v2/auth/token` | Exchange `identifier`+`secret` (username+PAT) for JWT. Prefer this over deprecated `POST /v2/users/login`. |
| `repo_get` | GET | `/v2/namespaces/{namespace}/repositories/{repository}` | One repository |
| `repo_list` | GET | `/v2/namespaces/{namespace}/repositories` | List repos in a namespace (`page`, `page_size` max 100) |
| `tag_list` | GET | `/v2/namespaces/{namespace}/repositories/{repository}/tags` | List tags |
| `listen` | GET (poll) or none | same as `tag_list`, or no Hub call | Poll tags for new names **or** instruct operator to point Hub webhook at StudioQuack message-event ingress |

Deprecated login: `POST /v2/users/login` — **do not use** (OpenAPI marks deprecated; PAT-in-body to `/v2/auth/token` is current).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `repo_get` | no | Chooses a public operation |
| `username` | string | yes | empty | no | Hub username (`identifier` on token exchange) |
| `pat` | password | yes | empty | yes | Personal Access Token (`secret` on token exchange). Never on INPUT. Never log. |
| `timeout_seconds` | number | no | `25` | no | Connect + request timeout |

Do not store the JWT in configuration. Cache JWT **in-memory for the current invoke only**; on 401, re-exchange once.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | namespace/repo/tag fields |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No PAT/JWT |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = repo_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `namespace` | string | yes | Hub namespace (user or org) |
| `repository` | string | yes | Repo name (not `ns/repo` in one field) |

### `operation = repo_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `namespace` | string | yes | Namespace to list |
| `page` | number | no | Default 1 |
| `page_size` | number | no | Default 10, max 100 |

### `operation = tag_list` | `listen` (poll mode)

| Field | Type | Required | Purpose |
|---|---|---|---|
| `namespace` | string | yes | Namespace |
| `repository` | string | yes | Repository |
| `page` | number | no | Default 1 |
| `page_size` | number | no | Default 10, max 100 |
| `since_tag` | string | no | `listen` poll: return tags newer than this name if Hub provides last_updated sort; else return page and let the workflow diff |

### `operation = listen` (webhook mode)

No Hub HTTP. Output documents the expected push payload (`push_data.tag`, `repository.repo_name`) from https://docs.docker.com/docker-hub/repos/manage/webhooks/ . Operator creates the webhook **in Hub UI** targeting StudioQuack message-event ingress. Hub does not HMAC-sign deliveries (`callback_url` is legacy/unsupported).

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `repository` | object | `repo_get` | name, namespace, description, is_private, star_count, pull_count |
| `repositories` | array | `repo_list` | Summaries + pagination `count`/`next` |
| `tags` | array | `tag_list`, poll `listen` | name, last_updated, digest if present |
| `webhook_setup` | object | webhook `listen` | `{ "configure_in": "Docker Hub UI Webhooks tab", "payload_fields": ["push_data.tag", "repository.repo_name"] }` |
| `error` | string | failure | No PAT/JWT |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After`; else 30–60 + jitter |

## Legal

- Hub API: https://docs.docker.com/docker-hub/api/latest/
- Token exchange example: https://docs.docker.com/docker-hub/repos/manage/export/
- Webhooks (UI): https://docs.docker.com/docker-hub/repos/manage/webhooks/
- Deprecated v1 Hub routes return 410: https://docs.docker.com/reference/api/hub/deprecated/
- Docker Subscription / Hub terms apply to the operator’s account. Do not redistribute private image metadata.
- Rate limits: not a single published number on the OpenAPI; honor 429.

## Implementation notes

- JDK HTTP only. TLS on. Base `https://hub.docker.com`.
- Auth flow: POST `/v2/auth/token` JSON `{ "identifier": username, "secret": pat }` → `access_token` → `Authorization: Bearer {jwt}` on subsequent calls. Do not send PAT as Bearer. Do not use account password.
- Headers: `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Body cap **8 MiB**. 429 retry with jitter (max 3).
- **Never** open a Unix socket. **Never** call Engine `/v1.41/` APIs.
- Logs: operation, namespace, repo, HTTP status — never PAT, JWT, or webhook callback secrets.
- Errors → `{ error }` on OUTPUT.
