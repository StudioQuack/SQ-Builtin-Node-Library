# Files

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/files/`
- **Researched:** 2026-08-20
- **Official API:** StudioQuack sandbox filesystem (platform — not a third-party API)
- **Docs:** https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Path.html
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_FILES`)

## Scope

All paths are resolved under sandbox tmp (`base_dir`). Host paths are rejected. `watch` does not open an ingress socket — use a BPMN timer that calls `list`/`read`, or a message event if an adapter signals changes.

read/write `file` fields are type `file`.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `read` | sandbox FS | NIO | → file ref |
| `write` | sandbox FS | NIO | file ref → |
| `list` | sandbox FS | NIO | Names |
| `delete` | sandbox FS | NIO | Unlink |
| `watch` | ingress | BPMN timer/message | No socket |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | list | no | Verb |
| `base_dir` | string | no | . | no | Sandbox root |
| `encoding` | string | no | UTF-8 | no | Text |
| `watch_glob` | string | no | | no | Timer hint |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- Platform sandbox only. Never daemon host paths.
- File contents may be personal data.

## Implementation notes

- Type file on read/write.
- watch: kind ingress.
- Reject `..` and absolute host paths.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
