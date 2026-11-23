# Neo4j

Handler: `OfficialApiNode` via `api_spec.json`.

- **Docs:** https://neo4j.com/docs/query-api/current/query/
- **Verified official HTTP:** `http://localhost:7474` (researched 2026-08-20)
- **Auth:** `password` (password secret). HTTP Basic (`username` + `password`).
- Customer owns the database / project. StudioQuack stores only the secret.

Verified 2026-08-20: official Query API `POST /db/{databaseName}/query/v2` on port 7474 (Aura uses https://<id>.databases.neo4j.io). Not Bolt. Replace `api_spec.baseUrl`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `cypher.run` | POST | `/db/{database}/query/v2` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `password` | password | yes | Official credential |
| `timeout_seconds` | number | no | Default 25 |

`api_spec.baseUrl` is static. OfficialApiNode does not substitute host. Point this file at your cluster origin when it is not the documented default.

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `password`.

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the SQL / rows / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP/SQL API only. No invented wire-over-HTTP proxy.
