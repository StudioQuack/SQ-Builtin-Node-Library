# Webflow

Official [Webflow Data API v2](https://developers.webflow.com/data/reference/rest-introduction). Handler: `OfficialApiNode` via `api_spec.json`. CMS collection items (staged) and site forms. Form submissions are respondent PII.

- **Docs:** https://developers.webflow.com/data/reference/rest-introduction · CMS · forms · webhooks
- **Base:** `https://api.webflow.com/v2`
- **Auth:** Bearer `access_token` (site token or OAuth / workspace token). Customer owns the Webflow site/workspace.

v1 (`api.webflow.com` without `/v2`) is retired for this node. Designer APIs are out of scope. Publishing is not a catalog verb.

## Default operation

When **INPUT** omits `operation` (and does not use a recognized `action` alias), the node uses the instance config `operation` (default **`site.list`**). Agent tool calls with an empty payload therefore list sites unless config overrides the verb.

## OAuth scopes

OAuth tokens must include the scopes required by the verb you call (Webflow returns HTTP 403 when a scope is missing): `sites:read` (`site.list`), `cms:read` / `cms:write` (CMS items), `forms:read` (forms and submissions), `ecommerce:read` (`order.list`, `sku.list`). Site tokens issued in the Webflow UI carry the permissions of that token.

## Pagination and filters

List operations that accept `limit` / `offset` validate locally before HTTP: `limit` is **1–100** (per Webflow), `offset` is **≥ 0**. `order.list` `status` must be one of: `pending`, `refunded`, `dispute-lost`, `fulfilled`, `disputed`, `unfulfilled`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `site.list` | GET | `/sites` |
| `item.list` | GET | `/collections/{collection_id}/items` |
| `item.get` | GET | `/collections/{collection_id}/items/{item_id}` |
| `item.create` | POST | `/collections/{collection_id}/items` |
| `item.update` | PATCH | `/collections/{collection_id}/items/{item_id}` |
| `item.delete` | DELETE | `/collections/{collection_id}/items/{item_id}` |
| `form.list` | GET | `/sites/{site_id}/forms` |
| `form.submission.list` | GET | `/sites/{site_id}/forms/{form_id}/submissions` |
| `order.list` | GET | `/sites/{site_id}/orders` |
| `sku.list` | GET | `/sites/{site_id}/products` |
| `trigger.form` | ingress | BPMN message event (`form_submission`) |
| `trigger.listen` / `trigger` | ingress | BPMN message event |

Underscore aliases `item_list`, `item_create`, `item_update`, `order_list`, `sku_list` match the ecommerce catalog row (same paths). `access_token` is the site token (catalog `api_token`).

`trigger.form` / `trigger.listen` do not open a socket. Register the HTTPS StudioQuack message-event URL in Webflow (`POST /v2/sites/{site_id}/webhooks`) from the Webflow UI or a one-off HTTP Request, then catch on a BPMN message event.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer site token or OAuth token |
| `site_id` | string | no | Default site id |
| `collection_id` | string | no | Default CMS collection id |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. CMS images/files: type `file` (ref-id). Failures: `{ error }` — never includes tokens or `formResponse`.

## Legal

- Customer owns the Webflow site and consent for form submissions and CMS records that hold PII. Minimize. Never log tokens or submission bodies.
- Rate limits (per token, per minute): Starter/Basic 60; CMS/eCommerce/Business 120; Enterprise custom. HTTP 429 + `Retry-After`.
- Official API only: `api.webflow.com/v2`. Do not scrape the Designer.
