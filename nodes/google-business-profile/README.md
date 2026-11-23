# Google Business Profile

Official [Business Profile APIs](https://developers.google.com/my-business). Handler: `OfficialApiNode` via `api_spec.json`. Local listings — not a social graph.

- **Docs:** https://developers.google.com/my-business · [Get location (v4)](https://developers.google.com/my-business/reference/rest/v4/accounts.locations/get) · [Prerequisites](https://developers.google.com/my-business/content/prereqs)
- **Default `baseUrl`:** `https://mybusiness.googleapis.com/v4` (reviews, localPosts)
- **location_get:** per-op `baseUrl` `https://mybusinessbusinessinformation.googleapis.com`, path `/v1/locations/{location_id}`, query `readMask` (https://developers.google.com/my-business/reference/businessinformation/rest/v1/locations/get)
- **Auth:** OAuth bearer `access_token`. Scope `https://www.googleapis.com/auth/business.manage`. Engine does not refresh tokens.
- **Access:** GBP is not open by enabling the API. Submit the [access request](https://developers.google.com/my-business/content/prereqs). Unapproved Cloud projects show **0 QPM** (calls fail, often 429). Approved default is typically 300 QPM.

## Operations

| operation | HTTP | Path (v4) |
|---|---|---|
| `location_get` | GET | `https://mybusinessbusinessinformation.googleapis.com/v1/locations/{location_id}` |
| `review_list` | GET | `/accounts/{account_id}/locations/{location_id}/reviews` |
| `review_reply` | PUT | `…/reviews/{review_id}/reply` body `comment` |
| `post_create` | POST | `…/localPosts` body `languageCode`, `summary`, `topicType` |
| `trigger` | ingress | BPMN message event — no socket |

`location_get` is the Business Information v1 GetLocation (no `accounts/{account_id}`). Reviews and localPosts remain on My Business v4.

`post_create` only sends `languageCode`, `summary`, `topicType`. Prefer `topicType=STANDARD`. EVENT/OFFER need extra objects this node does not send.

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_token` | password | yes | Bearer |
| `account_id` | string | no | Path fallback |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted. 

## Legal

- Customer owns the Cloud project, OAuth grant, and listings. Reviews and posts are public business content; treat reviewer names as personal data.
- [Google APIs Terms](https://developers.google.com/terms) · [GBP APIs](https://developers.google.com/my-business)
- Official APIs only. Do not scrape Maps or the Business Profile UI.
