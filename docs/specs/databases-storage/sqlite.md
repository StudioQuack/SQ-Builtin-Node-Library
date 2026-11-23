# SQLite (local file) — do not build

- **Status:** planned (do not build — host filesystem path)
- **Catalog:** `docs/catalog/databases-storage.md`
- **Folder when built:** none
- **Researched:** 2026-08-20
- **Official API:** SQLite 3 C API (file-backed database). Public domain / blessing: https://www.sqlite.org/copyright.html
- **Docs:** https://www.sqlite.org/docs.html
- **Auth:** none for a local file (the path *is* the capability)

## Scope

**DO NOT BUILD this node.** Catalog status remains `planned` so the row stays in the index, but implementers must not ship `nodes/sqlite/`.

A local SQLite database is addressed by `filePath` / `localPath`. That is a **host filesystem path**. StudioQuack execution forbids exposing host FS paths to nodes (sandbox: no host path, secrets on tmpfs only). `backup.export` to a host path is the same violation.

Hosted SQLite dialect (libSQL over HTTP) is **Turso**. Implement [`turso.md`](turso.md) instead (`nodes/turso/`).

Do not implement:

- `filePath`, `localPath`, relative paths, or `jdbc:sqlite:/…`
- libSQL embedded / local replica files
- attaching additional files via SQL `ATTACH DATABASE '…'` with a path

## Operations (`operation` config values)

Catalog listed `execute.sql`, `row.select`, `row.insert`, `row.update`, `row.delete`, `backup.export`. **None are to be implemented** on a local file.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| — | — | — | Use Turso Cloud HTTP (`turso.md`) |

## Configuration parameters

Do not add `filePath` / `readOnly` as shippable config.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | unused | Do not ship |
| `output` | OUTPUT | unused | Do not ship |

## Legal

- SQLite license: https://www.sqlite.org/copyright.html
- No cloud rate limits (local file). Irrelevant because this node is not built.
- Official-API-first: N/A for an unbuilt node.

## Implementation notes

Redirect all SQLite-dialect work to [`turso.md`](turso.md) (Bearer token + `POST /v2/pipeline`).
