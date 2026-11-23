# Hashnode

- **Status:** done
- **Catalog:** `docs/catalog/social-media.md`
- **Folder when built:** `nodes/hashnode/`
- **Researched:** 2026-08-20
- **Official API:** Hashnode Public GraphQL API (unversioned schema; POST-only). Canonical host on apidocs: `https://gql.hashnode.com`. Writes and publication-scoped reads require **Hashnode Pro** on the target publication (changelog 2026-05-13).
- **Docs:** https://apidocs.hashnode.com/ — PAT: https://hashnode.com/settings/developer — Pro gate: https://hashnode.com/changelog/2026-05-13-graphql-api-paid-access
- **Auth:** api_key (Personal Access Token) — secret binding: `personal_access_token`

## Scope

One node for Hashnode. Calls the official GraphQL endpoint with JDK HTTP (`POST` JSON `{ query, variables }`). Catalog ops map to schema fields: `publishPost`, `post`, `createDraft`, `publication`. Host cover images first and pass `coverImageOptions.coverImageURL` inside `variables` — there is no typed INPUT `cover_image`.

**Do not** use retired `https://api.hashnode.com` (returns HTTP 502; officially discontinued). **Do not** scrape hashnode.com or hashnode.dev HTML.

Endpoint choice (verify at implement time with introspection):

| Source (2026-08-20) | URL |
|---|---|
| Canonical public docs (`apidocs.hashnode.com`) | `https://gql.hashnode.com` — **default** |
| Official Hashnode GraphQL agent skill (`hashnode/gql-skill`) | `https://gql-beta.hashnode.com` |

Ship default `graphql_url` = `https://gql.hashnode.com`. Allow config override only to Hashnode-owned hosts (`gql.hashnode.com`, `gql-beta.hashnode.com`). Introspect before calling `createImageUploadURL` (documented on the skill / beta schema; **not** present in the apidocs.hashnode.com schema dump fetched 2026-08-20).

Pro: target **publication** must be on Pro for writes and for `publication` / drafts. Public `post(id:)` is not Pro-gated. On `FORBIDDEN` + Pro-plan message: map to `{ error }`, do not retry.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `post_publish` | POST | GraphQL `publishPost(input: PublishPostInput!)` | Publish a post now |
| `post_get` | POST | GraphQL `post(id: ID!)` | Fetch a post by id (public; PAT optional) |
| `draft_create` | POST | GraphQL `createDraft(input: CreateDraftInput!)` | Save unpublished draft |
| `publication_get` | POST | GraphQL `publication(id: ObjectId, host: String)` | Publication metadata (**Pro** as of 2026-05-13) |

Do not expose retired legacy mutations (`createPublicationStory`, `createStory`). Optional schema mutations (`updatePost`, `publishDraft`, `createImageUploadURL`) are implementation helpers, not extra catalog ops — use them only as described under cover images.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `post_publish` | no | Chooses the row above |
| `personal_access_token` | password | yes for writes, drafts, `publication_get`; no for public `post_get` | | yes | PAT from Account Settings → Developer. Header (see notes). |
| `publication_id` | — | — | — | — | Not a node config. Put `publicationId` inside the GraphQL `variables` object on INPUT (empty bodyFrom POSTs the whole JSON). |
| `graphql_url` | string | no | `https://gql.hashnode.com` | no | Official GraphQL origin only |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — GraphQL `{ query, variables }` | Host cover images first; pass `coverImageOptions.coverImageURL` inside `variables` |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Failures never include the PAT |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = post_publish`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `title` | string | yes | `PublishPostInput.title` |
| `content_markdown` | string | yes | `PublishPostInput.contentMarkdown` |
| `publicationId` | string | no | Inside GraphQL `variables` (not a typed INPUT / config field) |
| `subtitle` | string | no | Subtitle |
| `slug` | string | no | Slug; API generates from title if omitted |
| `tags` | array | no | List of tag slugs (max 15). Map to `[{ slug }]` |
| `canonical_url` | string | no | `originalArticleURL` |
| `disable_comments` | boolean | no | `disableComments` |
| `coverImageOptions.coverImageURL` | string | no | Already-hosted cover URL inside GraphQL `variables` (no typed INPUT `cover_image`) |
| `published_at` | string | no | ISO-8601 `publishedAt` (backdate) |
| `series_id` | string | no | `seriesId` |
| `co_author_ids` | array | no | `coAuthors` ObjectIds |
| `meta_title` | string | no | `metaTags.title` |
| `meta_description` | string | no | `metaTags.description` |

Empty `title` or `content_markdown` → `{ error }`, no HTTP.

### `operation = post_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `post_id` | string | yes | GraphQL `post(id:)` |

