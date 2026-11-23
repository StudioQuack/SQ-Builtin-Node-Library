# Harvest

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/harvest/api_spec.json`.

## Overview

Official Harvest API v2. extraHeaders send required `Harvest-Account-Id: {account_id}` and identifying `User-Agent: StudioQuack (https://studioquack.com)` (https://help.getharvest.com/api-v2/authentication-api/authentication/authentication/).

- **Official API:** Harvest API v2
- **Docs:** https://help.getharvest.com/api-v2/
- **Researched:** 2026-08-22
- **Auth:** `bearer` bound to configuration `access_token` (password / secret). Never place credentials on INPUT.
- **Folder:** `nodes/harvest/`

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `client_create` | POST | `https://api.harvestapp.com/v2/clients` | Official |
| `client_get` | GET | `https://api.harvestapp.com/v2/clients/{id}` | Official |
| `client_list` | GET | `https://api.harvestapp.com/v2/clients` | Official |
| `client_update` | PATCH | `https://api.harvestapp.com/v2/clients/{id}` | Official |
| `client_delete` | DELETE | `https://api.harvestapp.com/v2/clients/{id}` | Official |
| `contact_create` | POST | `https://api.harvestapp.com/v2/contacts` | Official |
| `contact_get` | GET | `https://api.harvestapp.com/v2/contacts/{id}` | Official |
| `contact_list` | GET | `https://api.harvestapp.com/v2/contacts` | Official |
| `contact_update` | PATCH | `https://api.harvestapp.com/v2/contacts/{id}` | Official |
| `contact_delete` | DELETE | `https://api.harvestapp.com/v2/contacts/{id}` | Official |
| `company_get` | GET | `https://api.harvestapp.com/v2/company` | Official |
| `estimate_create` | POST | `https://api.harvestapp.com/v2/estimates` | Official |
| `estimate_get` | GET | `https://api.harvestapp.com/v2/estimates/{id}` | Official |
| `estimate_list` | GET | `https://api.harvestapp.com/v2/estimates` | Official |
| `estimate_update` | PATCH | `https://api.harvestapp.com/v2/estimates/{id}` | Official |
| `estimate_delete` | DELETE | `https://api.harvestapp.com/v2/estimates/{id}` | Official |
| `expense_create` | POST | `https://api.harvestapp.com/v2/expenses` | Official |
| `expense_get` | GET | `https://api.harvestapp.com/v2/expenses/{id}` | Official |
| `expense_list` | GET | `https://api.harvestapp.com/v2/expenses` | Official |
| `expense_update` | PATCH | `https://api.harvestapp.com/v2/expenses/{id}` | Official |
| `expense_delete` | DELETE | `https://api.harvestapp.com/v2/expenses/{id}` | Official |
| `invoice_create` | POST | `https://api.harvestapp.com/v2/invoices` | Official |
| `invoice_get` | GET | `https://api.harvestapp.com/v2/invoices/{id}` | Official |
| `invoice_list` | GET | `https://api.harvestapp.com/v2/invoices` | Official |
| `invoice_update` | PATCH | `https://api.harvestapp.com/v2/invoices/{id}` | Official |
| `invoice_delete` | DELETE | `https://api.harvestapp.com/v2/invoices/{id}` | Official |
| `project_create` | POST | `https://api.harvestapp.com/v2/projects` | Official |
| `project_get` | GET | `https://api.harvestapp.com/v2/projects/{id}` | Official |
| `project_list` | GET | `https://api.harvestapp.com/v2/projects` | Official |
| `project_update` | PATCH | `https://api.harvestapp.com/v2/projects/{id}` | Official |
| `project_delete` | DELETE | `https://api.harvestapp.com/v2/projects/{id}` | Official |
| `task_create` | POST | `https://api.harvestapp.com/v2/tasks` | Official |
| `task_get` | GET | `https://api.harvestapp.com/v2/tasks/{id}` | Official |
| `task_list` | GET | `https://api.harvestapp.com/v2/tasks` | Official |
| `task_update` | PATCH | `https://api.harvestapp.com/v2/tasks/{id}` | Official |
| `task_delete` | DELETE | `https://api.harvestapp.com/v2/tasks/{id}` | Official |
| `time_entry_create_duration` | POST | `https://api.harvestapp.com/v2/time_entries` | Official |
| `time_entry_create_start_end` | POST | `https://api.harvestapp.com/v2/time_entries` | Official |
| `time_entry_get` | GET | `https://api.harvestapp.com/v2/time_entries/{id}` | Official |
| `time_entry_list` | GET | `https://api.harvestapp.com/v2/time_entries` | Official |
| `time_entry_update` | PATCH | `https://api.harvestapp.com/v2/time_entries/{id}` | Official |
| `time_entry_delete` | DELETE | `https://api.harvestapp.com/v2/time_entries/{id}` | Official |
| `time_entry_restart` | PATCH | `https://api.harvestapp.com/v2/time_entries/{id}/restart` | Official |
| `time_entry_stop` | PATCH | `https://api.harvestapp.com/v2/time_entries/{id}/stop` | Official |
| `user_create` | POST | `https://api.harvestapp.com/v2/users` | Official |
| `user_get` | GET | `https://api.harvestapp.com/v2/users/{id}` | Official |
| `user_list` | GET | `https://api.harvestapp.com/v2/users` | Official |
| `user_update` | PATCH | `https://api.harvestapp.com/v2/users/{id}` | Official |
| `user_delete` | DELETE | `https://api.harvestapp.com/v2/users/{id}` | Official |
| `user_me` | GET | `https://api.harvestapp.com/v2/users/me` | Official |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — `operation` plus per-op fields | File payloads use type `file` (ref-id) |
| `output` | OUTPUT | `{ status, http_status, data }` or `{ error }` | Failures never include secrets |

FUNCTION ports: none.

## Configuration parameters

`operation` (select), `access_token` (password), `account_id`, `timeout_seconds`. Create/update take official Harvest fields (`name`, `address`, `currency`, `bill_by`, `budget_by`, `user_id`, `issue_date`, `due_date`, …).

## Legal

- Terms: https://www.getharvest.com/terms-of-service
- Official-API-first: no scrape, no unofficial wrappers.
- PII may leave the sandbox toward the provider. The operator is the data controller.
- `listen` / `webhook_listen` are not a StudioQuack Webhook node — use a BPMN message event delivered through the StudioQuack engine's message-event ingress.

## Implementation notes

- JDK HTTP via OfficialApiNode (no vendor SDK).
- HTTP 4xx/5xx raise a BPMN error (`HTTP_<status>` / `AUTH_FAILED` / `RATE_LIMITED`).
- extraHeaders are applied after auth. Values support `{configKey}` (input, then node config).
