# WordPress

Official WordPress REST API `wp/v2` (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_WORDPRESS`). Dispatched by `OfficialApiNode` from `api_spec.json`.

- **Self-host handbook:** https://developer.wordpress.org/rest-api/
- **Application Passwords:** https://developer.wordpress.org/advanced-administration/security/application-passwords/
- **WordPress.com REST:** https://developer.wordpress.com/docs/api/
- **Default baseUrl:** `https://public-api.wordpress.com/wp/v2/sites/{site}`
- **Auth:** HTTP Basic — `wp_username` + `application_password` (StudioQuack secrets). Never on INPUT.

Packaged `baseUrl` is `https://public-api.wordpress.com/wp/v2/sites/{site}` (https://developer.wordpress.com/docs/api/getting-started/). Config `site` is the numeric site id (recommended) or domain. Paths remain `/posts`, `/pages`, `/users` relative to that base.

- **WordPress.com:** use the packaged host + `site`. Official WP.com auth is OAuth2 Bearer; this node still sends HTTP Basic (`wp_username` + Application Password). Application Passwords are not used directly on `public-api.wordpress.com` — exchange them for a Bearer token if WP.com rejects Basic.
- **Self-host:** `https://{origin}/wp-json/wp/v2` with Application Passwords (https://developer.wordpress.org/rest-api/). HTTPS required.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `post_create` / `post_update` | POST | `/posts` · `/posts/{post_id}` |
| `post_get` / `post_list` | GET | `/posts/{post_id}` · `/posts` (`per_page`, `page`, `search`) |
| `page_create` / `page_update` | POST | `/pages` · `/pages/{page_id}` |
| `page_get` / `page_list` | GET | `/pages/{page_id}` · `/pages` (`per_page`, `page`) |
| `user_create` / `user_update` | POST | `/users` · `/users/{user_id}` |
| `user_get` / `user_list` | GET | `/users/{user_id}` · `/users` (`per_page`) |
| `trigger` | ingress | Not a socket — use a BPMN message event |

`user_create` / `user_update` need a role with `create_users` / `edit_users`. WP updates use **POST**, not PUT.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `wp_username` | string | no | Basic user |
| `application_password` | password | yes | Application Password (strip spaces) |
| `site` | string | yes | WP.com site id/domain for `/wp/v2/sites/{site}` |
| `timeout_seconds` | number | no | Default 30 |

## Legal

- REST authentication: https://developer.wordpress.org/rest-api/using-the-rest-api/authentication/
- WP.com responsible use: https://developer.wordpress.com/docs/api/guidelines-for-responsible-use-of-automattics-apis/
- Users/email are PII. Never log passwords. Official REST only (no XML-RPC, no wp-admin scrape).
