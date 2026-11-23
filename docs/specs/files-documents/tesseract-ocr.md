# Tesseract OCR

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md`
- **Folder when built:** `nodes/tesseract-ocr/`
- **Researched:** 2026-08-20
- **Official API:** Tesseract OCR 5 CLI (Apache-2.0). No vendor HTTP API.
- **Docs:** https://tesseract-ocr.github.io/ · Command line: https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html · Input formats: https://tesseract-ocr.github.io/tessdoc/InputFormats.html · Man page: https://github.com/tesseract-ocr/tesseract/blob/main/doc/tesseract.1.asc · Source: https://github.com/tesseract-ocr/tesseract
- **Auth:** none — secret binding: none

## Scope

Local optical character recognition inside the node image. The handler **spawns the `tesseract` binary** in the sandbox (plus `pdftoppm` from Poppler for `ocr_pdf_page`). There is no cloud endpoint. Bytes never leave the customer host. Best choice when PHI / ID images must stay on-host.

This is **not** Docling. Docling already ships on the StudioQuack engine daemon for document conversion. Do not wrap Docling here.

**Do not build:** a cloud OCR HTTP client; HTML scrape of any site; OCRmyPDF as a required runtime (optional later, not this catalog row); inventing PDF-as-native-Tesseract-input (official tessdoc: *Tesseract does not support reading PDF files*).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `ocr_image` | n/a (exec) | `tesseract <image> <outbase> -l <lang> --psm <n> --oem <n> [--tessdata-dir …] [pdf]` | OCR one Leptonica-readable image. Text on OUTPUT as `string`. Optional searchable PDF via official `pdf` config. |
| `ocr_pdf_page` | n/a (exec) | `pdftoppm -f N -l N -r 300` then `tesseract` as above | Rasterize **one** PDF page, then OCR. Tesseract does not read PDF natively. |
| `list_languages` | n/a (exec) | `tesseract --list-langs [--tessdata-dir …]` | Languages present in the image’s tessdata. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `ocr_image` | no | Chooses the row above. |
| `tessdata_prefix` | string | no | image default (parent of `tessdata/`) | no | Official `TESSDATA_PREFIX`: **parent directory of `tessdata/`**. Handler sets the env var and/or `--tessdata-dir` so `eng.traineddata` resolves. |
| `lang` | string | no | `eng` | no | `-l` value. ISO 639-3 codes, `+` joined (`eng+deu`). Must exist in tessdata. |
| `psm` | number | no | `3` | no | `--psm` 0–13 (see man page). Default 3 = fully automatic segmentation, no OSD. |
| `oem` | number | no | `3` | no | `--oem` 0–3. `1` = LSTM only; `0` = legacy (needs tessdata with legacy models); `3` = default based on what is available. |
| `timeout_seconds` | number | no | `60` | no | Kill `tesseract` / `pdftoppm` after this many seconds. |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Source image/PDF is type `file`. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Extracted text is `string`. Searchable PDF is type `file`. Failures never include file bytes. |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = ocr_image`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source image ref-id. Leptonica formats: PNG, JPEG, TIFF, JPEG 2000, GIF (first frame), WebP (non-animated), BMP, PNM. Not HEIC/AVIF/JPEG-XL. |
| `lang` | string | no | Overrides config `-l`. |
| `psm` | number | no | Overrides config `--psm`. |
| `oem` | number | no | Overrides config `--oem`. |
| `emit_searchable_pdf` | boolean | no | If true, add official configfile `pdf` so Tesseract writes a searchable PDF (image + invisible text layer). |

### `operation = ocr_pdf_page`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `file` | file | yes | Source **PDF** ref-id. Handler rasterizes `page_number` with `pdftoppm` (ship Poppler in the image), then runs Tesseract on that PNG. |
| `page_number` | number | yes | 1-based page index (`pdftoppm -f N -l N`). |
| `dpi` | number | no | Rasterize DPI. Default `300`. Cap (e.g. 600) to bound CPU/RAM. |
| `lang` | string | no | Overrides config. |
| `psm` | number | no | Overrides config. |
| `oem` | number | no | Overrides config. |
| `emit_searchable_pdf` | boolean | no | If true, Tesseract `pdf` config on the **rasterized page** (one-page searchable PDF). Do not invent a multi-page merge. |

### `operation = list_languages`

No input fields. Uses `tessdata_prefix` from config.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `text` | string | `ocr_image`, `ocr_pdf_page` | UTF-8 recognized text (`stdout` / `.txt`). Not a file. |
| `searchable_pdf` | file | those ops when `emit_searchable_pdf=true` | Platform ref-id of the PDF Tesseract wrote. Official `pdf` config — do not invent other PDF writers. |
| `languages` | array of string | `list_languages` | Codes from `--list-langs` (skip the “List of available languages” header). |
| `error` | string | failure | Actionable, no paths that leak host layout beyond a generic “binary missing / tessdata missing / timeout”. |

## Legal

- License: **Apache License 2.0** (Tesseract). Language data: respective tessdata repos (`tessdata`, `tessdata_fast`, `tessdata_best`).
- No vendor ToS. No SaaS. Bytes stay on the customer daemon / sandbox.
- PHI / government IDs: this node is the on-host path. Do not forward the same file to a cloud OCR node in the same workflow unless the operator has a DPA/BAA with that vendor.
- Do not scrape websites as an OCR substitute.

## Implementation notes

- **JDK HTTP does not apply.** Tesseract is a local C++ binary, not an HTTP API. Spawn `tesseract` (and `pdftoppm` for PDF pages) with a bounded timeout; do not shell-interpolate user strings (argv array only).
- **Must ship tessdata in the image.** At minimum `eng.traineddata` and `osd.traineddata`. Document which pack (`tessdata_fast` recommended for size). Missing lang → `{ error }` listing `list_languages`.
- Resolve INPUT `file` via the daemon file helper to sandbox bytes. Never store base64 as the StudioQuack field type.
- Size cap the source file (e.g. 25 MiB image / 50 MiB PDF) and raster dimensions so a hostile PDF cannot exhaust the sandbox.
- `--psm 2` is documented as not implemented — reject with `{ error }`.
- Log shapes only: lang, psm, oem, page index, byte length, duration. Never log recognized text (may be PHI).
- Resource caps: OCR is CPU-heavy; honor sandbox memory/CPU limits. One process per invoke.
- Do not call the network. Egress not required.
