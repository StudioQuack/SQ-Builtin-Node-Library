# Disqus

Official Disqus **Web API 3.0**. Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://disqus.com/api/docs/
- **Base:** `https://disqus.com/api/3.0`
- **Auth:** query `api_key`. Never on INPUT.
- **Researched:** 2026-08-20

Disqus documents both `api_key` and `api_secret`. `OfficialApiNode` sends **one** query secret (`api_key`). It cannot attach `api_secret` or an OAuth `access_token` on the same call.

Disqus 3.0 does not document a first-party webhook. This node has **no** `trigger` operation.

There is **no** `GET /forums/list.json`. `forum_list` maps to documented [users/listForums](https://disqus.com/api/docs/users/listForums/).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `post_list` | GET | `/posts/list.json` |
| `post_create` | POST | `/posts/create.json` |
| `thread_get` | GET | `/threads/details.json` |
| `forum_list` | GET | `/users/listForums.json` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb (`post_list`) |
| `api_key` | password | yes | Query `api_key` |
| `timeout_seconds` | number | no | Default 25 |

Ports: `input` / `output` only. Failures raise a BPMN error — never includes `api_key`.

Commenter email and IP are PII. Do not persist them from `data` unless the workflow has a lawful basis.

## Legal

- API terms: https://disqus.com/api/docs/restrictions/
- Customer owns the Disqus application. Official Web API only.
