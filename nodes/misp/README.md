# MISP

Official [MISP OpenAPI](https://www.misp-project.org/openapi/) / Automation API. Customer-hosted instance. Auth: `Authorization: {api_key}` (raw key, no Bearer). Event/attribute payloads are often personal or law-enforcement sensitive. Honor sharing groups and TLP.

- **Docs:** https://www.misp-project.org/openapi/
- **Base:** `https://{mispHost}`
- **Auth:** header `Authorization` prefix empty, configKey `api_key`
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `create-attribute` | POST | `{mispHost}/attributes/add/{eventId}` |
| `get-attribute` | GET | `{mispHost}/attributes/view/{attributeId}` |
| `list-attributes` | GET | `{mispHost}/attributes` |
| `search-attributes` | POST | `{mispHost}/attributes/restSearch` |
| `update-attribute` | PUT | `{mispHost}/attributes/edit/{attributeId}` |
| `delete-attribute` | DELETE | `{mispHost}/attributes/delete/{attributeId}` |
| `create-event` | POST | `{mispHost}/events/add` |
| `get-event` | GET | `{mispHost}/events/view/{eventId}` |
| `list-events` | GET | `{mispHost}/events` |
| `search-events` | POST | `{mispHost}/events/restSearch` |
| `update-event` | PUT | `{mispHost}/events/edit/{eventId}` |
| `delete-event` | DELETE | `{mispHost}/events/delete/{eventId}` |
| `publish-event` | POST | `{mispHost}/events/publish/{eventId}` |
| `unpublish-event` | POST | `{mispHost}/events/unpublish/{eventId}` |
| `add-event-tag` | POST | `{mispHost}/events/addTag/{eventId}/{tagId}/local:{local}` |
| `remove-event-tag` | POST | `{mispHost}/events/removeTag/{eventId}/{tagId}` |
| `create-feed` | POST | `{mispHost}/feeds/add` |
| `get-feed` | GET | `{mispHost}/feeds/view/{feedId}` |
| `list-feeds` | GET | `{mispHost}/feeds` |
| `update-feed` | PUT | `{mispHost}/feeds/edit/{feedId}` |
| `enable-feed` | POST | `{mispHost}/feeds/enable/{feedId}` |
| `disable-feed` | POST | `{mispHost}/feeds/disable/{feedId}` |
| `get-galaxy` | GET | `{mispHost}/galaxies/view/{galaxyId}` |
| `list-galaxies` | GET | `{mispHost}/galaxies` |
| `delete-galaxy` | DELETE | `{mispHost}/galaxies/delete/{galaxyId}` |
| `get-noticelist` | GET | `{mispHost}/noticelists/view/{noticelistId}` |
| `list-noticelists` | GET | `{mispHost}/noticelists` |
| `search-objects` | POST | `{mispHost}/objects/restsearch` |
| `create-org` | POST | `{mispHost}/admin/organisations/add` |
| `get-org` | GET | `{mispHost}/organisations/view/{organisationId}` |
| `list-orgs` | GET | `{mispHost}/organisations` |
| `update-org` | PUT | `{mispHost}/admin/organisations/edit/{organisationId}` |
| `delete-org` | DELETE | `{mispHost}/admin/organisations/delete/{organisationId}` |
| `create-tag` | POST | `{mispHost}/tags/add` |
| `list-tags` | GET | `{mispHost}/tags` |
| `update-tag` | POST | `{mispHost}/tags/edit/{tagId}` |
| `delete-tag` | POST | `{mispHost}/tags/delete/{tagId}` |
| `create-user` | POST | `{mispHost}/admin/users/add` |
| `get-user` | GET | `{mispHost}/admin/users/view/{userId}` |
| `list-users` | GET | `{mispHost}/admin/users` |
| `update-user` | PUT | `{mispHost}/admin/users/edit/{userId}` |
| `delete-user` | DELETE | `{mispHost}/admin/users/delete/{userId}` |
| `get-warninglist` | GET | `{mispHost}/warninglists/view/{warninglistId}` |
| `list-warninglists` | GET | `{mispHost}/warninglists` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `mispHost` | string | no | Host without scheme |
| `api_key` | password | yes | Authorization: {api_key} |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes secrets.

## Catalog mapping (OpenAPI 2.5, fetched 2026-08-20)

Attributes: `POST /attributes/add/{eventId}`, `GET /attributes/view/{attributeId}`, `GET /attributes`, `POST /attributes/restSearch`, `PUT /attributes/edit/{attributeId}`, `DELETE /attributes/delete/{attributeId}`.

Events: `POST /events/add`, `GET /events/view/{eventId}`, `GET /events`, `POST /events/restSearch`, `PUT /events/edit/{eventId}`, `DELETE /events/delete/{eventId}`, `POST /events/publish/{eventId}`, `POST /events/unpublish/{eventId}`, `POST /events/addTag/{eventId}/{tagId}/local:{local}`, `POST /events/removeTag/{eventId}/{tagId}`.

Feeds / galaxies / noticelists / objects / orgs / tags / users / warninglists: matching OpenAPI paths (`/objects/restsearch` is lowercase in the spec). Org/user mutations use `/admin/…`.

YAML: https://raw.githubusercontent.com/MISP/MISP/develop/app/webroot/doc/openapi.yaml
