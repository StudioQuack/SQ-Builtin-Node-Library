# Google Translate

Official Cloud Translation Advanced (v3) node (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_GOOGLE_TRANSLATE`). Dispatched by `OfficialApiNode` from `api_spec.json`. No custom Java handler.

## Official API

- **Name / version:** Cloud Translation API v3 (Advanced). Researched 2026-08-20.
- **Origin:** `https://translate.googleapis.com`
- **Docs:** [REST](https://cloud.google.com/translate/docs/reference/rest) · [translateText](https://cloud.google.com/translate/docs/reference/rest/v3/projects/translateText) · [detectLanguage](https://cloud.google.com/translate/docs/reference/rest/v3/projects/detectLanguage) · [getSupportedLanguages](https://cloud.google.com/translate/docs/reference/rest/v3/projects/getSupportedLanguages) · [Authenticate](https://cloud.google.com/translate/docs/authentication)
- **Auth:** StudioQuack secret `access_token` → `Authorization: Bearer {access_token}`. OAuth scopes: `https://www.googleapis.com/auth/cloud-translation` or `https://www.googleapis.com/auth/cloud-platform`. Never on INPUT. Never logged.

v3 **does not support API keys**. Cloud Translation Basic (v2) accepts `?key=` / API keys; this node is v3 only. Bind a short-lived OAuth access token (service account JWT grant or user OAuth), not a Google API key.

## Scope

One node for Cloud Translation v3 text translate, language detect, and supported-language list. Path parent is `projects/{project}` (global). For a regional call, set `project` to `{project-id}/locations/{location-id}` so the path becomes `/v3/projects/{id}/locations/{loc}:translateText`.

Dropped: v2 Basic (`/language/translate/v2`), document translation, batch translate, romanizeText, glossary CRUD (glossary *application* on translate is `glossaryConfig` on `translate`).

## Operations

| operation | HTTP | Path | When to use |
|---|---|---|---|
| `translate` | POST | `/v3/projects/{project}:translateText` | Translate `contents[]` |
| `detect` | POST | `/v3/projects/{project}:detectLanguage` | Detect language of `content` |
| `languages` | GET | `/v3/projects/{project}/supportedLanguages` | List supported languages |

## Ports

| Port | Direction | Type | Notes |
|---|---|---|---|
| `input` | INPUT | object | Per-operation fields. No secrets. |
| `output` | OUTPUT | object | `{status, http_status, data}` . Secrets redacted. |

FUNCTION ports: none.

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Verb above. Default `translate`. |
| `access_token` | password | **yes** | OAuth bearer. |
| `project` | string | no | GCP project id or number (path `{project}`). |
| `timeout_seconds` | number | no | Default `30`. Floor 1. |

## Legal

- [Google Cloud Terms of Service](https://cloud.google.com/terms)
- [Cloud Translation service](https://cloud.google.com/terms/services)
- [Data Processing Addendum](https://cloud.google.com/terms/data-processing-addendum)
- Operator is the GCP customer. Translated text leaves the daemon toward Google. Attribution / Cloud ToS apply. DPIA if the workflow handles identifiable content.

## Implementation notes

- JDK HTTP via `OfficialApiNode`. Auth type `bearer` / `access_token`.
- OfficialApiNode does not send `x-goog-user-project`. If quota/billing requires it, a later handler revision must add that header (do not invent a second node).
- HTTP 4xx/5xx Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.  Never log `access_token`.

## Unverified

- Whether a regional `translate.googleapis.com` vs `translation.googleapis.com` host difference matters for v3 (both appear in Google docs; this node uses `translate.googleapis.com` from the method reference).
