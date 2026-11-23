# Backblaze B2

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/backblaze-b2/`
- **Official API:** https://www.backblaze.com/apidocs/introduction-to-the-b2-native-api · https://www.backblaze.com/docs/cloud-storage-native-api-versions
- **Auth secret:** `application_key`
- **Researched:** 2026-08-22

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Default `baseUrl` is **Native API v4**: `https://api.backblazeb2.com/b2api/v4`.
Put the `authorizationToken` from `b2_authorize_account` in `application_key`.
Downloaded bytes use OUTPUT `file` (Platform file ref-id).

## Operations

| operation | HTTP | Path |
|---|---|---|
| `bucket_list` | POST | `/b2_list_buckets` |
| `bucket_create` | POST | `/b2_create_bucket` |
| `file_upload` | POST | `/b2_get_upload_url` |
| `file_download` | GET | `/b2_download_file_by_id` |
| `file_delete` | POST | `/b2_delete_file_version` |
| `file_list` | POST | `/b2_list_file_names` |
| `hide_file` | POST | `/b2_hide_file` |
| `get_download_auth` | POST | `/b2_get_download_authorization` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), `application_key` (password), `timeout_seconds`.

## Legal

- Terms: https://www.backblaze.com/company/policy/terms-of-service
- Official-API-first. No scrape. No unofficial web wrapper.
- Authorize first (`b2_authorize_account`) then call the returned `apiUrl`. Current Native API version is **v4**. Multi-bucket application keys require v4 authorize. Lifecycle rules are customer-owned.
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.
