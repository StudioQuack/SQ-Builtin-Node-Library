# Abstract API

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/abstract-api` |
| `templateId` | `cdcf3384-c0e1-52d2-9030-a074538339a2` |
| Env override | `SQ_TEMPLATE_ID_ABSTRACT_API` |
| Category | Data & Analytics |
| Official docs | https://docs.abstractapi.com |
| Auth | query `api_key`, one key per product (Abstract keys are not shared across APIs) — see below |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `ip_geolocate` | http GET | `ipgeolocation.abstractapi.com/v1/` |
| `phone_validate` | http GET | `phonevalidation.abstractapi.com/v1/` |
| `email_validate` | http GET | `emailvalidation.abstractapi.com/v1/` |
| `vat_validate` | http GET | `vat.abstractapi.com/v1/` |
| `exchange_live` | http GET | `exchange-rates.abstractapi.com/v1/live/` |
| `company_enrich` | http GET | `companyenrichment.abstractapi.com/v2` |
| `holiday_list` | http GET | `holidays.abstractapi.com/v1/` |

## Auth

Abstract issues a distinct key per API, so each operation reads its own password config, sent as query `api_key`. Only the key of the operation that runs is required; a blank key fails before HTTP.

| operation | config key |
|---|---|
| `ip_geolocate` | `ip_geolocation_api_key` |
| `phone_validate` | `phone_validation_api_key` |
| `email_validate` | `email_validation_api_key` |
| `vat_validate` | `vat_api_key` |
| `exchange_live` | `exchange_rates_api_key` |
| `company_enrich` | `company_enrichment_api_key` |
| `holiday_list` | `holidays_api_key` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }`.

## Legal

Per-product AbstractAPI terms and keys. Company Enrichment is Version 2. Phone/email are PII.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
