# LingvaNex

Official LingvaNex Translator API (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_LINGVANEX`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Endpoint:** `POST https://api-b2b.backenster.com/b1/api/v3/translate`
- **Docs (2026-08-20):** [Overview](https://docs.lingvanex.com/reference/overview) · [Product](https://lingvanex.com/products/translationapi/) · [Language codes](https://docs.lingvanex.com/reference/user-guide)
- **Auth:** header `Authorization: {api_key}` (raw key, no `Bearer` prefix — as in LingvaNex’s own samples)
- **Body:** `platform` (exact `api`), `to`, `data`; optional `from`, `translateMode` (`html`), `enableTransliteration`

## Operations

| operation | HTTP | Path |
|---|---|---|
| `translate` | POST | `/translate` |

`from` / `to` use `language_COUNTRY` (`en_GB`, `es_ES`). Omit `from` to auto-detect.

## Ports

`input` / `output` only.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `api_key` | password | **yes** | Authorization raw key |
| `timeout_seconds` | number | no | Default 25 |

Create a key in the [LingvaNex dashboard](https://account.lingvanex.com/registration/).

## Legal

- Operator accepts LingvaNex Translator terms on lingvanex.com
- Source text leaves the host to LingvaNex. Minimize; do not send PHI without a covering DPA.
- Logs: operation, status, sizes, language codes — never `data` bodies, never `api_key`.
