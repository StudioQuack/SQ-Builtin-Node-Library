# ntfy

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/ntfy/`
- **Researched:** 2026-08-20
- **Official API:** ntfy HTTP pub/sub API (publish + subscribe)
- **Docs:** https://docs.ntfy.sh/publish/ · subscribe https://docs.ntfy.sh/subscribe/api/ · auth https://docs.ntfy.sh/publish/#access-tokens
- **Auth:** none \| bearer \| basic — secret bindings: `access_token` (bearer) or `username`+`password` (basic)

## Scope

One node for the **official ntfy HTTP API**. Publish with PUT/POST to `{base_url}/{topic}`. Subscribe (`listen`) on the **same node** via JSON stream, SSE, or WebSocket — never a sibling trigger node.

Topics on the public host are **guessable secrets**. ntfy’s own terms: topic names on ntfy.sh are public; without access control the topic **is** the password. For production traffic to **ntfy.sh**, this node **requires** `auth=bearer` (or `basic`) and a non-empty credential. `auth=none` is allowed only when `base_url` is a customer-hosted origin (not `ntfy.sh` / `*.ntfy.sh`).

Attachments: PUT/POST file body or `X-Attach` URL. Platform type `file` for bytes uploaded to ntfy. Hosted ntfy.sh attachment cap is **2 MB** (self-host default often 15 MB). Message text max **4096** bytes; longer bodies become attachments.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `publish` | PUT or POST | `/{topic}` | Send a notification (JSON body or headers + body) |
| `listen` | GET | `/{topic}/json` (default), `/{topic}/sse`, `/{topic}/ws` | Subscribe; JSON NDJSON recommended for JDK |

Base: config `base_url` (default `https://ntfy.sh`). Strip trailing slash. Topic: `[-_A-Za-z0-9]`, max 64 characters.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `publish` | no | Chooses the row above |
| `base_url` | string | no | `https://ntfy.sh` | no | ntfy.sh or self-host origin (`https://ntfy.example.com`). SSRF: allow only https (http only if operator explicitly sets `allow_insecure_http=true` on self-host) |
| `topic` | string | yes | empty | **yes if auth=none** | Default topic. Treat as secret when unauthenticated. Never log |
| `auth` | select | yes | `bearer` | no | `none` \| `bearer` \| `basic` |
| `access_token` | password | if `auth=bearer` | empty | yes | `Authorization: Bearer tk_…`. Never on INPUT |
| `username` | string | if `auth=basic` | empty | no | Basic user |
| `password` | password | if `auth=basic` | empty | yes | Basic password. Empty user + token-as-password is ntfy’s token-via-basic form — prefer `auth=bearer` |
| `allow_insecure_http` | boolean | no | `false` | no | Permit `http://` **self-host only**. Always false for ntfy.sh |
| `timeout_seconds` | number | no | `25` | no | `publish` timeout |
| `listen_idle_seconds` | number | no | `300` | no | `listen` stream idle cap. Floor 30, cap 3600 |
| `max_attach_bytes` | number | no | `2097152` | no | Default 2 MiB (ntfy.sh). Raise for self-host if the server allows (docs default 15 MiB) |

