# Spotify

Official [Spotify Web API](https://developer.spotify.com/documentation/web-api). Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://developer.spotify.com/documentation/web-api · [Search](https://developer.spotify.com/documentation/web-api/reference/search) · [Feb 2026 changelog](https://developer.spotify.com/documentation/web-api/references/changes/february-2026)
- **Base:** `https://api.spotify.com/v1`
- **Auth:** Bearer `access_token` minted outside StudioQuack. OfficialApiNode **only** sends `access_token`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `search` | GET | `/search` query `q`, `type`, `limit`, `market` |
| `track_get` | GET | `/tracks/{track_id}` |
| `playlist_get` | GET | `/playlists/{playlist_id}` |
| `playlist_add` | POST | `/playlists/{playlist_id}/items` body `uris` |
| `player_state` | GET | `/me/player` |
| `trigger` | ingress | BPMN message event — no socket |

**Search (Feb 2026):** `limit` default **5**, maximum **10**. This node does not clamp; Spotify returns 400 above 10. Paginate with `offset` only if a later spec adds that query key.

**Playlists:** add-items path is `/items` (not `/tracks`). Send `uris` as a JSON array of Spotify URIs.

**Player:** `GET /me/player` needs scope `user-read-playback-state`. HTTP 204 = nothing playing. Spotify has **no first-party webhook** for playback; `trigger` still maps to StudioQuack message-event ingress (poll `player_state` or catch an external message).

`playlist_add` needs `playlist-modify-public` or `playlist-modify-private`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer (the only auth the engine sends) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Legal

- Customer owns the Spotify app and user grant. Development Mode has extra caps (Premium, user cap, endpoint set) — read the Feb 2026 notes.
- [Developer Terms](https://developer.spotify.com/terms) · [Developer Policy](https://developer.spotify.com/policy)
- Official Web API only. Do not scrape open.spotify.com.
