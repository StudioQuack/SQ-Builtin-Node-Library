# Microsoft OneDrive

> Catalog-canonical row is productivity-collaboration; this file is the implementer contract until that category spec lands.

- **Status:** planned
- **Catalog:** `docs/catalog/files-documents.md` (same node as `docs/catalog/productivity-collaboration.md`)
- **Folder when built:** `nodes/microsoft-onedrive/`
- **Researched:** 2026-08-20
- **Official API:** Microsoft Graph v1.0 Drive / DriveItem
- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/onedrive?view=graph-rest-1.0
- **Auth:** `oauth2_delegated` or `entra_app_only` — secret binding: `client_secret`, `refresh_token`

## Scope

One node for OneDrive (personal and work/school) files using **Microsoft Graph v1.0** DriveItem APIs. Default host `https://graph.microsoft.com/v1.0`. Address the user’s drive as `/me/drive` (delegated) or `/users/{user-id}/drive` / `/drives/{drive-id}` (app-only). JDK HTTP only — do not ship the Microsoft Graph Java SDK.

Simple content PUT (`driveitem-put-content`) currently documents a **250 MB** cap (Learn page updated 2026-08-11). **createUploadSession** is required above that cap. Catalog copy historically said “chunked >4 MB” (older Graph docs). Implementer: if PUT returns `413` / invalid range, fall back to an upload session. Session fragments should be multiples of **320 KiB**.

`listen` is official Graph **change notifications**: `POST /subscriptions` with `notificationUrl` = StudioQuack webhook ingress, then `GET …/delta` for the payload. Do not scrape onedrive.live.com / Office web.

File bytes use type `file` (`sq-file:` / `sq-ref:gridfs:`), never inline bytes.

**Do not build:** Excel Graph as this node; SharePoint list item CRUD (that is `microsoft-sharepoint`); legacy OneDrive API without Graph.

## Operations (`operation` config values)

Paths below are relative to `{graph_base_url}`. Substitute `/me/drive` with `/drives/{drive_id}` or `/users/{user_id}/drive` from config.

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `copy` | POST | `/me/drive/items/{item-id}/copy` | Body `{ parentReference, name? }`. **202 Accepted** — poll `Location` monitor URL. |
| `delete` | DELETE | `/me/drive/items/{item-id}` | Delete item (recycle behavior per tenant). |
| `download` | GET | `/me/drive/items/{item-id}/content` | Follow redirect; store as `file` ref-id. |
| `get` | GET | `/me/drive/items/{item-id}` | Metadata. Path alt: `/me/drive/root:/{path}`. |
| `rename` | PATCH | `/me/drive/items/{item-id}` | Body `{ "name": "…" }`. |
| `search` | GET | `/me/drive/root/search(q='{query}')` | Search in the drive. |
| `share` | POST | `/me/drive/items/{item-id}/createLink` | Body `{ type, scope }`. Optional `invite` at `/items/{id}/invite`. |
| `upload` | PUT | `/me/drive/items/{parent-id}:/{filename}:/content` (≤ simple cap) or `POST …:/{filename}:/createUploadSession` then PUT to `uploadUrl` | Stream from `file` ref-id. |
| `create_folder` | POST | `/me/drive/items/{parent-id}/children` | `{ name, folder: {}, "@microsoft.graph.conflictBehavior": "rename" }`. |
| `list_children` | GET | `/me/drive/items/{item-id}/children` | Page with `@odata.nextLink`. |
| `listen` | POST | `/subscriptions` | `{ changeType: "updated", notificationUrl, resource, expirationDateTime, clientState }`. Resource e.g. `/me/drive/root` (personal any folder; **business: root only**). |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `list_children` | no | Chooses the row above. |
| `auth_type` | select | yes | `oauth2_delegated` | no | `oauth2_delegated` \| `entra_app_only`. |
| `tenant_id` | string | yes | — | no | Entra tenant GUID or `common` / `consumers` (delegated personal). |
| `client_id` | string | yes | — | no | App registration id. |
| `client_secret` | password | yes | — | **yes** | App secret (or cert later — this node uses secret). |
| `refresh_token` | password | if delegated | — | **yes** | Delegated refresh token (`offline_access`). |
| `graph_base_url` | string | no | `https://graph.microsoft.com/v1.0` | no | National cloud Graph root if needed. |
| `drive_id` | string | no | — | no | If set, address `/drives/{drive_id}` instead of `/me/drive`. |
| `user_id` | string | app-only without `drive_id` | — | no | `/users/{user_id}/drive`. |
| `conflict_behavior` | select | no | `fail` | no | `fail` \| `replace` \| `rename` (`@microsoft.graph.conflictBehavior`). |
| `listen_notification_url` | string | `listen` | — | no | HTTPS ingress URL. |
| `timeout_seconds` | number | no | `60` | no | JSON. |
| `upload_timeout_seconds` | number | no | `300` | no | Content / session parts. |

File payloads use type `file` (platform ref-id), never inline bytes, on both this table and the ports below.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets. |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or `clientState`. |

FUNCTION ports: none.

<!-- CONTINUE -->
