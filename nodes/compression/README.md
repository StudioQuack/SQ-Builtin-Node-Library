# Compression

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/compression/`
- **Researched:** 2026-08-20
- **Official API:** ZIP APPNOTE, RFC 1952 gzip, POSIX tar
- **Docs:** https://www.rfc-editor.org/rfc/rfc1952
- **Auth:** none — secret binding: `(none)`
- **Handler:** `CompressionNode` (`CodecHandlers`, `SQ_TEMPLATE_ID_COMPRESSION`)

## Scope

Transforms a `file` ref into another `file` ref. Rejects `..` and absolute entry paths (zip-slip). Size and entry caps.

Ports and the `file` param are type `file`.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `compress` | local | zip/gzip/tar | file → archive |
| `decompress` | local | zip/gzip/tar | archive → file |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | compress | no | Verb |
| `format` | select | no | zip | no | Format |
| `file_name` | string | no | | no | Output name |
| `timeout_seconds` | number | no | 25 | no | Budget |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- No third-party API. Archive contents may be personal data.

## Implementation notes

- **`file` INPUT:** sandbox path under `/sandbox/files`, inline ContentBlock (`data`/`contentBase64`), or `$fileRef` — not a free-text ref-id. Agent tools must materialise or upload bytes before compress.
- **`file_name`:** optional; when set on INPUT, validated (non-empty, ≤255 chars, no `/` or `..`) before the source file is read.
- Validation order: operation → format → `file_name` (when provided) → `file` existence.
- Type `file` on INPUT/OUTPUT; zip-slip rejected on decompress.
- Cap entries and uncompressed size (`FilesSupport.MAX_FILE_BYTES`).
- File errors use BPMN codes (`INVALID_INPUT`, `REQUEST_FAILED`) via `FilesSupport.Fail`; not a raw subprocess string.
