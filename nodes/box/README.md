# Box

Box Platform API 2.0 via `OfficialApiNode`. Docs: [Reference](https://developer.box.com/reference/) · [Upload file](https://developer.box.com/reference/post-files-content/)

Auth: Bearer `access_token` (OAuth, JWT, or CCG minted outside StudioQuack).

`upload` POSTs multipart `attributes` (JSON `{name, parent:{id}}`) then `file` to `https://upload.box.com/api/2.0/files/content`. Direct upload is capped at 50 MB; chunked upload is not implemented. `parent_id` nests as `parent.id` on copy/create/update folder.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `copy_file` | POST | `/files/{file_id}/copy` |
| `delete_file` | DELETE | `/files/{file_id}` |
| `download` | GET | `/files/{file_id}/content` |
| `get_file` | GET | `/files/{file_id}` |
| `search` | GET | `/search` |
| `share_file` | PUT | `/files/{file_id}` |
| `upload` | POST | `https://upload.box.com/api/2.0/files/content` |
| `create_folder` | POST | `/folders` |
| `get_folder` | GET | `/folders/{folder_id}` |
| `delete_folder` | DELETE | `/folders/{folder_id}` |
| `share_folder` | PUT | `/folders/{folder_id}` |
| `update_folder` | PUT | `/folders/{folder_id}` |
| `listen` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `access_token` (password), `timeout_seconds`.
