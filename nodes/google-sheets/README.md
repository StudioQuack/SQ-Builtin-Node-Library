# Google Sheets

In-process StudioQuack builtin (`templateId` `8a614338-622c-4b2e-b00b-87cf4fa53fa7`, override with `SQ_TEMPLATE_ID_GOOGLE_SHEETS`). Handler: `com.studioquack.nodelibrary.nodes.data.GoogleSheetsNode`.

## Overview

The node reads and writes a Google spreadsheet through the **official Google Sheets API v4** (`https://sheets.googleapis.com/v4/spreadsheets`). Auth is a **Google Cloud service account** using OAuth 2.0 JWT bearer grant (**RS256**, PKCS8 PEM). There is no end-user OAuth consent screen, no Google client library, and no PyJWT — the handler signs with JDK `KeyFactory` / `Signature` (`SHA256withRSA`).

Share the target spreadsheet with the service account’s `client_email` or every API call returns HTTP 403.

Provider docs:

- Sheets API v4: [developers.google.com/sheets/api](https://developers.google.com/sheets/api)
- REST reference: [developers.google.com/sheets/api/reference/rest](https://developers.google.com/sheets/api/reference/rest)
- Service-account JWT: [developers.google.com/identity/protocols/oauth2/service-account](https://developers.google.com/identity/protocols/oauth2/service-account)
- Enable the API: [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Library → Google Sheets API
- Google APIs Terms of Service: [developers.google.com/terms](https://developers.google.com/terms)
- Authorization / scopes: [Sheets API authorizing](https://developers.google.com/workspace/sheets/api/guides/authorizing)

Typical quota: **300 read and 300 write requests/minute per project**, plus **60 read and 60 write requests/minute per user per project** (https://developers.google.com/workspace/sheets/api/limits). A service account counts as one user. `jobType` is `custom-script-executor`. Needs outbound HTTPS to `oauth2.googleapis.com` and `sheets.googleapis.com`.

The OUTPUT port value is a **map** of the schema fields on success.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | Action + A1 ranges / 2D `values`. Optional `spreadsheet_id` overrides instance config. Service-account JSON is **not** on this port. |
| `output` | OUTPUT | object | yes | Success object. Secrets redacted to `***`. |

`DataSupport.readInput` unwraps `inputVariables.input`. Failures raise a BPMN error. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

### `input` schema

| Field | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `action` | string | no | `read` | `read` \| `batch_read` \| `write` \| `append` \| `clear` \| `get_metadata` (case-sensitive) | Which Sheets v4 call to run. Other strings → `INVALID_INPUT` `Unknown action: …`. |
| `spreadsheet_id` | string | if config blank | config `spreadsheet_id` | `[A-Za-z0-9_-]+`, length ≥ 20, must not contain `PLACEHOLDER` / `YOUR_` / `EXAMPLE` / `TODO` (case-insensitive). Typical ~44 chars from `docs.google.com/spreadsheets/d/{THIS}/edit` | Workbook id for this invoke. |
| `range` | string | `read`, `write`, `append`, `clear` | — | A1 notation (`Sheet1!A1:D10`, `Sheet1!A:D`, named range). Path-encoded (`!` → `%21`, `:` → `%3A`) | Cell window. Ignored by `batch_read` / `get_metadata`. |
| `ranges` | list | `batch_read` | — | JSON **array** of A1 strings (a CSV string is rejected) | Repeated `ranges=` on `values:batchGet`. |
| `values` | list | `write`, `append` | — | 2D JSON array of rows; cells string/number/boolean/empty | Body `values` with `majorDimension=ROWS`. Empty/omitted → missing. |
| `value_input_option` | string | no | `USER_ENTERED` | `USER_ENTERED` (formulas/dates parsed as typed) \| `RAW` (store literals) | Sheets `valueInputOption` on write/append. Other strings are forwarded (typically HTTP 400). |
| `insert_data_option` | string | no | `INSERT_ROWS` | `INSERT_ROWS` \| `OVERWRITE` | Sheets `insertDataOption` on **append** only. |

### `output` schema

| Field | Type | When present | Purpose |
| --- | --- | --- | --- |
| `status` | string | success | Always `ok`. |
| `read_range` | string | `read` | A1 range Google returned (may expand `A:D`). |
| `major_dimension` | string | `read` | `ROWS` (default) or `COLUMNS`. |
| `cell_data` | array | `read` | 2D formatted values (`FORMATTED_VALUE` / `FORMATTED_STRING`). `[]` if empty. |
| `rows_returned` | number | `read` | `len(cell_data)`. |
| `ranges_returned` | number | `batch_read` | `len(value_ranges)`. |
| `value_ranges` | array | `batch_read` | `{range, values, rows_returned}` per requested range. |
| `updated_range` | string | `write`, `append` | Google `updatedRange` / `updates.updatedRange`. |
| `updated_rows` | number | `write`, `append` | Rows reported updated (0 if omitted). |
| `updated_columns` | number | `write` only | Columns reported updated. |
| `updated_cells` | number | `write`, `append` | Cells reported updated. |
| `cleared_range` | string | `clear` | `clearedRange`, else the requested range. |
| `title` | string | `get_metadata` | Workbook `properties.title`. |
| `sheets_count` | number | `get_metadata` | Worksheet count. |
| `sheets` | array | `get_metadata` | `{sheet_id, title, index, row_count, column_count}`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `service_account_json` | password | yes |  | yes | Google Cloud service-account key (Required). Purpose: mint a per-request OAuth access token via JWT RS256 (iss=client_email, scope=https://www.googleapis.com/auth/spreadsheets, ... |
| `spreadsheet_id` | string | no |  | no | Default workbook id when input.spreadsheet_id is blank. INPUT wins. |
| `action` | select | yes | read | no | read, batch_read, write, append, clear, get_metadata. INPUT action wins. |
| `range` | string | no |  | no | A1 range for read, write, append, and clear. INPUT wins. |
| `ranges` | list | no |  | no | JSON array of A1 ranges for batch_read. INPUT wins. |
| `values` | list | no |  | no | 2D JSON array for write and append. INPUT wins. |
| `value_input_option` | string | no |  | no | write/append. USER_ENTERED or RAW. Empty uses USER_ENTERED. |
| `insert_data_option` | string | no |  | no | append only. INSERT_ROWS or OVERWRITE. Empty uses INSERT_ROWS. |
| `timeout_seconds` | number | no | 25 | no | Per-HTTP connect+read timeout for the token POST and every Sheets call. |

## How it works

1. Dispatch: POST `/invoke` with `templateId` `8a614338-622c-4b2e-b00b-87cf4fa53fa7` → `GoogleSheetsNode`.
2. Read `service_account_json` (required), `spreadsheet_id`, `timeout_seconds`. Parse the key as JSON object. Validate spreadsheet id (length, charset, reject placeholder tokens).
3. **JWT RS256 (JDK only):**
   - Load PKCS8 PEM via `KeyFactory.getInstance("RSA")` + `PKCS8EncodedKeySpec` (literal `\n` in the JSON string is expanded to newlines).
   - Compact JWS: `base64url({"alg":"RS256","typ":"JWT"}).base64url(payload).base64url(SHA256withRSA)`.
   - Payload: `iss=client_email`, `scope=https://www.googleapis.com/auth/spreadsheets`, `aud=https://oauth2.googleapis.com/token`, `iat=now`, `exp=now+3600`.
4. **Token exchange:** POST `application/x-www-form-urlencoded` to `https://oauth2.googleapis.com/token` with `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` and `assertion=<jwt>`. Read `access_token`. **The token is not cached** (stack-local only; next invoke signs again).
5. Sheets call with `Authorization: Bearer <token>`:
   - `read` → `GET /v4/spreadsheets/{id}/values/{range}?valueRenderOption=FORMATTED_VALUE&dateTimeRenderOption=FORMATTED_STRING`
   - `batch_read` → `GET /v4/spreadsheets/{id}/values:batchGet?ranges=…`
   - `write` → `PUT /v4/spreadsheets/{id}/values/{range}?valueInputOption=…`
   - `append` → `POST /v4/spreadsheets/{id}/values/{range}:append?valueInputOption=…&insertDataOption=…`
   - `clear` → `POST /v4/spreadsheets/{id}/values/{range}:clear` body `{}`
   - `get_metadata` → `GET /v4/spreadsheets/{id}?fields=spreadsheetId,properties.title,sheets.properties`
6. **OUTPUT is the result map.** `dump()` does `DataSupport.ports(body)`. Failures raise a BPMN error (secrets redacted).

`private_key`, the JWT assertion, `access_token`, and the raw key JSON are added to the redact list and replaced with `***` if they leak into an error string. They are never logged.

## Security & legal

This is an **official** Google API. Use is subject to the [Google APIs Terms of Service](https://developers.google.com/terms) and [Sheets API authorization](https://developers.google.com/workspace/sheets/api/guides/authorizing) (scope `https://www.googleapis.com/auth/spreadsheets`).

- **`service_account_json` is a secret.** Store it only in `configurationParameters` (password). Never put it on `input`, in engine variables, in logs, or in a downstream FUNCTION payload. A leaked key can read/write every spreadsheet shared with that `client_email`.
- Tokens are minted per invoke and never stored on the instance or in static fields (tenant isolation).
- Share the workbook with the least privilege that works (Viewer vs Editor). Do not grant the service account domain-wide delegation unless the customer’s Google admin has a separate, documented need.
- Typical quota: 300 reads and 300 writes/min per project; 60 reads and 60 writes/min per user per project (https://developers.google.com/workspace/sheets/api/limits). HTTP 403 usually means the sheet is not shared or the Sheets API is not enabled; HTTP 429 is quota — back off.
- Spreadsheet contents may include personal data. Outbound calls leave the customer sandbox toward Google; the customer is the Google Cloud project owner and the data controller for that sheet.
- PHI: do not put electronic protected health information in a Google Sheet unless the customer’s Google Cloud / BAA posture covers that project. This node does not add extra encryption beyond TLS to Google.

## Examples

Instance config: `service_account_json` = the key file contents, `spreadsheet_id` = the workbook id, `timeout_seconds` = 25.

### `read`

Input:

```json
{
  "action": "read",
  "range": "Sheet1!A1:D10"
}
```

OUTPUT port map:

```json
{
  "status": "ok",
  "read_range": "Sheet1!A1:D10",
  "major_dimension": "ROWS",
  "cell_data": [["Name", "Score"], ["Ada", "98"]],
  "rows_returned": 2
}
```

### `write` and `append`

```json
{
  "action": "write",
  "range": "Sheet1!A1:B2",
  "values": [["Name", "Score"], ["Ada", 98]],
  "value_input_option": "USER_ENTERED"
}
```

```json
{
  "action": "append",
  "range": "Sheet1!A:B",
  "values": [["Bob", 71]],
  "insert_data_option": "INSERT_ROWS"
}
```

Write success: `{ "status": "ok", "updated_range": "Sheet1!A1:B2", "updated_rows": 2, "updated_columns": 2, "updated_cells": 4 }`.

### `get_metadata`

```json
{ "action": "get_metadata" }
```

Parsed success includes `title`, `sheets_count`, and `sheets[]` with `sheet_id`, `title`, `index`, `row_count`, `column_count`.
