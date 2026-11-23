# Buffer

Official Buffer **GraphQL** API at `POST https://api.buffer.com/`. Docs: [Posts & scheduling](https://developers.buffer.com/guides/posts-and-scheduling.html) · [REST migration](https://developers.buffer.com/guides/rest-migration.html)

Auth: Bearer `access_token` (personal API key or OAuth). Do **not** call `api.bufferapp.com` (legacy REST).

Purpose-oriented fields (`text`, `channelId`, `organizationId`, `schedulingType`, `mode`, `dueAt`, `first`, `after`) nest into GraphQL `variables`. Supply `query` with the matching document (or a variables-based mutation). `variables` remains an escape hatch.

The node’s `api_spec.json` defines **`bodyDefaults.query` per operation**: if `query` is omitted or blank, that default document is sent so POST `/` always carries valid JSON (`{ "query", "variables" }`). Overrides win when `query` is non-empty.

Typical documents:

- `organization_list`: `query { account { organizations { id name } } }`
- `channel_list`: `query($input: ChannelsInput!) { channels(input: $input) { id name service } }` with `organizationId`
- `post_create`: `mutation($input: CreatePostInput!) { createPost(input: $input) { ... on PostActionSuccess { post { id text status } } ... on MutationError { message } } }`
- `post_list`: `query($first: Int, $after: String, $input: PostsInput) { posts(first: $first, after: $after, input: $input) { edges { node { id text } } } }`

## Operations

| operation | HTTP | Path |
|---|---|---|
| `organization_list` | POST | `/` |
| `channel_list` | POST | `/` |
| `post_create` | POST | `/` |
| `post_list` | POST | `/` |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. GraphQL may return HTTP 200 with `errors[]` — inspect `data`.

## Config

`operation`, `access_token` (password), `timeout_seconds`.
