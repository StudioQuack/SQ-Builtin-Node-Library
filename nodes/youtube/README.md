# YouTube Search

In-process StudioQuack builtin (`templateId` `5fa127c0-dceb-4397-8432-2cfe24beeb19`, env `SQ_TEMPLATE_ID_YOUTUBE`) that calls the official **YouTube Data API v3**. Handler: `YoutubeNode`. One invoke runs one action against `https://www.googleapis.com/youtube/v3`.

This node is **read-only**. It does not post comments, upload videos, or edit playlists. Those writes need a separate OAuth 2.0 user-consent node; they are not sandbox stubs here — they are not implemented.

Provider docs: [YouTube Data API v3](https://developers.google.com/youtube/v3/docs). Quota units: [quota calculator](https://developers.google.com/youtube/v3/determine_quota_cost).

## 1. Overview

| Action | Status | HTTP | Notes |
|---|---|---|---|
| `search` (default) | **Implemented** | `GET /search?part=snippet` | Full-text search. Since 2026-06-01: own **Search Queries** bucket, default **100 calls/day**, 1 unit per call (including extra pages). |
| `video_details` | **Implemented** | `GET /videos?part=snippet,statistics,contentDetails` | First 50 ids. **1 unit**. |
| `channel_info` | **Implemented** | `GET /channels?part=snippet,statistics,brandingSettings` | Unknown id is still `status=ok` with `data.error`. **1 unit**. |
| `trending` | **Implemented** | `GET /videos?chart=mostPopular` | Regional chart. **1 unit**. |
| Comment / upload / playlist write | **Not implemented** | — | Needs OAuth 2.0 user consent. Not a stub. |

There is no `sandbox` mode. Every successful action is a live Google call. The API key is sent only as query `key=` (never an `Authorization` header, never on the INPUT port).

Default allocation (since 2026-06-01): **100 `search.list` calls/day**, **100 `videos.insert` calls/day**, and **10,000 units/day** for all other methods. Resets midnight Pacific. [Quota calculator](https://developers.google.com/youtube/v3/determine_quota_cost).

## 2. Ports

This template has two ports. There is no error OUTPUT port. Failures set `output.error` on the same OUTPUT port. The handler never uses a FUNCTION / helper channel.

| Port | Direction | Type | Required | Data flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Previous node → `inputVariables.input` (or `currentNode.inputList` with `targetPortValue=input`). A one-element list is unwrapped to the first object. Missing/non-object → `{}`, then the action’s required-field check fails. |
| `output` | OUTPUT | object | yes | Handler reply key `output` only. Success: `status=ok` plus action fields. Failure: `{error}` only. Never contains `api_key`. |

### INPUT `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `action` | string | no | `search` | Case-sensitive: `search`, `video_details`, `channel_info`, `trending`. Blank/whitespace → `search`. | Selects the YouTube method. |
| `query` | string | search unless `q` | — | Any non-empty Unicode (YouTube search syntax forwarded as-is). | Search string sent as `q=`. Wins over `q`. |
| `q` | string | search unless `query` | — | Same as `query`. | Alias of `query` when `query` is blank. |
| `order` | string | no | `relevance` | YouTube: `relevance`, `date`, `rating`, `viewCount`, `title`. Other strings are forwarded (HTTP 400 likely). | `search` sort. Ignored otherwise. |
| `maxResults` | number | no | `25` | Integer. Capped at `min(n, 50)`. No lower bound (`0` is forwarded). | Page size for `search` and `trending`. |
| `type` | string | no | `video` | `video`, `channel`, `playlist`. | `search.list` type. When `channel`/`playlist`, `video_id`/`url` on a row may be empty. |
| `publishedAfter` | string | no | omitted | RFC 3339, e.g. `2026-01-01T00:00:00Z`. | Inclusive lower bound. Search only. |
| `publishedBefore` | string | no | omitted | RFC 3339. | Exclusive upper bound. Search only. |
| `channelId` | string | no | omitted | `UC…` channel id. | Restrict search to one channel. Wins over `channel_id` for search. |
| `channel_id` | string | `channel_info`; search fallback | — | `UC…` channel id. | Required for `channel_info`. Search-scope fallback when `channelId` is blank. |
| `pageToken` | string | no | omitted (first page) | Opaque token from `output.next_page_token`. | Search pagination. |
| `regionCode` | string | no | `US` for trending; omitted for search | ISO 3166-1 alpha-2 (`US`, `GB`, …). | Trending region; optional search filter. |
| `relevanceLanguage` | string | no | `en` | ISO 639-1. Always sent on search. | Search language boost, not a hard filter. |
| `videoDuration` | string | no | omitted | `short`, `medium`, `long` (`any` or blank = not sent). | Search duration filter (`type=video` only). |
| `video_ids` | array | `video_details` unless `video_id` | — | JSON array of strings, comma-separated string, or JSON-stringified array. Extra ids past 50 are dropped. | `videos.list` ids. |
| `video_id` | string | `video_details` unless `video_ids` | — | Typical 11-character video id. | Single-id fallback when `video_ids` is empty. |
| `categoryId` | string | no | omitted | YouTube category id (`10` Music, `1` Film, `20` Gaming). | Trending `videoCategoryId`. |

Do not put `api_key` on this port. The handler does not read it from input.

### OUTPUT `output` schema

| Field | Type | When present | Purpose |
|---|---|---|---|
| `status` | string | success | Exact value `ok`. Also `ok` when `channel_info` finds no channel. |
| `action` | string | success | Echo: `search` \| `video_details` \| `channel_info` \| `trending`. |
| `query` | string | `search` | Echo of the string sent as `q=`. |
| `videos_returned` | number | search / details / trending | Length of this page’s `videos[]`. |
| `total_results` | number | `search` | `pageInfo.totalResults` (YouTube estimate). Missing → `0`. |
| `next_page_token` | string \| null | `search` | Pass back as `input.pageToken`. JSON `null` on the last page. |
| `videos` | array | search / details / trending | Normalized rows (see below). Descriptions capped at 500 characters. |
| `data` | object | `channel_info` | Channel fields. An unknown id raises `HTTP_404`. |
| `region` | string | `trending` | Echoed `regionCode` (default `US`). |
| `error` | string | failure only | No other success keys. Never includes the API key (URLs sanitized). |

**`videos[]` row fields** (subset depends on action):

| Field | Type | Actions | Notes |
|---|---|---|---|
| `video_id` | string | all three | Search: `id.videoId` (empty for channel/playlist hits). |
| `title` | string | all three | `snippet.title`. |
| `description` | string | all three | Capped at 500 chars. |
| `channel_title` | string | all three | `snippet.channelTitle`. |
| `channel_id` | string | search, details | Absent on trending rows. |
| `published_at` | string | all three | RFC 3339. |
| `thumbnail` | string | search | `thumbnails.high.url`. |
| `url` | string | all three | `https://www.youtube.com/watch?v={video_id}` (empty on search when id is empty). |
| `tags` | array | details | First 20 `snippet.tags`. |
| `category_id` | string | details | `snippet.categoryId`. |
| `view_count` | number | details, trending | 64-bit. Missing → `0`. |
| `like_count` | number | details, trending | Hidden likes → `0`. |
| `comment_count` | number | details, trending | Disabled comments → `0`. |
| `duration` | string | details, trending | ISO 8601, e.g. `PT3M33S`. |
| `definition` | string | details | Typically `hd` or `sd`. |

**`data` (channel_info hit):** `channel_id`, `title`, `description` (500 cap), `custom_url`, `published_at`, `country`, `subscriber_count`, `video_count`, `view_count` (counts are 64-bit). Branding settings are requested but not mapped onto the payload.

## 3. Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
|---|---|---|---|---|---|
| `api_key` | password | **yes** | (empty) | Google Cloud API key with YouTube Data API v3 enabled. | Query `key=` on every request. Blank → `api_key is required. Get one from Google Cloud Console with YouTube Data API v3 enabled.` |
| `timeout_seconds` | number | no | `20` | Positive integer. `≤0` / non-numeric → `20`. | Per-call HTTP connect+read timeout. |

Obtain the key: [Google Cloud Console](https://console.cloud.google.com/) → enable **YouTube Data API v3** → Credentials → API key. Restrict the key to that API and to the daemon’s egress IPs when you can.

## 4. How it works

1. **Dispatch.** `SocialNodeHandlers` registers `YoutubeNode` under `SQ_TEMPLATE_ID_YOUTUBE` or bundled id `5fa127c0-dceb-4397-8432-2cfe24beeb19`. The engine routes the invoke by `templateId`.
2. **Config.** `SocialSupport.nodeParams` reads `configurationParameters` by `shortCode`. `SocialSupport.inputPort` unwraps `input`.
3. **Auth.** `keyed()` puts `api_key` in the query map as `key`. `SocialHttp.getJson` builds the URI. No `Authorization` header.
4. **Action switch.** Case-sensitive `action` (default `search`) selects `search` / `video_details` / `channel_info` / `trending`. Unknown → `output.error` listing those four names. No HTTP.
5. **Pagination.** Search only. First page omits `pageToken`. If `next_page_token` is a string, send it back unchanged as `pageToken`. Each extra page counts as another `search.list` call against the Search Queries bucket.
6. **Errors.** `handle` never throws. Validation and HTTP failures map to `{error}` on port `output`:
   - HTTP 403 → message plus `API key may be invalid or YouTube Data API v3 not enabled in Google Cloud Console.`
   - HTTP 429 → plus `Daily quota exceeded (10,000 units/day on free tier).`
   - Other `ApiException` → `YouTube API Error: HTTP {status}: {sanitized provider message}`.
   - Unexpected → `Error: {SimpleName}: {sanitized message}`.
7. **Rate limits.** No retry/backoff in this handler. Google quota is daily units, not a sleep loop. Bound payloads: `SocialHttp` rejects bodies over 5 MiB; descriptions/tags are capped as above.
8. **Sanitization.** `SocialHttp.sanitize` redacts `key=` / token query params and strips absolute URLs from error strings so the API key cannot appear on `output.error`.

## 5. Security & legal

- Store `api_key` only in `configurationParameters` (password). Never on an edge, in a FUNCTION payload, or in a log node.
- The handler never logs `api_key` or `helperToken`. Errors are sanitized.
- Restrict the Google Cloud key to YouTube Data API v3 and known egress IPs.
- **YouTube API Services Terms of Service:** [developers.google.com/youtube/terms/api-services-terms-of-service](https://developers.google.com/youtube/terms/api-services-terms-of-service)
- **Google APIs Terms of Service:** [developers.google.com/terms](https://developers.google.com/terms)
- Do not use this node to scrape, cache, or republish YouTube content beyond what those terms allow. Quota abuse and required attribution (YouTube branding) are the customer’s responsibility.
- **GDPR:** search hits, channel metadata, and descriptions can identify a natural person (channel owner, on-video speech in titles). Process only the fields the workflow needs; do not persist full result pages “just in case”; honor erasure when a workflow store holds that data.

## 6. Examples

### `search`

Input:

```json
{
  "action": "search",
  "query": "studioquack workflow",
  "order": "date",
  "maxResults": 10,
  "type": "video",
  "relevanceLanguage": "en"
}
```

Output:

```json
{
  "status": "ok",
  "action": "search",
  "query": "studioquack workflow",
  "videos_returned": 1,
  "total_results": 1280,
  "next_page_token": "CAUQAA",
  "videos": [
    {
      "video_id": "dQw4w9WgXcQ",
      "title": "Example title",
      "description": "…",
      "channel_title": "Example Channel",
      "channel_id": "UC_x5XG1OV2P6uZZ5FSM9Ttw",
      "published_at": "2026-08-01T12:00:00Z",
      "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
      "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    }
  ]
}
```

Next page: same input plus `"pageToken": "CAUQAA"`.

### `video_details`

Input: `{"action":"video_details","video_ids":["dQw4w9WgXcQ"]}` (or `"video_id":"dQw4w9WgXcQ"`).

Output includes `view_count`, `like_count`, `comment_count`, `duration`, `definition`, `tags`, `category_id` on each row.

### `channel_info`

Input: `{"action":"channel_info","channel_id":"UC_x5XG1OV2P6uZZ5FSM9Ttw"}`.

Hit: `data` with `subscriber_count` / `video_count` / `view_count`. Miss: BPMN error `HTTP_404` "Channel not found: UC…".

### `trending`

Input: `{"action":"trending","regionCode":"US","maxResults":10,"categoryId":"10"}`.

Output: `status`, `action=trending`, `region=US`, `videos_returned`, `videos[]` (no `channel_id` / `thumbnail` / `tags` / `definition` on those rows).