**Hard rule:** if the host of `base_url` is `ntfy.sh` or ends with `.ntfy.sh`, `auth=none` → `{ error: "ntfy.sh public topics are guessable; set auth=bearer and access_token." }` — no HTTP.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object | Topic override allowed; credentials never on INPUT |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | No tokens, no topic if treating topic as secret (log length only) |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic` | string | if config blank | Override config topic |
| `message` | string | no | Body (required unless `file` is set). Max 4096 bytes as text |
| `title` | string | no | `X-Title` / JSON `title` |
| `priority` | number or string | no | 1–5 or `min`/`low`/`default`/`high`/`urgent` → `X-Priority` |
| `tags` | string or array | no | Comma-separated or JSON array → `X-Tags` |
| `click` | string | no | URL opened on tap (`X-Click`) |
| `delay` | string | no | Scheduled delivery (`X-Delay`), e.g. `30m` or Unix ts — server-side delay |
| `email` | string | no | `X-Email` forward (ntfy.sh ToS: no spam) |
| `icon` | string | no | Icon URL (`X-Icon`) |
| `actions` | string or array | no | Action buttons as documented (header or JSON) |
| `markdown` | boolean | no | `X-Markdown: true` when supported |
| `cache` | string | no | `yes` \| `no` (`X-Cache`) |
| `firebase` | string | no | `yes` \| `no` (`X-Firebase`) |
| `filename` | string | no | `X-Filename` for attachments |
| `file` | file | no | Bytes PUT/POST as the body with `X-Filename`. Mutually exclusive with `attach_url` |
| `attach_url` | string | no | `X-Attach` public URL (externally hosted; ntfy size limits do not apply to the URL itself) |

Prefer JSON publish (`Content-Type: application/json`) when there is no `file` body:

```json
{ "topic": "…", "message": "…", "title": "…", "priority": 4, "tags": ["warning"] }
```

When `file` is set: PUT the file bytes to `/{topic}` with headers for title/message/filename. Do not also JSON-encode the file.

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `topic` | string | if config blank | One topic or comma-separated list (official multi-topic subscribe) |
| `stream` | string | no | `json` (default) \| `sse` \| `websocket`. Do not use `raw` (drops metadata) |
| `poll` | boolean | no | `true` → `poll=1`, return cached messages and close |
| `since` | string | no | Duration, Unix ts, message id, `all`, or `latest` |
| `scheduled` | boolean | no | `scheduled=1` include delayed messages (with poll) |
| `filter_priority` | string | no | Comma-separated priorities (OR) |
| `filter_tags` | string | no | Comma-separated tags (AND) |

Default `listen` is a long-lived GET `{base_url}/{topic}/json` (NDJSON). Emit only `event=message` (and optional `message_delete` / `message_clear` if present). Drop `open` and `keepalive` from OUTPUT.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `id` | string | publish / listen message | ntfy message id |
| `time` | number | when present | Unix timestamp |
| `expires` | number | when present | |
| `topic` | string | when present | Do not log |
| `message` | string | message events | |
| `title` | string | when present | |
| `priority` | number | when present | |
| `tags` | array | when present | |
| `click` | string | when present | |
| `attachment` | object | when present | `{ name, url, type, size, expires }` — `url` is ntfy’s file URL, not a StudioQuack `file` ref unless the handler copies (v1: pass through) |
| `messages` | array | `listen` + `poll=true` | Cached messages |
| `error` | string | failure | No credentials. 401/403: auth/ACL |
| `http_status` | number | HTTP error | |
| `retry_after_seconds` | number | 429 / 5xx | Honor `Retry-After`; ntfy.sh rate-limits anonymous and free tiers |

## Legal

- Hosted ToS: https://docs.ntfy.sh/terms/ (updated 2026-01-26). Applies to ntfy.sh, not self-host.
- Privacy: https://docs.ntfy.sh/privacy/ (confirm live).
- **Public topics:** anyone who guesses the topic can publish and subscribe. Production on ntfy.sh must use access tokens / reserved topics (paid) or self-host with ACL.
- Acceptable use: no spam, no malware, no circumvention of rate limits. Message length 4096; ntfy.sh attachment 2 MB / 20 MB per-visitor; 200 MB attachment bandwidth/day (docs 2026-08-20).
- Self-host: customer is the data controller; operator DPA is with themselves. Still redact tokens and topic names in logs.
- Notification content may be PII. FCM/APNs may see payloads on mobile push.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`.
- Auth: `Authorization: Bearer {access_token}` or `Authorization: Basic …`. Do **not** put tokens in `?auth=` query (they leak to logs and proxies) unless a client cannot set headers — this runtime can.
- Host check for ntfy.sh before any request.
- SSRF: parse `base_url`; deny non-http(s), deny cloud metadata IPs, deny DNS rebinding. `allow_insecure_http` does not bypass metadata blocks.
- `listen` JSON: `Content-Type: application/x-ndjson`; read lines until idle timeout. SSE: parse `data:` lines. WebSocket: `wss://` + `/{topic}/ws`.
- File publish: cap `max_attach_bytes`; resolve `file` ref; set `X-Filename`.
- Body cap on publish response **64_000**. Listen: cap each JSON line (e.g. 1 000 000) then skip/error.
- Logs: operation, HTTP status, sizes — never token, password, topic string, message body.
- **Do not build:** account REST (signup, billing), email publishing SMTP, phone-call notifications, `raw` subscribe as a first-class stream, a separate trigger node.
