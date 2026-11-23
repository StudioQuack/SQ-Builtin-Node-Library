# Convert File

- **Status:** done
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/convert-file/`
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.ConvertFileNode` (`SQ_TEMPLATE_ID_CONVERT_FILE`)
- **Official formats:** RFC 4180 CSV, RFC 5545 iCalendar, ECMA-376 OOXML (XLSX) and ODF (ODS) via JDK `java.util.zip` + XML, RFC 8259 JSON, HTML, RTF, plain text
- **Docs:** https://www.rfc-editor.org/rfc/rfc4180 · https://www.rfc-editor.org/rfc/rfc5545
- **Auth:** none

## Scope

Reads a platform `file` ref (`FilesSupport.readFileValue`) and writes another `file` ContentBlock in the target format. Also publishes `outputFiles` (same list `OfficialApiNode.outputFilesOf` reads). Does not fetch Office documents from the internet. Does not add libraries.

Source kind is taken from the file name / MIME, then a short content sniff. Legacy OLE2 `.xls` is refused (cannot be produced or parsed robustly without a library).

Caps: 8 MiB source/result (`MAX_FILE_BYTES`), 20 000 rows, 256 columns, 32 768 characters per cell, 5 000 ICS events, 64 MiB unzip total. XML parse is XXE-hardened.

## Operations (`operation` config values)

| operation | Output | Notes |
|---|---|---|
| `to_csv` | `text/csv` | RFC 4180 (quoted fields, `""` escapes, CRLF) |
| `to_xlsx` | XLSX zip | Inline-string sheet XML; unzip `xl/worksheets/sheet1.xml` to inspect |
| `to_ods` | ODS zip | `content.xml` table cells; `mimetype` stored uncompressed |
| `to_json` | `application/json` | Header row → array of objects; otherwise arrays / passthrough JSON |
| `to_html` | `text/html` | `<table>` with optional `<thead>` |
| `to_rtf` | `application/rtf` | Tab-separated rows, `\par` line breaks |
| `to_text` | `text/plain` | Tab-separated rows |
| `to_ics` | `text/calendar` | RFC 5545 VCALENDAR / VEVENT; lines folded at 75 octets |

`to_xls` is **not** implemented and is not an option.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | to_csv | no | Target format |
| `file_name` | string | no | | no | Output name |
| `sheet` | string | no | | no | Source worksheet name or 0-based index |
| `header_row` | select | no | true | no | First row is header |
| `encoding` | string | no | UTF-8 | no | Charset for text formats |
| `timeout_seconds` | number | no | 25 | no | Run budget (in-process) |

INPUT `operation`, `file`, `sheet`, `file_name`, `header_row`, `encoding` override the same-named config. `file` is required.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | file + per-op overrides | Failures never include secrets |
| `output` | OUTPUT | `{ status, data, file, outputFiles }` | Errors raise a BPMN error |

FUNCTION ports: none.

## Legal

- No scrape of Office Online. Customer owns the file contents.
- Spreadsheets and calendars may contain personal data. Bytes are never logged.