### `operation = draft_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `publicationId` | string | no | Inside GraphQL `variables`; required to create a draft |
| `title` | string | no | Draft title |
| `content_markdown` | string | no | Markdown body |
| `subtitle` | string | no | Subtitle |
| `slug` | string | no | Slug |
| `tags` | array | no | Tag slugs |
| `coverImageOptions.coverImageURL` | string | no | Already-hosted cover URL inside GraphQL `variables` |
| `canonical_url` | string | no | `originalArticleURL` |

### `operation = publication_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `publicationId` | string | no | Inside GraphQL `variables` as `publication(id:)` — one of id or host required |
| `host` | string | no | `publication(host:)` e.g. `blog.example.com` or `name.hashnode.dev` |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | GraphQL success with data and no blocking errors | `ok` |
| `post_id` | string | publish / get | `post.id` |
| `draft_id` | string | draft | `draft.id` |
| `publication_id` | string | publication / publish | Publication id |
| `title` | string | 2xx data | Title |
| `slug` | string | post | Slug |
| `url` | string | post | Public post URL |
| `canonical_url` | string | post | Canonical URL if set |
| `cover_image_url` | string | post/draft | Resolved cover URL |
| `host` | string | publication | Publication host |
| `error` | string | failure | Provider or validation message; never the PAT |

Always request `id` on queried objects (Hashnode cache guidance).

## Legal

- Terms of Use (API §8): https://hashnode.com/terms — no harvest of publications you do not own/authorize; no reselling API access; no rate-limit bypass; no scrape/crawl beyond reasonable use.
- Pro access: https://hashnode.com/changelog/2026-05-13-graphql-api-paid-access and https://hashnode.com/changelog/2026-06-11-introducing-hashnode-pro
- Rate limits (apidocs, 2026-08-20): queries **20 000/min**, mutations **500/min**. Depth cap ~10; connection `first` typically max 100.
- PII: drafts, emails, and `unsubscribeCode` are restricted; do not log them.
- Official-API-first: GraphQL only. No HTML scrape, no unofficial third-party wrappers.

## Implementation notes

- JDK `HttpClient` only. No Hashnode SDK.
- `Content-Type: application/json`. TLS verify. Body cap 2 MiB JSON; cover file cap **8 MB**, `image/*`, **no SVG** (skill constraints).
- Timeout default 30 s. No inner retry except the one-shot Authorization fallback below. HTTP 429 / 5xx → `{ error }` with status code. GraphQL HTTP 200 with `errors[]` → `{ error }` from `extensions.code` + `message` (`UNAUTHENTICATED`, `FORBIDDEN`, `BAD_USER_INPUT`, `NOT_FOUND`, `GRAPHQL_VALIDATION_FAILED`).
- **Authorization:** send `Authorization: Bearer {personal_access_token}` (2026 official skill). Apidocs still describe the header value as the raw PAT. If the first call returns `UNAUTHENTICATED`, retry **once** with `Authorization: {personal_access_token}` (no scheme). Never log the header.
- **Cover `file`:** (1) Introspect `Mutation.createImageUploadURL`. (2) If present: `createImageUploadURL(input: { contentType })` → multipart POST to `presignedPost.url` with every `presignedPost.fields` entry then `file` last → CDN URL `https://cdn.hashnode.com/{fields.key}` (not the S3 object URL) → `coverImageOptions.coverImageURL`. (3) If absent: require `cover_image_url`; if only `cover_image` was provided, `{ error }` that upload is unavailable on this endpoint. Do not invent an unofficial upload host.
- Never throw past dispatch.
