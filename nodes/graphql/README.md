# GraphQL

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/graphql/`
- **Researched:** 2026-08-22
- **Official API:** GraphQL over HTTP (GraphQL spec September 2025 + graphql-over-http draft)
- **Docs:** https://spec.graphql.org/September2025/ · https://graphql.github.io/graphql-over-http/
- **Auth:** bearer — secret binding: `auth_token` (blank when the host needs no auth)
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_GRAPHQL`)

## Scope

Calls a GraphQL HTTP endpoint with `query` / `variables` / optional `operationName`. Official surface: POST application/json (or GET with query string). Not a crawler. Customer owns the target API terms.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `query` | HTTP POST | `/` (endpoint) | Read fields |
| `mutate` | HTTP POST | `/` (endpoint) | Write fields |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | query | no | Which capability to run. EXACT accepted values: query / mutate. Required. Example: 'query'. |
| `endpoint` | string | yes |  | no | Absolute GraphQL HTTP URL (https). Required. Example: 'https://api.example.com/graphql'. |
| `auth_token` | password | no |  | yes | Bearer token for the GraphQL host (Authorization: Bearer). Secret. Blank when the host needs no auth. |
| `query` | string | no |  | no | GraphQL query or mutation document. Required. Example: query { viewer { id } }. |
| `variables` | object | no |  | no | GraphQL variables object. query / mutate. Example: {"id":"1"}. |
| `operationName` | string | no |  | no | GraphQL operationName when the document has several operations. query / mutate. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- GraphQL is a language, not a host ToS. The operator must comply with the **target endpoint** terms.
- TLS is verified. There is no ignore-TLS knob.
- Official-API-first: no HTML scrape of GraphiQL.

## Implementation notes

- `baseUrl` is the instance `endpoint` (absolute https URL). OfficialApiNode substitutes path params from input/config.
- Prefer POST + `application/json` body `{query, variables, operationName}`.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
