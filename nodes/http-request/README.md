# HTTP Request

## Overview

HTTP Request is a **generic HTTP client** for StudioQuack workflows. The in-process handler `com.studioquack.nodelibrary.nodes.web.HttpRequestNode` uses JDK `java.net.http.HttpClient` (not Python `requests`). It sends one allowlisted method to an absolute `http`/`https` URL with optional headers and a JSON or text body, then returns port **`output`** as a **map** `{ result, http_status }` on 2xx (structured fields for Duck Tape / AI Agent tools — not a JSON string on the port).

Use **Web Browser** when you need stripped page text and robots.txt. Use **Web Search** when you only have a query. Use this node when you need a specific verb, custom headers, or a request body.

This is not a branded SaaS connector. It speaks HTTP as specified in [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html) / [RFC 9112](https://www.rfc-editor.org/rfc/rfc9112.html). Methods other than `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` are rejected (the Python tool forwarded arbitrary verbs). TLS verification off is **lab-only**.

| | |
| --- | --- |
| Designer name | HTTP Request |
| `templateId` / definition `_id` | `30f59c3b-afa1-463a-9a60-9cda6ccae790` |
| Image env override | `SQ_TEMPLATE_ID_HTTP_REQUEST` |
| Handler | `com.studioquack.nodelibrary.nodes.web.HttpRequestNode` |
| Catalog folder | `nodes/http-request` |

Protocol docs:

- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [MDN HTTP request methods](https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods)

## Ports

The daemon delivers port data as `inputVariables.input`, or as `executionContext.currentNode.inputList[]` with `targetPortValue=input`. A one-element list is unwrapped. Success returns `{result, http_status}` on 2xx. Failures raise a BPMN error. Credential header values are redacted from error messages.

### `input` (INPUT)

| Field | Type | Required | Schema / accepted values | How it is used |
| --- | --- | --- | --- | --- |
| `url` | string | **yes** | Absolute `http://` or `https://` URL. Path, query, and port allowed. Trimmed. Empty / missing / whitespace-only → `INVALID_INPUT` `URL is missing` (no request). Missing or non-http(s) scheme → `INVALID_INPUT` `HTTP Request Error: unsupported URL scheme: {scheme\|(none)}`. Malformed URI → `INVALID_INPUT` `HTTP Request Error: {message}`. | Passed to JDK `HttpClient` as the request URI. Not a crawler and not a search box. |
| `method` | string | no | Case-insensitive, stored upper-case. Exact allowlist: `GET` (default when omitted/blank), `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. Any other verb (`TRACE`, `CONNECT`, `PROPFIND`, custom tokens) → `INVALID_INPUT` `HTTP Request Error: method not allowed: {METHOD}` and **is not sent**. | HTTP verb on the wire. |
| `headers` | object | no | JSON object of string→string, a JSON object string, or omit/`{}`. Invalid JSON → `{}`. | Merged **on top of** configuration `defaultHeaders` (call keys win). `Authorization` values are sent but never logged. After merge, if `User-Agent` is missing (case-insensitive), Chrome/120 is set **and only then** missing `Accept` / `Accept-Language` are filled (Python parity). |
| `body_text` | string | no | Any Java/JSON string, including empty. Numbers/objects on this field are ignored. | Raw UTF-8 body when no JSON body resolved. `Content-Type` is **not** auto-set — set it in `headers` (`application/x-www-form-urlencoded`, `text/plain`, `application/xml`, …). |
| `body` | object / string / scalar | no | Dict → JSON body. String → text body (if `body_text` is not a string). Any other non-null non-dict → `str(body)` as UTF-8 text. | A dict is serialized with `Json.write`; if `Content-Type` is missing after header merge, `application/json` is added. If both JSON and text bodies resolve, **only JSON is sent** (one `HttpClient` publisher). |

Default `User-Agent` when missing (same as Web Browser):

```text
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```

Filled only in that same case: `Accept` = `text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8`; `Accept-Language` = `en-US,en;q=0.9`.

### `output` (OUTPUT)

The port value is an **object map**:

| Field | Type | Required | Schema | What flows |
| --- | --- | --- | --- | --- |
| `result` | string | yes | 2xx preview | Human-readable 2xx response. No stack traces. |
| `http_status` | number | no | Parsed from the `Status:` line in `result` | Present on 2xx. |

Success shape, exactly:

```text
HTTP {METHOD} {url}
Status: {status}
Content-Type: {content_type}

Body:
{body_preview}
```

- `{METHOD}` — allowlisted upper-case verb
- `{url}` — the **input** URL (not necessarily the post-redirect URI)
- `{status}` — numeric status code
- `{content_type}` — response `Content-Type` header lower-cased (empty if missing)
- `{body_preview}` — if the bytes parse as a JSON object or array they are re-serialized compactly with `Json.write`; otherwise raw text. Longer than 200_000 characters → cut and suffixed `... [truncated]`. A byte-capped read also appends `... [truncated]`.

Failures raise a BPMN error (credential headers redacted):

| Condition | Code / message |
| --- | --- |
| Missing / blank `url` | `INVALID_INPUT` `URL is missing` |
| Method not in the allowlist | `INVALID_INPUT` `HTTP Request Error: method not allowed: TRACE` |
| Scheme missing or not `http`/`https` | `INVALID_INPUT` `HTTP Request Error: unsupported URL scheme: {scheme\|(none)}` |
| Connect / TLS / timeout / I/O | `REQUEST_FAILED` `HTTP Request Error: {message}` |
| Non-2xx | `AUTH_FAILED` / `RATE_LIMITED` / `HTTP_<status>` via `HTTP {status}` |
| Any other unexpected exception | `REQUEST_FAILED` `Error: {TypeName}: {message}` |

## Configuration parameters

Instance `configurationParameters`, keyed by `shortCode`.

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `timeoutSecondsDefault` | number | no | `20` | Number or numeric string. Blank / non-numeric → `20` (`toInt` never throws). Values below 1 are clamped to 1 by `WebNodeSupport.send`. | Connect + request timeout in seconds for every call (`HttpClient.connectTimeout` and `HttpRequest.timeout`). |
| `verifyTLSDefault` | select | no | `true` | Options `false` \| `true`. **Only the string `false`** (any case, surrounding whitespace ignored) disables certificate verification — matching Python `str(verify_cfg).lower() != 'false'`. `true`, `1`, `yes`, `0`, empty, and any other token all **verify**. | Whether to verify the server TLS certificate. Disabling verification is **lab-only** (self-signed lab hosts). Not for production. |
| `defaultHeaders` | string | no | empty | JSON object string, an already-decoded object/map, or empty. Invalid JSON → `{}`. | Headers applied to every call, then overridden by `input.headers` (call wins). Merged keys keep the casing given; `User-Agent` / `Accept` / `Accept-Language` presence is checked case-insensitively after the merge. `Authorization` values are never logged. Do not put secrets here if the instance config is shared — bind a secret and pass the header per call, or keep the instance private. |

## How it works

### Dispatch

`NodeLibraryApp.route` reads invoke `templateId` and looks up `NodeRegistry`. `WebNodeHandlers.register` wires this handler under `SQ_TEMPLATE_ID_HTTP_REQUEST` (wins) or definition `_id` `30f59c3b-afa1-463a-9a60-9cda6ccae790`. Unknown ids stay HTTP 200 with `error: unknown templateId`.

### HTTP

1. Read `timeoutSecondsDefault` (default 20) and `verifyTLSDefault`.
2. Upper-case `method`; default `GET`; reject anything outside the allowlist **before** connecting.
3. Merge `defaultHeaders` then `input.headers`; apply browser defaults if `User-Agent` is missing.
4. Require non-empty `url` with scheme `http` or `https`.
5. Resolve body: `body` dict → JSON UTF-8; else `body_text` string, else `body` string, else `str(body)` for other non-null non-dict. JSON wins if both resolve. Empty/null body → `BodyPublishers.noBody()`.
6. `WebNodeSupport.send`: JDK `HttpClient`, `HttpClient.Redirect.NORMAL`, connect + request timeout both `max(1, timeoutSecondsDefault)`, TLS per `verifyTLSDefault`, **response read capped at 200_000 bytes**.
7. JDK-forbidden request headers (`Host`, `Content-Length`, `Connection`, …) are skipped rather than failing the node.
8. Format the preview on 2xx. Non-2xx and transport exceptions raise a BPMN error.

Helper logs: `http-request {METHOD} timeoutSeconds=…` and `http-request done status=…` or `http-request failed: {SimpleName}`. Never URL query strings, never header values, never `helperToken`, never `Authorization`.

### Timeout / error mapping

Timeouts and I/O are caught around `send` and raised as `REQUEST_FAILED` `HTTP Request Error: {message}`. Unexpected exceptions become `Error: {TypeName}: {message}`. The daemon sandbox request budget (`sqs.sandbox.oci.requestTimeoutMs`, default 300 s) can still fail the whole step first if `timeoutSecondsDefault` is larger.

## Security & legal

- **Target-site terms.** This node does **not** consult robots.txt (that is Web Browser). The operator is responsible for every URL, method, and body the workflow sends: site terms of service, copyright, privacy, and applicable law. Fetch and mutate only resources the operator is allowed to access. StudioQuack does not grant rights to third-party APIs.
- **SSRF / egress.** The handler will call any `http`/`https` URL on the input port, including addresses that look like cloud metadata or RFC1918. Containment is the daemon’s sandbox network posture, set per engine and not changeable by any node or workflow: outbound internet is allowed and the private network is denied. On a `DEGRADED` host the daemon reports that its host rules are not applied; that is a host fact, not a node bypass.
- **TLS.** Default verifies certificates. `verifyTLSDefault=false` is lab-only for self-signed hosts. It is not a production setting.
- **Secrets.** Tokens belong in instance `configurationParameters` (`defaultHeaders`) or a per-call `headers` object fed from a StudioQuack secret — never in logs, never interpolated into `result` by this handler as a dedicated field. `Authorization` values are sent and never logged. Do not put secrets in a shared instance’s `defaultHeaders`.
- **DoS bounds.** HTTP read cap 200_000 bytes; preview cap 200_000 characters; connect+request timeout default 20 s. These exist so a hostile origin cannot fill the sandbox heap.
- **Verb allowlist.** `TRACE` and `CONNECT` are rejected so the node cannot be used as an HTTP proxy primitive or for TRACE-based header reflection.

## Examples

### GET JSON

**Input:**

```json
{
  "method": "GET",
  "url": "https://httpbin.org/get",
  "headers": {
    "Accept": "application/json"
  }
}
```

**Output** (port `output` is this JSON **string**):

```json
{"result":"HTTP GET https://httpbin.org/get\nStatus: 200\nContent-Type: application/json\n\nBody:\n{\"url\":\"https://httpbin.org/get\"}"}
```

Decoded `result` text:

```text
HTTP GET https://httpbin.org/get
Status: 200
Content-Type: application/json

Body:
{"url":"https://httpbin.org/get"}
```

### POST JSON body

**Input:**

```json
{
  "method": "POST",
  "url": "https://httpbin.org/post",
  "headers": {
    "Accept": "application/json",
    "X-Request-Id": "abc-123"
  },
  "body": {
    "query": "bpmn",
    "max_results": 5
  }
}
```

The handler adds `Content-Type: application/json` (missing after merge), serializes `body`, and returns a preview whose `Status:` line reflects the origin (200 on httpbin). HTTP 404/500 from the origin raise a BPMN error.

### Missing URL

**Input:** `{"method":"GET"}`

Raises `INVALID_INPUT` with message `URL is missing`.
