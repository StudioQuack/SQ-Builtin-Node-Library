# Product Hunt Search

In-process StudioQuack builtin (`templateId` `83226eb9-3d21-4b15-8404-33ef438b2256`, override with `SQ_TEMPLATE_ID_PRODUCTHUNT`). Handler: `com.studioquack.nodelibrary.nodes.data.ProducthuntNode`.

## Overview

The node discovers Product Hunt launches through the **official Product Hunt GraphQL API v2**. It is read-only: one `posts` query per invoke. It does not create products, vote, or comment.

Auth is customer-owned. Create an application at [producthunt.com/v2/oauth/applications](https://www.producthunt.com/v2/oauth/applications) and store credentials on the instance. The handler resolves a bearer **per invoke** and never caches it (tenant isolation).

Provider docs:

- GraphQL endpoint: `https://api.producthunt.com/v2/api/graphql`
- OAuth token: `https://api.producthunt.com/v2/oauth/token`
- Applications / Developer Token: [producthunt.com/v2/oauth/applications](https://www.producthunt.com/v2/oauth/applications)
- API terms / fair use: [Product Hunt Terms](https://www.producthunt.com/legal/terms-of-service), [GraphQL API docs](https://api.producthunt.com/v2/docs) (published budget about **450 requests / 15 minutes**)

Needs outbound HTTPS egress to `api.producthunt.com`. `jobType` is `custom-script-executor`.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | GraphQL search variables. Empty object still runs `order=NEWEST` with `first=min(default_limit, 20)`. Credentials are **not** on this port. |
| `output` | OUTPUT | object | yes | Success `{status, posts_returned, has_next_page, end_cursor, posts[]}` or `{error}`. Secrets redacted to `***`. |

`DataSupport.readInput` unwraps `inputVariables.input`. Reply is always keyed by `output`. No separate error port.

### `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `posted_after` | string | no | omitted | ISO 8601 DateTime (typically UTC with `Z`) | GraphQL `postedAfter`. Blank → not sent. |
| `posted_before` | string | no | omitted | ISO 8601 DateTime | GraphQL `postedBefore`. Combine with `posted_after` for a window. |
| `topic` | string | no | omitted | Product Hunt topic **slug** (kebab-case), not a display name | GraphQL `topic`. Unknown slugs usually yield an empty page, not a transport error. |
| `order` | string | no | `NEWEST` | `NEWEST` \| `VOTES` \| `RANKING` (case-sensitive `PostsOrder!`) | Sort. Other strings are forwarded and typically return GraphQL errors. Do not send `newest`. |
| `limit` | number | no | instance `default_limit` (20) | integer; handler sends `min(limit or default_limit, 20)` | GraphQL `first`. Product Hunt caps a page at 20; page with `after`. `0` / blank → `default_limit`. |
| `after` | string | no | omitted (first page) | opaque `end_cursor` from the previous ok response | GraphQL `after`. Do not invent cursors. |

### `output` schema

| Field | Type | When present | Purpose |
| --- | --- | --- | --- |
| `status` | string | success | Always `ok` (including `posts_returned=0`). |
| `posts_returned` | number | success | `posts.length` (0–20). |
| `has_next_page` | boolean | success | GraphQL `pageInfo.hasNextPage` (`false` if missing). |
| `end_cursor` | string or null | success | GraphQL `pageInfo.endCursor`; pass as next `input.after`. |
| `posts` | array | success | Flattened launches (see below). |
| `error` | string | failure only | Auth required, `GraphQL errors: …`, `Product Hunt API Error: …`, or `Error: ClassName: …`. |

Each `posts[]` item: `id`, `name`, `tagline`, `description` (first **500** chars of `node.description`), `votes_count`, `comments_count`, `website`, `url`, `created_at`, `makers[]` (name strings), `topics[]` (`{name, slug}`).

## Configuration parameters

| Name | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `developer_token` | password | no* | `""` | Developer Token from the application dashboard (Generate Token once) | **Auth priority 1.** Non-blank → `Authorization: Bearer` on GraphQL only. Skips OAuth and ignores `api_key` / `api_secret`. Recommended: avoids Cloudflare on `/v2/oauth/token`. |
| `api_key` | password | no* | `""` | API Key / OAuth `client_id` | Used only when `developer_token` is empty. **Priority 2:** if `api_secret` is empty, `api_key` is sent as Bearer. **Priority 3:** with `api_secret`, POSTed as `client_id` to the token endpoint. |
| `api_secret` | password | no* | `""` | API Secret / OAuth `client_secret` | With `api_key` and no `developer_token`, triggers `client_credentials` OAuth (up to 3 attempts). Empty + `api_key` set → api_key-as-bearer. |
| `timeout_seconds` | number | no | `25` | positive integer. non-positive / blank → 25 | Per-request timeout for OAuth POST and GraphQL POST. Backoff sleeps (2s, 4s, 6s) are extra wall time. |
| `default_limit` | number | no | `20` | positive integer. non-positive / blank → 20 | Fallback page size when `input.limit` is omitted, blank, or 0. Still clamped to 20 at send time. |

\*At least one auth path must be configured or the node returns the authentication-required error and makes no GraphQL call.

Never log or return `developer_token`, `api_key`, `api_secret`, or `access_token`.

## How it works

1. Dispatch: POST `/invoke` with `templateId` `83226eb9-3d21-4b15-8404-33ef438b2256` → `NodeRegistry` → `ProducthuntNode`.
2. Auth order (whitespace-only values are missing):
   1. **`developer_token`** → Bearer; OAuth skipped.
   2. **`api_key` with empty `api_secret`** → `api_key` as Bearer.
   3. **`api_key` + `api_secret`** → POST JSON `{client_id, client_secret, grant_type: "client_credentials"}` to `https://api.producthunt.com/v2/oauth/token`, read `access_token`. Up to **3** attempts; on Cloudflare or transient HTTP/transport failure, sleep `2 * (attempt + 1)` seconds (2s, 4s, 6s). **The bearer is never cached** (not on the instance, not in a static field).
   4. None of the above → `output.error` = the `AUTH_REQUIRED` message (developer_token recommended; redeploy the template if new config fields are not visible).
3. GraphQL POST to `https://api.producthunt.com/v2/api/graphql` with headers `Authorization: Bearer …`, `Content-Type: application/json`, `Accept: application/json`, `User-Agent: StudioQuack-ProductHunt/1.0`. Body: `{query: QUERY_POSTS, variables}`.
4. `QUERY_POSTS` asks `posts(order, postedAfter, postedBefore, topic, first, after)` for `edges[].node` (id, name, tagline, description, votesCount, commentsCount, website, url, createdAt, makers.name, topics.edges.node.{name,slug}) and `pageInfo.{hasNextPage,endCursor}`.
5. Variables: `order` default `NEWEST`; `first = min(limit or default_limit, 20)`; optional `postedAfter` / `postedBefore` / `topic` / `after` only when non-blank.
6. If the JSON contains `errors`, the node returns `{error: "GraphQL errors: " + json(errors)}` and does not map posts. Cloudflare on GraphQL (HTML body or non-object JSON) is **not** retried: `Product Hunt API Error: Cloudflare challenge on GraphQL endpoint (HTTP …)`.

A Developer Token avoids `/v2/oauth/token` entirely and is the reliable path for automation.

## Security & legal

This is an **official** OAuth/GraphQL API. Use is subject to [Product Hunt Terms of Service](https://www.producthunt.com/legal/terms-of-service) and the [v2 API](https://api.producthunt.com/v2/docs). Create credentials only at [producthunt.com/v2/oauth/applications](https://www.producthunt.com/v2/oauth/applications).

- Stay inside the published fair-use budget (~**450 requests / 15 minutes**). This node does not throttle for you.
- Credentials are customer-owned secrets. Store them only in `configurationParameters` (password fields). Do not put tokens on `input`, in logs, or in downstream FUNCTION payloads.
- Bearer tokens are resolved per invoke and never cached across tenants.
- Error strings pass through `DataSupport.redact` for `developer_token`, `api_key`, `api_secret`, and `access_token`.
- The query is read-only. Do not use this node to mutate Product Hunt data.
- Outbound calls leave the customer sandbox toward `api.producthunt.com`. Cloudflare challenges are an edge defense, not a StudioQuack outage.

## Examples

Instance config: `developer_token` = (token), `timeout_seconds` = 25, `default_limit` = 20.

### Newest launches in a topic

Input:

```json
{
  "order": "NEWEST",
  "limit": 10,
  "topic": "artificial-intelligence",
  "posted_after": "2026-08-01T00:00:00Z"
}
```

Output (shape):

```json
{
  "status": "ok",
  "posts_returned": 1,
  "has_next_page": true,
  "end_cursor": "MjA=",
  "posts": [
    {
      "id": "123",
      "name": "Acme",
      "tagline": "Does X",
      "description": "First 500 characters of the Product Hunt description…",
      "votes_count": 42,
      "comments_count": 3,
      "website": "https://acme.example",
      "url": "https://www.producthunt.com/posts/acme",
      "created_at": "2026-08-01T12:00:00Z",
      "makers": ["Ada"],
      "topics": [{ "name": "Developer Tools", "slug": "developer-tools" }]
    }
  ]
}
```

### Next page

Same filters plus `"after": "MjA="` (the previous `end_cursor`). Stop when `has_next_page` is `false`.
