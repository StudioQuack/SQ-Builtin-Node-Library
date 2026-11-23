# Web Search / DataForSEO SERP Context

## Overview

Web Search runs a **Tavily** web search and returns numbered title / URL / snippet lines for downstream reading or a **Web Browser** fetch. The designer label retains the historical “DataForSEO SERP Context” name; the in-process handler `com.studioquack.nodelibrary.nodes.web.WebSearchNode` talks to Tavily’s official HTTP API (or a Tavily-compatible proxy).

Use this node when you have a search **query** and need public URLs. Do not pass a known URL here — that is Web Browser. `include_answer` is accepted on the input schema but is **unused**: the handler never reads it and never sends it (Python never did either).

### Tavily API

Official product: [Tavily Search](https://docs.tavily.com/documentation/api-reference/endpoint/search), a search engine aimed at LLM / agent workflows. Base URL: `https://api.tavily.com`.

| Endpoint | Method | This node |
| --- | --- | --- |
| `/search` | `POST` | **Yes** — the only call. Handler POSTs `{baseUrl}/search`. |
| `/extract` | `POST` | No — use Web Browser (or Tavily Extract outside this node). |
| `/crawl`, `/map` | `POST` | No |
| `/research` | `POST` | No |

Authentication (Tavily): API keys from [app.tavily.com](https://app.tavily.com). Official docs show `Authorization: Bearer tvly-…`. This handler authenticates by putting `api_key` in the **JSON body** only (Tavily-compatible; not copied into headers, logs, or output). Request body fields sent: `api_key`, `query`, `max_results`. `search_depth` is omitted, so Tavily’s default `basic` applies (1 API credit per [credits docs](https://docs.tavily.com/documentation/api-credits)).

Published Tavily plans (confirm live numbers at [billing](https://app.tavily.com/billing) / [API credits](https://docs.tavily.com/documentation/api-credits)):

| Plan | Typical monthly credits |
| --- | --- |
| Researcher (Free) | 1,000 — no credit card |
| Project | 4,000 |
| Bootstrap | 15,000 |
| Startup | 38,000 |
| Growth | 100,000 |
| Pay as you go | per-credit after the plan cap |
| Enterprise | custom |

Search credit cost: `basic` / `fast` / `ultra-fast` = 1 credit; `advanced` = 2 credits. HTTP 429 from the provider raises `RATE_LIMITED` (message includes a 60s retry hint).

Provider docs:

- [Tavily API introduction](https://docs.tavily.com/documentation/api-reference/introduction)
- [POST /search](https://docs.tavily.com/documentation/api-reference/endpoint/search)
- [Credits & pricing](https://docs.tavily.com/documentation/api-credits)
- [FAQ / plans](https://docs.tavily.com/faq/faq)
- [Platform Terms of Service](https://www.tavily.com/terms)
- [Acceptable Use Policy](https://www.tavily.com/acceptable-use-policy)

| | |
| --- | --- |
| Designer name | Web Search / DataForSEO SERP Context |
| `templateId` / definition `_id` | `e154fd82-f17e-4a8e-a457-7334cee69d24` |
| Image env override | `SQ_TEMPLATE_ID_WEB_SEARCH` |
| Handler | `com.studioquack.nodelibrary.nodes.web.WebSearchNode` |
| Catalog folder | `nodes/web-search` |

## Ports

The daemon delivers port data as `inputVariables.input`, or as `executionContext.currentNode.inputList[]` with `targetPortValue=input`. A one-element list is unwrapped. Success returns `{"output": {"result": <string>}}`. Failures raise a BPMN error. `result` never contains `api_key`.

### `input` (INPUT)

One search request. The handler reads these three shortCodes only.

| Field | Type | Required | Schema / accepted values | How it is used |
| --- | --- | --- | --- | --- |
| `query` | string | **yes** | Any non-empty Unicode string (keywords, a question, or a provider operator string). Trimmed. | Sent as Tavily JSON field `query` on `POST {baseUrl}/search`. Empty / missing / whitespace-only → `INVALID_INPUT` `Query is missing` and **no HTTP call**. Not a URL fetch. Do not put `api_key` here. |
| `max_results` | number | no | Positive integers (`1`, `2`, …). JSON numbers and numeric strings parse. Empty / missing / non-numeric / `<= 0` → configuration `maxResultsDefault` (default `5`). Schema value `0` means “use default”, not “return zero hits”. | Caps how many organic hits are formatted into `result`. The handler slices the provider `results` array to this many items. Keep small — each hit is `{n}. {title} - {url}\n{snippet}`. |
| `include_answer` | boolean | no | `true` \| `false` (JSON boolean or boolean-like). Default `false`. | **Accepted but unused.** Not read; not sent. On success `result` is numbered lines or `No results found.` |

### `output` (OUTPUT)

Single field `result` on success. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

| Field | Type | Required | Schema | What flows |
| --- | --- | --- | --- | --- |
| `result` | string | no | Numbered hits or `No results found.` | Search outcome. Never includes `api_key`. |

**Success `result` strings**

| When | Exact / format |
| --- | --- |
| Success with hits | Numbered blocks `{n}. {title or url} - {url}\n{snippet}` joined by a blank line. `title` empty → URL used as title. Snippet is `content`, else `snippet`; newlines in snippets become spaces. |
| Success with zero organic hits | Literal `No results found.` |

HTTP 429 raises `RATE_LIMITED` (message includes `HTTP 429` and a 60s retry hint). HTTP `>=500` raises `HTTP_<status>` with a 30s retry hint. Timeout raises `REQUEST_FAILED` mentioning `timed out` and a 30s retry hint.

## Configuration parameters

Instance `configurationParameters`, keyed by `shortCode`.

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `api_key` | password | **yes** | empty | Provider secret (`tvly-…` or a compatible proxy key). Empty / missing / whitespace-only raises `INVALID_INPUT` `Missing Tavily API Key. Provide it in node parameters (apiKey).` and does not call the network. | Authenticates `POST {baseUrl}/search`. Sent **only** as JSON field `api_key`. Bind a StudioQuack secret; do not commit a literal. **Never logged, never echoed, never returned.** |
| `baseUrl` | string | no | `https://api.tavily.com` | Absolute `http` or `https` origin. Trailing `/` characters are stripped. Empty/missing → default. `ftp` / `file` / `data` / `javascript` / schemeless → `INVALID_INPUT` `baseUrl must be http or https`. Do not append `/search` — the handler adds it. | Search API origin. Must be reachable from the sandbox. Example proxy: `https://search-proxy.example.com`. |
| `maxResultsDefault` | number | no | `5` | Positive integers. `0`, negative, empty, or non-numeric → `5`. | Instance default when `input.max_results` is omitted, empty, non-numeric, or `<= 0`. `input.max_results` overrides per invoke when it parses to an integer `> 0`. HTTP body is capped at ~1_000_000 bytes. |
| `timeout_seconds` | number | no | `25` | Positive integers. `0`, negative, empty, or non-numeric → `25`. HTTP helper floors at 1 second. | Connect + request timeout for `POST {baseUrl}/search` (Java honors this; the Python tool hardcoded 25). Keep at or below the daemon sandbox request budget (`sqs.sandbox.oci.requestTimeoutMs`, default 300_000 ms) or the daemon fails the node first. On expiry the node raises `REQUEST_FAILED` mentioning `timed out` and a 30s retry hint. |

## How it works

### Dispatch

`NodeLibraryApp.route` reads invoke `templateId` and looks up `NodeRegistry`. `WebNodeHandlers.register` wires this handler under `SQ_TEMPLATE_ID_WEB_SEARCH` (wins) or definition `_id` `e154fd82-f17e-4a8e-a457-7334cee69d24`. Unknown ids stay HTTP 200 with `error: unknown templateId`.

### HTTP

1. Validate `query` and `api_key` (fail closed, no network).
2. Resolve `max_results` (`input` then `maxResultsDefault`, both must be `> 0` or they fall back).
3. Strip trailing `/` from `baseUrl`; require scheme `http` or `https`; POST `{baseUrl}/search`.
4. Headers: `Content-Type: application/json`, `User-Agent: StudioQuack/1.0`.
5. Body (compact JSON): `{ "api_key", "query", "max_results" }`.
6. `WebNodeSupport.send`: JDK `HttpClient`, TLS **always verified**, `HttpClient.Redirect.NEVER`, connect + request timeout both `timeout_seconds` (floored at 1 s), body cap **1_000_000 bytes**.
7. Parse JSON object; read `results` array; format up to `max_results` hits.

Error-body snippets in thrown messages are truncated to 240 characters and have the API-key literal replaced with `***`.

### Timeout / status mapping

| HTTP / transport | Outcome |
| --- | --- |
| 2xx + `results` with hits | numbered string on `output.result` |
| 2xx + empty `results` | `No results found.` |
| 429 | `RATE_LIMITED` (`HTTP 429` + 60s retry hint) |
| `>= 500` | `HTTP_<status>` (30s retry hint) |
| Timeout (`HttpTimeoutException` or IOException whose message contains `timed out`) | `REQUEST_FAILED` (`timed out` + 30s retry hint) |
| 3xx, other 4xx, other transport | `HTTP_<status>` or `REQUEST_FAILED` (`Tavily Search HTTP Error: …`) |
| Bad scheme | `INVALID_INPUT` `baseUrl must be http or https` |
| Non-object JSON / unexpected | `REQUEST_FAILED` `Error: {TypeName}: {message}` |

The node does **not** auto-retry. The retry hint lives in the error message.

Helper logs: `web-search start max_results=… timeout_s=…`, `ok results=N`, `HTTP 429`, `request timed out`, class name on unexpected errors. Never `api_key` or `helperToken`.

## Security & legal

- **Tavily contract.** Use of this node with `https://api.tavily.com` is use of Tavily’s Services. The operator must accept Tavily’s [Platform Terms of Service](https://www.tavily.com/terms) and [Acceptable Use Policy](https://www.tavily.com/acceptable-use-policy). Queries and result URLs may include personal data; the operator is the controller of that processing. StudioQuack does not grant Tavily rights or credits.
- **Target-site terms.** Search snippets and URLs are third-party content. Fetching a result URL (Web Browser / HTTP Request) remains subject to that site’s terms and robots.txt. This node only asks Tavily to search.
- **SSRF / egress.** Outbound POST goes to `baseUrl` (default `api.tavily.com`). The handler cannot bypass the daemon’s sandbox posture, which is set per engine: outbound internet allowed, the private network denied. A custom `baseUrl` that points at link-local or RFC1918 addresses is still subject to those host rules. The node has no API to disable them.
- **Secrets.** `api_key` lives only in instance `configurationParameters` (prefer a StudioQuack secret binding). It is sent only in the outbound JSON body, never as a header, never on any output port, never in helper logs. If a provider error body echoes the key, it is redacted to `***`. Do not put the key on the input port.
- **DoS / cost bounds.** Response body cap ~1 MiB; `max_results` default 5; connect+request timeout default 25 s. HTTP 429 / 5xx / timeout are structured so a runaway retry loop is the caller’s choice, not an unbounded inner loop.

## Examples

### Successful search

**Config:** `api_key` bound to a StudioQuack secret; `baseUrl` = `https://api.tavily.com`; `maxResultsDefault` = `5`; `timeout_seconds` = `25`.

**Input:**

```json
{
  "query": "BPMN exclusive gateway semantics",
  "max_results": 2,
  "include_answer": false
}
```

Outbound POST `https://api.tavily.com/search`:

```json
{
  "api_key": "***",
  "query": "BPMN exclusive gateway semantics",
  "max_results": 2
}
```

**Output:**

```json
{
  "result": "1. Exclusive Gateway - https://www.omg.org/spec/BPMN/2.0/\nAn Exclusive Gateway is used to create alternative paths within a Process flow.\n\n2. BPMN Gateways - https://camunda.com/bpmn/reference/\nExclusive (XOR) gateways route the flow to exactly one outgoing branch."
}
```

(`include_answer` has no effect on that POST.)

### Rate limited

**Input:** same as above, provider returns HTTP 429.

**Output:**

```json
{
  "result": {
    "status": "rate_limited",
    "source": "Web Search",
    "rate_limited": true,
    "retry_after_seconds": 60,
    "message": "Web search provider rate limited the request. Wait before retrying once; keep any prior usable results.",
    "error": "Web search HTTP 429: HTTP 429 for url: https://api.tavily.com/search — {\"detail\":\"rate limited\"}"
  }
}
```

### Missing API key

**Input:** `{"query":"robots.txt user-agent"}` with blank `api_key`.

**Output:**

```json
{
  "result": "Missing Tavily API Key. Provide it in node parameters (apiKey)."
}
```
