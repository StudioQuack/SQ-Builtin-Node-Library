# Nextcloud

- **Status:** done (OfficialApiNode package)
- **Folder:** `nodes/nextcloud/`
- **Official API:** https://docs.nextcloud.com/server/stable/developer_manual/client_apis/WebDAV/index.html
- **Auth secret:** `app_password`
- **Researched:** 2026-08-20

Calls the official HTTP surface via `OfficialApiNode` + `api_spec.json`.
Downloaded payloads may appear as OUTPUT `file` (platform file ref-id). Never inline bytes or base64 as the stored type.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `copy_file` | COPY | `/remote.php/dav/files/{username}/{path}` |
| `delete_file` | DELETE | `/remote.php/dav/files/{username}/{path}` |
| `download` | GET | `/remote.php/dav/files/{username}/{path}` |
| `move_file` | MOVE | `/remote.php/dav/files/{username}/{path}` |
| `share_file` | POST | `/ocs/v2.php/apps/files_sharing/api/v1/shares` |
| `upload` | PUT | `/remote.php/dav/files/{username}/{path}` |
| `copy_folder` | COPY | `/remote.php/dav/files/{username}/{path}` |
| `create_folder` | MKCOL | `/remote.php/dav/files/{username}/{path}` |
| `delete_folder` | DELETE | `/remote.php/dav/files/{username}/{path}` |
| `list_folder` | PROPFIND | `/remote.php/dav/files/{username}/{path}` |
| `move_folder` | MOVE | `/remote.php/dav/files/{username}/{path}` |
| `share_folder` | POST | `/ocs/v2.php/apps/files_sharing/api/v1/shares` |
| `user_invite` | POST | `/ocs/v1.php/cloud/users` |
| `user_get` | GET | `/ocs/v1.php/cloud/users/{userid}` |
| `user_list` | GET | `/ocs/v1.php/cloud/users` |
| `user_update` | PUT | `/ocs/v1.php/cloud/users/{userid}` |
| `user_delete` | DELETE | `/ocs/v1.php/cloud/users/{userid}` |

## Ports

| Port | Direction | Notes |
|---|---|---|
| `input` | INPUT | Per-operation fields. No secrets. |
| `output` | OUTPUT | `{ status, http_status, data, file? }` or `{ error }`. |

FUNCTION ports: none.

## Configuration

`operation` (select), auth secrets (password), `base_url`, `timeout_seconds`.

## Legal

- Terms: Customer-hosted — no vendor SaaS terms.
- Official-API-first. No scrape. No unofficial web wrapper.
- Self-hosted AGPL server. Use app passwords, not the account password. Set `base_url` to the real origin (interpolated as baseUrl).
- Logs: shapes, ids, status codes, sizes — never file bytes or secrets.

## Implementation notes

- Engine: OfficialApiNode (JDK HTTP). No vendor SDK.
- `listen` / ingress: use a BPMN message start or catch event.
- Failures map to OUTPUT `{ error }`. Never throw past dispatch.

`upload` PUTs raw `file` bytes. `copy_*` / `move_*` send WebDAV `Destination: {destination}`.
