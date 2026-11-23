# Spotify

- **Status:** planned
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/spotify/`
- **Researched:** 2026-08-20
- **Official API:** Spotify Web API (`https://api.spotify.com/v1`)
- **Docs:** https://developer.spotify.com/documentation/web-api · [February 2026 changelog](https://developer.spotify.com/documentation/web-api/references/changes/february-2026) · [Feb 2026 migration](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide)
- **Auth:** Bearer `access_token` minted outside StudioQuack (`refresh_token` is unused; engine has no OAuth)

## Scope

One node for the Spotify catalog and the authenticated user’s playlists/player. **February 2026 Web API changes are in force:** search `limit` max **10** (default **5**); playlist item endpoints are `/playlists/{id}/items` (not `/tracks`); batch `GET /tracks` is gone — use `GET /tracks/{id}` per track; several user playlist list/create-by-user-id routes were removed (use current `/me/playlists` where still published). Spec **only** live endpoints.

There is **no** official Spotify webhook for player or playlist events comparable to EventSub. Catalog `trigger` is **omitted** (not “later”). Do not poll-scrape open.spotify.com. Cover image upload is type `file` (JPEG, max **256 KB** after encode).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `search` | GET | `/v1/search` | Catalog search. Clamp `limit` to 1–**10**, default **5** |
| `track_get` | GET | `/v1/tracks/{id}` | One track (not batch `/v1/tracks`) |
| `playlist_get` | GET | `/v1/playlists/{playlist_id}/items` | Playlist items for playlists the user **owns or collaborates on** (post-Feb 2026). Metadata: GET `/v1/playlists/{playlist_id}` if still in the live reference — if 404/410, items-only |
| `playlist_add` | POST | `/v1/playlists/{playlist_id}/items` | Add up to 100 URIs (`playlist-modify-public` / `playlist-modify-private`) |
| `playlist_cover` | PUT | `/v1/playlists/{playlist_id}/images` | Custom cover. Body JPEG. Input type `file`. Scopes `ugc-image-upload` + playlist modify |
| `player_state` | GET | `/v1/me/player` | Current playback (`user-read-playback-state`). HTTP **204** = no active device |

OAuth: `https://accounts.spotify.com/authorize` and `https://accounts.spotify.com/api/token`. Development Mode (from Feb/Mar 2026 platform posts): Premium owner, tight user caps, reduced endpoint set — operator must use a Client ID that still has these endpoints.

**Do not call (removed / replaced):** `POST|GET|PUT|DELETE /playlists/{id}/tracks`; `GET /tracks` (batch); `GET /users/{id}/playlists` and `POST /users/{id}/playlists` if the changelog marks them removed — use published `/me/playlists` only if still listed.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `refresh_token` | password | yes | empty | yes | User refresh token for user-scoped ops |
| `access_token` | password | no | empty | yes | Optional Bearer; refresh on 401 |
| `timeout_seconds` | number | no | `25` | no | Floor 1 s |
| `default_market` | string | no | empty | no | ISO 3166-1 alpha-2 `market` |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. Cover image is `file`, not base64 on the port. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | 204 player → `status: ok`, `is_playing: false`, empty device |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `query` | string | yes | `q`. Empty → `{ error }` no HTTP |
| `type` | string | yes | Comma-separated: `album`, `artist`, `playlist`, `track`, `show`, `episode`, `audiobook` as documented |
| `limit` | number | no | Default **5**, max **10**. Clamp; do not send 50 |
| `offset` | number | no | 0–1000 for further pages |
| `market` | string | no | Else config `default_market` |

### `operation = track_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `id` | string | yes | Spotify track id (not a comma-separated batch) |
| `market` | string | no | Market |

### `operation = playlist_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `playlist_id` | string | yes | Playlist id |
| `limit` | number | no | Items page: default 20, max **50** (items endpoint, not search) |
| `offset` | number | no | Items offset |
| `market` | string | no | Market |
| `fields` | string | no | Sparse fields if the items endpoint still documents `fields` |

Response field rename: playlist objects use **`items`**, not `tracks`, after February 2026. Normalize OUTPUT to `items`.

### `operation = playlist_add`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `playlist_id` | string | yes | Playlist |
| `uris` | array | yes | Spotify URIs (`spotify:track:…` / episode). Max **100**. Send JSON body `{ "uris": [...], "position": n }` — do not put a huge list on the query string |
| `position` | number | no | Zero-based insert index |

### `operation = playlist_cover`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `playlist_id` | string | yes | Playlist |
| `file` | file | yes | JPEG image ref. Handler reads bytes, enforces **≤ 256 KB**, `Content-Type: image/jpeg`, PUT raw body (Spotify expects JPEG bytes; if they still require base64, follow the live reference — the 2026-08-20 page showed raw JPEG in cURL `--data`). |

Reject non-JPEG or > 256 KB with `{ error }` no HTTP.

### `operation = player_state`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `market` | string | no | Market |
| `additional_types` | string | no | `track,episode` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx or player 204 | `ok` |
| `results` | object | `search` | Provider paging objects per `type` (capped arrays of `{ id, name, uri, type }`) |
| `track` | object | `track_get` | `{ id, name, uri, duration_ms, artists }` |
| `items` | array | `playlist_get` | Playlist items (`item` / track or episode) |
| `snapshot_id` | string | `playlist_add` / `playlist_get` | Playlist version |
| `is_playing` | boolean | `player_state` | false on 204 |
| `device` | object | `player_state` 200 | `{ id, name, type, volume_percent }` |
| `progress_ms` | number | `player_state` 200 | Progress |
| `item` | object | `player_state` 200 | Current track/episode `{ id, name, uri, type }` |
| `error` | string | failure | Actionable, no token |
| `http_status` | number | HTTP error | Including 204 handled as success for player |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` (required on 429). Else 60 / 30 |

## Legal

- [Web API](https://developer.spotify.com/documentation/web-api) · [Developer Policy / Terms](https://developer.spotify.com/terms) (confirm live) · [Rate limits](https://developer.spotify.com/documentation/web-api/concepts/rate-limits) · [February 2026 changes](https://developer.spotify.com/documentation/web-api/references/changes/february-2026)
- Rate limit: rolling **30-second** window, **no published fixed QPS**. 429 + `Retry-After`. Playlist image upload may have a tighter custom limit.
- Platform policy (player docs): no commercial streaming app, no altering audio, no A/V sync, no non-interactive broadcast. This node returns **metadata and state**, not a licensed playback stream.
- Do not persist full catalog dumps. User listening state is PII.
- Development Mode restrictions (Premium, user caps, reduced endpoints) from Spotify’s Feb 2026 developer-access posts apply to many Client IDs.

## Implementation notes

- JDK `HttpClient` only. TLS verified. `Redirect.NEVER`. No Spotify SDK.
- `Authorization: Bearer {access_token}`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`.
- Token URL `https://accounts.spotify.com/api/token`. One refresh on 401.
- Empty `access_token` → `{ error }` — no HTTP. Mint the OAuth token outside StudioQuack.
- Search: if input `limit` > 10, clamp to 10 (do not send illegal values).
- JSON cap **1_000_000** bytes. Cover PUT cap **256_000** bytes. Timeout 25 s.
- No in-handler retry loop; surface `Retry-After`.
- Logs: operation, status, counts, snapshot_id — never tokens, never image bytes, never search query text beyond length.
