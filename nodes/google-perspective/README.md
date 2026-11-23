# Google Perspective

Official Perspective Comment Analyzer (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GOOGLE_PERSPECTIVE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Comment Analyzer `v1alpha1`
- **Endpoint:** `POST https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze`
- **Docs (2026-08-20):** [Methods](https://developers.perspectiveapi.com/s/about-the-api-methods) · [Attributes](https://developers.perspectiveapi.com/s/about-the-api-attributes-and-languages) · [Product](https://www.perspectiveapi.com/)
- **Auth:** Google API key as `x-goog-api-key` (samples also use `?key=`; OfficialApiNode uses the header form).

## Scope

One operation: **`analyze`** (AnalyzeComment). Returns **scores only** (`attributeScores.*.summaryScore.value` in `[0,1]`). This node is not a ban engine, not a moderator, and not a legal determination of hate speech.

Set `requestedAttributes` on INPUT (typical: `{"TOXICITY":{}}`). Set `doNotStore: true` for private text or anyone under 13.

**Sunset:** Perspective remains in service until **2026-12-31**. New usage/quota requests closed after February 2026. Plan a replacement before that date.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `analyze` | POST | `/v1alpha1/comments:analyze` |

## Ports

`input` / `output` only.

## Configuration

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `api_key` | password | **yes** | `x-goog-api-key` |
| `timeout_seconds` | number | no | Default 25 |

## Legal

- Perspective / Jigsaw terms on [perspectiveapi.com](https://www.perspectiveapi.com/) and the Google Cloud project that owns the key
- Scores are probabilities that a reader would perceive an attribute — not proof of intent and not an automated decision under GDPR Art. 22 by themselves. A human policy must sit on top if you hide or escalate comments.
- `doNotStore: true` for private or under-13 content (required by the API docs).
- Logs: operation, status, sizes — never comment text, never `api_key`.
