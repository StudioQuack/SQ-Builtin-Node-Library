# Hashnode

Official Hashnode Public GraphQL API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_HASHNODE`). Dispatched by `OfficialApiNode` from `api_spec.json`.

- **Docs:** https://apidocs.hashnode.com/
- **PAT:** https://hashnode.com/settings/developer
- **Pro gate:** https://hashnode.com/changelog/2026-05-13-graphql-api-paid-access
- **Base:** `https://gql.hashnode.com` (do not use retired `https://api.hashnode.com`)
- **Auth:** Bearer `personal_access_token`. As of 2026-05-13, **every** GraphQL request against a publication (queries and mutations) requires Hashnode Pro (https://hashnode.com/changelog/2026-05-13-graphql-api-paid-access).

Body is `query` plus optional `variables`, or purpose fields that merge into `variables` via `bodyMap`. `publicationId`, `title`, `contentMarkdown`, `subtitle`, `slug`, `tags` map to `variables.input`; `host` / `id` map to `variables` on get operations. The engine does not inject a GraphQL document — `query` must be non-empty and look like GraphQL (validated locally).

**Response handling:** HTTP 200 HTML (marketing/redirect pages) is rejected — not reported as `status: ok`. Only JSON with top-level `data` and/or `errors` is accepted. Pro plan + PAT required per Hashnode (see changelog link above).

## GraphQL operation names

| node `operation` | GraphQL field |
|---|---|
| `post_publish` | `publishPost` |
| `post_get` | `post` |
| `draft_create` | `createDraft` |
| `publication_get` | `publication` |

All four are `POST /` on `https://gql.hashnode.com`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Which catalog verb (same HTTP path) |
| `personal_access_token` | password | yes | Bearer PAT |
| `publicationId` | string | no | Default publication ObjectId for publish/draft |
| `timeout_seconds` | number | no | Default 30 |

## Ports

One INPUT `input`, one OUTPUT `output`. Host cover images first and pass `coverImageOptions.coverImageURL` inside `variables` (not a typed INPUT field). Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

Example `post_publish` input:

```json
{
  "query": "mutation PublishPost($input: PublishPostInput!) { publishPost(input: $input) { post { id title url } } }",
  "variables": { "input": { "title": "Hello", "contentMarkdown": "# Hi", "publicationId": "…" } }
}
```

## Legal

- Terms of Use (API §8): https://hashnode.com/terms
- Rate limits (apidocs): queries 20,000/min, mutations 500/min.
- Customer owns the PAT. Do not harvest publications you do not own. Official GraphQL only.
