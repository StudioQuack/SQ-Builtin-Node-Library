# SAML / OAuth2 Tokens

OAuth 2.0 **HTTP** utilities (not a vendor IdP admin API). Cite the RFCs, not vendor blogs. Token, introspection, and revocation URLs must be **HTTPS** (RFC 6749 §3.1 / §3.2; RFC 7662 §2; RFC 7009 §2).

- **Docs:** https://www.rfc-editor.org/rfc/rfc6749.html
- **Base:** `https://{host}{path}`
- **Auth:** basic `client_id` + `client_secret` (password)
- **Handler:** `OfficialApiNode` via `api_spec.json`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `oauth-authorize-url` | GET | `{authHost}{authPath}` |
| `oauth-exchange-code` | POST | `{tokenHost}{tokenPath}` |
| `oauth-client-credentials` | POST | `{tokenHost}{tokenPath}` |
| `oauth-refresh` | POST | `{tokenHost}{tokenPath}` |
| `oauth-introspect` | POST | `{introspectionHost}{introspectionPath}` |
| `oauth-revoke` | POST | `{revokeHost}{revokePath}` |

## Configuration parameters

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | OAuth HTTP verbs |
| `client_id` | string | no | Basic username / client_id |
| `client_secret` | password | yes | Basic password |
| `authHost / authPath` | string | no | Authorize URL parts |
| `tokenHost / tokenPath` | string | no | Token URL parts |
| `introspectionHost / introspectionPath` | string | no | RFC 7662 |
| `revokeHost / revokePath` | string | no | RFC 7009 |
| `timeout_seconds` | number | no | Default 20 |

## Ports

`input` / `output` only. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## RFCs

- [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749.html) OAuth 2.0
- [RFC 6750](https://www.rfc-editor.org/rfc/rfc6750.html) Bearer
- [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662.html) Token Introspection
- [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009.html) Token Revocation
- [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636.html) PKCE (optional `code_verifier` on exchange)

Not implemented: RFC 6749 implicit grant (§4.2), resource-owner password grant (§4.3), device authorization (RFC 8628).

## Not executable on OfficialApiNode (local crypto)

`saml-decode-assertion` and `saml-validate-assertion` (OASIS SAML 2.0 Core) require XML parsing and XML-DSig. OfficialApiNode is HTTP only. They are **omitted from `api_spec.json`**. Do not treat an unsigned decode as authentication.

## Engine gaps

- OfficialApiNode JSON-serializes POST bodies even when `contentType` is `application/x-www-form-urlencoded` (RFC token requests are form-urlencoded).
- Path placeholders are URL-encoded. A path such as `/oauth/token` in `tokenPath` becomes `%2Foauth%2Ftoken`. Prefer a reverse proxy that exposes a single-segment path, or a host that does not need extra path segments in the placeholder.
- `oauth-authorize-url` performs an HTTP GET (OfficialApiNode always requests). Browser redirects are not modeled.
- HTTP Basic is applied on every verb, including authorize GET (engine has one auth block).
