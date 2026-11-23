# Notion

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/notion/api_spec.json`.

## Overview

Official Notion API (version `2026-03-11` on every call).

- **Docs:** https://developers.notion.com/reference/intro and https://developers.notion.com/reference/patch-page
- **Auth:** bearer `integration_token`. Pages/databases must be shared with the integration.
- **Trash:** `in_trash` (do not send `archived`).

## Operations

| operation | HTTP | Path | Body / query |
|---|---|---|---|
| `page_create` | POST | `/pages` | `parent`, `properties`, `children`, `icon`, `cover` |
| `page_get` | GET | `/pages/{page_id}` | |
| `page_update` | PATCH | `/pages/{page_id}` | `properties`, `icon`, `cover`, `in_trash` |
| `page_archive` | PATCH | `/pages/{page_id}` | `in_trash` |
| `page_search` | POST | `/search` | `query`, `filter`, `sort` |
| `database_get` | GET | `/databases/{database_id}` | |
| `database_page_create` | POST | `/pages` | `parent`, `properties`, `icon`, `cover` |
| `database_page_get` | GET | `/pages/{page_id}` | |
| `database_page_list` | POST | `/data_sources/{data_source_id}/query` | `filter`, `sorts`, `start_cursor`, `page_size` |
| `database_page_update` | PATCH | `/pages/{page_id}` | `properties`, `icon`, `cover`, `in_trash` |
| `block_append` | PATCH | `/blocks/{block_id}/children` | `children`, `position` |
| `block_list` / `block_get` | GET | `/blocks/{block_id}[/children]` | `start_cursor`, `page_size` |
| `user_get` / `user_list` | GET | `/users[/{user_id}]` | `start_cursor`, `page_size` |
| `data_source_get` | GET | `/data_sources/{data_source_id}` | |
| `data_source_search` | POST | `/search` | `query`, `filter` |
| `listen` | ingress | — | BPMN message event |

`properties` is the official per-schema property map (cannot be flattened without the data-source schema).
