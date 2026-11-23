# Microsoft Entra ID

Official **Microsoft Graph REST v1.0** users and groups (commercial default). Handler: `OfficialApiNode` via `api_spec.json`. Customer owns the Entra tenant.

- **Docs:** https://learn.microsoft.com/en-us/graph/api/resources/user?view=graph-rest-1.0
- **Groups:** https://learn.microsoft.com/en-us/graph/api/resources/group?view=graph-rest-1.0
- **Base:** `https://graph.microsoft.com/v1.0`
- **Auth:** `Authorization: Bearer {access_token}` (already minted; delegated or application). Never log `access_token` or `passwordProfile.password`.

National clouds (US Government, China) use different Graph hosts; this package pins the commercial root.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-user` | POST | `/users` |
| `get-user` | GET | `/users/{userId}` |
| `list-users` | GET | `/users` |
| `update-user` | PATCH | `/users/{userId}` |
| `delete-user` | DELETE | `/users/{userId}` |
| `add-user-to-group` | POST | `/groups/{groupId}/members/$ref` body `{ "@odata.id": "https://graph.microsoft.com/v1.0/directoryObjects/{id}" }` |
| `remove-user-from-group` | DELETE | `/groups/{groupId}/members/{userId}/$ref` |
| `create-group` | POST | `/groups` |
| `get-group` | GET | `/groups/{groupId}` |
| `list-groups` | GET | `/groups` |
| `update-group` | PATCH | `/groups/{groupId}` |
| `delete-group` | DELETE | `/groups/{groupId}` |

`remove-user-from-group` **must** keep `/$ref`. Omitting it can delete the directory object when the app also has object-delete permissions.

Typical permissions (admin consent): `User.ReadWrite.All`, `Group.ReadWrite.All`, `GroupMember.ReadWrite.All`. Personal Microsoft accounts are not supported for these directory APIs.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Graph Bearer token |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes `access_token`.

## Legal

- Microsoft APIs Terms of Use: https://learn.microsoft.com/legal/microsoft-apis/terms-of-use
- Online Services terms: https://www.microsoft.com/licensing/terms
- Directory users/groups are personal data. The customer is the controller of the tenant. StudioQuack does not accept Microsoft’s terms on the customer’s behalf.
- Official Graph v1.0 only. Do not call undocumented Admin Center HTML.

Never log `access_token` or passwords.
