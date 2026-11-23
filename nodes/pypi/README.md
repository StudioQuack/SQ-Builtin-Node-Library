# PyPI

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_PYPI`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [PyPI Warehouse JSON / upload](https://docs.pypi.org/api/) at `https://pypi.org`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Errors raise a BPMN error. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `project_get` | GET | `/pypi/{name}/json` | Official HTTP |
| `version_get` | GET | `/pypi/{name}/{version}/json` | Official HTTP |
| `files_list` | GET | `/pypi/{name}/json` | Official HTTP |
| `upload` | POST | `https://upload.pypi.org/legacy/` | Warehouse multipart: `name`, `version`, `content` (file), `filetype`, digests, metadata |

`search` is **removed**. XML-RPC search is permanently disabled ([warehouse XML-RPC](https://warehouse.pypa.io/api-reference/xml-rpc/)). Use `project_get` or https://pypi.org/search in a browser.

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | PyPI API token for upload (__token__ as username). Public JSON GET does not need it. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses raise a BPMN error with `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [PyPI Warehouse JSON / upload docs](https://docs.pypi.org/api/). Terms: https://policies.python.org/pypi.org/Terms-of-Use/.
- XML-RPC `search` is permanently disabled ([warehouse XML-RPC](https://warehouse.pypa.io/api-reference/xml-rpc/)). This node has no `search` operation. Use `project_get` or https://pypi.org/search in a browser.
- PyPI Terms. Do not scrape pypi.org HTML. upload is official Warehouse /legacy/ (multipart); OfficialApiNode sends JSON — treat upload as a stub until multipart lands. File artifacts: type file.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
