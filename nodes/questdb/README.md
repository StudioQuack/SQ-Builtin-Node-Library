# QuestDB

Official QuestDB **REST** (`OfficialApiNode` via `api_spec.json`). Customer-owned instance. Not ILP TCP (`:9009`) and not PostgreSQL wire (`:8812`).

- **Verified HTTP:** `GET http://localhost:9000/exec?query=` — [REST API](https://questdb.io/docs/reference/api/rest/) / [query REST](https://questdb.com/docs/query/rest-api/) (2026-08-20).
- **CSV import:** `POST /imp` is official (`multipart/form-data` CSV in part `data`). Query params: `name`, `timestamp`, `partitionBy`, `overwrite`, `atomicity`, `delimiter`, `fmt`, `forceHeader`, `skipLev`, `o3MaxLag`, `maxUncommittedRows`. Optional multipart `schema` part for column types.
- **Placeholder `baseUrl`:** `http://localhost:9000`. OfficialApiNode does **not** substitute host. Edit `api_spec.json` `baseUrl` for Cloud / TLS.
- **Auth:** HTTP Basic is documented for OSS (`http.user` / `http.password` in `server.conf`) and Cloud. Enterprise also documents Bearer tokens (not used here — one auth type). OfficialApiNode **requires** a non-empty `password`. Default OSS with auth disabled typically ignores `Authorization`; set a placeholder user/password so the node can send Basic. When auth is enabled, use real credentials. Prefer TLS in production (open HTTP is an operator risk).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `execute.sql` | GET | `/exec` (`query` query param) |
| `import.csv` | POST | `/imp` |

`import.csv` POSTs `multipart/form-data` with form field `data` (CSV text or a platform file ContentBlock), matching official `/imp` (`curl -F data=@file.csv` — [REST API](https://questdb.com/docs/reference/api/ilp/overview/) / [CSV import](https://questdb.com/docs/reference/api/rest/)). `execute.sql` also accepts `/exec` query flags `limit`, `nm`, `count`, `timings`.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `user` | string | no | Basic username |
| `password` | password | yes | Basic password (required by the handler even if the server has auth off) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED). Never includes `password`.

## Legal

- Customer is controller of time-series rows. Minimize: send only the SQL required.
- Apache 2.0 / Cloud ToS: https://questdb.com
- Official REST only. Do not scrape the Web Console. Do not `COPY` from a host filesystem path.
