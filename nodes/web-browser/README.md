# Web Browser

## Overview

Web Browser GETs one public `http`/`https` URL and returns readable page text. It is **not** a headless browser: it does not execute JavaScript, fill forms, keep cookies, or drive a real Chrome/Firefox session. The in-process handler `com.studioquack.nodelibrary.nodes.web.WebBrowserNode` uses JDK `java.net.http.HttpClient` (TLS always verified, `HttpClient.Redirect.NORMAL`) and converts HTML to text with a regex pipeline (no Jsoup).

Use this node when the workflow already has a URL. Use **Web Search** when you only have a query. Use **HTTP Request** when you need a non-GET verb, custom headers, or a request body.

robots.txt is honored by default (`RobotsTxt`, a reasonable port of CPython `urllib.robotparser` aligned with [RFC 9309](https://www.rfc-editor.org/rfc/rfc9309.html)). Unreachable robots.txt **fail-open**: the page GET still runs and `metadata.robots` is `unreachable`. Raw HTML is capped at 2_000_000 bytes; robots.txt at 524_288 bytes.

| | |
| --- | --- |
| Designer name | Web Browser |
| `templateId` / definition `_id` | `cc63350b-4d0d-4328-87c0-624f6018d0e1` |
| Image env override | `SQ_TEMPLATE_ID_WEB_BROWSER` |
| Handler | `com.studioquack.nodelibrary.nodes.web.WebBrowserNode` |
| Catalog folder | `nodes/web-browser` |

Provider / protocol docs:

- [RFC 9309 — Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309.html)
- [robotstxt.org](https://www.robotstxt.org/robotstxt.html)
- [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)

## Ports

The daemon delivers port data as `inputVariables.input`, or as `executionContext.currentNode.inputList[]` with `targetPortValue=input`. A one-element list is unwrapped. A JSON-object string is parsed. Success returns `{"output": <object>}`. `blocked_by_robots` is a successful outcome. Failures raise a BPMN error.

### `input` (INPUT)

JSON object. Configuration knobs (`timeout_seconds`, `user_agent`, `respect_robots_txt`, `location`) do **not** belong here.

| Field | Type | Required | Schema / accepted values | How it is used |
| --- | --- | --- | --- | --- |
| `url` | string | **yes** | Absolute `http` or `https` URL (case-insensitive scheme). Trimmed. Host must be non-blank. Path, query, port, and userinfo may be present; the fragment is not sent. | Parsed with `java.net.URI`. This is the document GET target and the URL passed to `RobotsTxt.canFetch`. Empty/missing → `INVALID_INPUT` `url is required.`. `ftp` / `file` / `data` / `javascript` / `mailto` / scheme-relative / blank host → `INVALID_INPUT` `url must be http or https.` |
| `max_chars` | number | no | Positive integer (JSON number or numeric string). `0`, `0.0`, empty string, or omitted → instance config `max_chars` (default `12000`). A non-numeric non-empty string falls through to `12000`, not the config value. | Caps `output.content` length after HTML stripping. Longer text is cut with `String.substring` and suffixed with `... [truncated]`. Independent of the 2_000_000-byte raw-HTML cap. |

Example payload: `{"url":"https://example.com/docs","max_chars":8000}`.

### `output` (OUTPUT)

Always an object on success. Inspect `status` first; do not treat HTTP 200 of the invoke as a fetched page (`blocked_by_robots` is a successful outcome). Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

| Field | Type | Present when | Schema / accepted values | What flows |
| --- | --- | --- | --- | --- |
| `status` | string | success | Exact: `ok` \| `blocked_by_robots` | `ok` = 2xx page GET, text extracted. `blocked_by_robots` = robots.txt disallowed this User-Agent+URL; the page was **not** fetched (not an exception). |
| `url` | string | `ok`; `blocked_by_robots` | Final or requested URL | On `ok`: final URL after `Redirect.NORMAL`. On `blocked_by_robots`: requested URL unchanged. |
| `title` | string | `ok` | Page title or `""` | First `<title>…</title>` (case-insensitive, DOTALL), inner HTML regex-stripped; else first `<h1>…</h1>` stripped; else `""`. HTML entities are not decoded (`&amp;` stays). Absent on blocked. |
| `content` | string | `ok` | Stripped page text | Regex pipeline (see How it works). Truncated to `max_chars` with `... [truncated]`. Absent on blocked. Unclosed `<script>` may leak as text. |
| `metadata` | object | `ok` and `blocked_by_robots` | See children | Fetch diagnostics. |
| `error` | string | `blocked_by_robots` | Human-readable reason | Always exactly `Fetch disallowed by robots.txt`. Never contains `helperToken`, cookies, or full HTML. |

`metadata` children:

| Field | Type | Present when | Accepted values | Purpose |
| --- | --- | --- | --- | --- |
| `content_type` | string | `ok` | First `Content-Type` header (may include charset), or `""` | Whether the body was HTML, XHTML, or something else that was still regex-stripped. |
| `status_code` | number | `ok` | Integer 200–299 | HTTP status after redirects. Non-2xx raises a BPMN error. |
| `fetched_at` | string | `ok` and `blocked_by_robots` | ISO-8601 UTC from `Instant.now().toString()` | When the robots decision or page fetch completed. |
| `robots` | string | `ok` and `blocked_by_robots` | Exact: `skipped` \| `allowed` \| `disallowed` \| `unreachable` | `skipped` = robots.txt was not consulted. `allowed` = 2xx robots.txt, `canFetch=true`, page fetched. `disallowed` = `canFetch=false`, page not fetched. `unreachable` = robots.txt network/timeout/non-2xx or URI failure — fail-open, page **is** fetched. |
| `location` | string | when config `location` is non-empty after trim | Same codes as the `location` parameter | Echo of which Accept-Language mapping was applied. |

## Configuration parameters

Instance `configurationParameters`, keyed by `shortCode`. All are instance-editable. None belong on the input port.

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `timeout_seconds` | number | no | `25` | Positive integers (JSON number or numeric string). Non-numeric / empty / missing → `25`. HTTP helper floors at 1 second. | Connect + request timeout for **each** of the robots.txt GET and the page GET (`HttpClient.connectTimeout` and `HttpRequest.timeout`). Not a shared wall clock across both calls. Keep under 60 s unless the origin is known-slow. |
| `max_chars` | number | no | `12000` | Positive integers. `0` in this config is passed through (Python parity) and truncates any non-empty page to `... [truncated]`. | Default cap on stripped `content` when input `max_chars` is omitted, `0`, or empty. Input `max_chars` overrides per call. Payload/DoS bound on the OUTPUT object, separate from the 2_000_000-byte raw HTML cap. |
| `user_agent` | string | no | Chrome/120 Windows UA (see below) | Any non-empty User-Agent string. Empty/missing → `WebNodeSupport.CHROME_UA`. | Sent on the page GET and the robots.txt GET, and passed to `RobotsTxt.canFetch` (substring match, case-insensitive). robots.txt rules are User-Agent-specific. Never log this value if a caller stuffs a secret into it. |
| `respect_robots_txt` | select | no | `true` | Options `true` \| `false`. Also parsed as boolean-like: `true`/`1`/`yes` vs `false`/`0`/`no`. Unknown/blank → `true`. | Whether the node consults robots.txt before the page GET. **Default is on.** Enforcement is the product default; turning it off is an operator override that logs a warning (see How it works). |
| `location` | string | no | `""` | ISO 3166-1 alpha-2 country code (case-insensitive, trimmed). Empty → no `Accept-Language` header and no `metadata.location`. | Sets the page GET `Accept-Language` header and is echoed on `metadata.location`. |

Default User-Agent:

```text
Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
```

`location` → exact `Accept-Language` sent:

| Code | Header |
| --- | --- |
| `us` | `en-US,en;q=0.9` |
| `gb` or `uk` | `en-GB,en;q=0.9` |
| `tr` | `tr-TR,tr;q=0.9,en;q=0.5` |
| `de` | `de-DE,de;q=0.9,en;q=0.5` |
| `fr` | `fr-FR,fr;q=0.9,en;q=0.5` |
| `es` | `es-ES,es;q=0.9,en;q=0.5` |
| `it` | `it-IT,it;q=0.9,en;q=0.5` |
| `nl` | `nl-NL,nl;q=0.9,en;q=0.5` |
| `pt` | `pt-PT,pt;q=0.9,en;q=0.5` |
| `br` | `pt-BR,pt;q=0.9,en;q=0.5` |
| `jp` | `ja-JP,ja;q=0.9,en;q=0.5` |
| `cn` | `zh-CN,zh;q=0.9,en;q=0.5` |
| `kr` | `ko-KR,ko;q=0.9,en;q=0.5` |
| `in` | `en-IN,en;q=0.9,hi;q=0.7` |
| `au` | `en-AU,en;q=0.9` |
| `ca` | `en-CA,en;q=0.9,fr-CA;q=0.7` |
| `mx` | `es-MX,es;q=0.9,en;q=0.5` |
| `pl` | `pl-PL,pl;q=0.9,en;q=0.5` |
| `se` | `sv-SE,sv;q=0.9,en;q=0.5` |
| any other non-empty | `{code}-{CODE},{code};q=0.9,en;q=0.5` |

## How it works

### Dispatch

The builtin-library image is one long-lived listener. `NodeLibraryApp.route` reads `templateId` from the invoke body and looks it up in `NodeRegistry`. `WebNodeHandlers.register` wires this handler under `SQ_TEMPLATE_ID_WEB_BROWSER` (wins) or definition `_id` `cc63350b-4d0d-4328-87c0-624f6018d0e1`. Unknown ids stay HTTP 200 with `error: unknown templateId`. Failures raise a BPMN error.

### HTTP

Both the robots.txt GET and the page GET go through `WebNodeSupport.send`:

- JDK `HttpClient`; TLS **always verified** (`verifyTls=true`).
- `connectTimeout` and `HttpRequest.timeout` both equal `max(1, timeout_seconds)` seconds.
- `HttpClient.Redirect.NORMAL` (follows same-protocol redirects; HTTPS→HTTP is not followed).
- Restricted headers (`Host`, `Content-Length`, `Connection`, …) are skipped rather than failing the node.
- Response body is read with an 8 KiB loop and **capped**: 524_288 bytes for robots.txt, 2_000_000 bytes for the page. Excess is dropped (`truncated=true`); the node still proceeds.
- Charset comes from `Content-Type`; unknown/missing → UTF-8.

Page GET headers: `User-Agent`, `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`, and `Accept-Language` when `location` is set.

### robots.txt (default ON)

When `respect_robots_txt` is true (the default):

1. Cache key `{scheme}://{netloc}` lower-cased in a process-lifetime `ConcurrentHashMap`.
2. On miss, GET `{scheme}://{authority}/robots.txt` with `User-Agent` and `Accept: text/plain,*/*;q=0.8`.
3. HTTP 2xx → `RobotsTxt.parse` then `canFetch(user_agent, url)`.
4. Disallowed → `status=blocked_by_robots`; **the page is not fetched**.
5. Timeout, network error, or non-2xx → fail-open: `metadata.robots=unreachable` and the page GET runs.

Matching: a specific User-Agent substring match (case-insensitive) wins over `*`; then the longest matching path prefix; equal length prefers Allow. No matching rule → allow. Paths are percent-decoded; the query string is not part of the match. Sitemap / Crawl-delay / Request-rate / unknown fields are ignored. Empty `Disallow:` means allow all.

When enforcement is **explicitly turned off**, the handler logs a warning and records `metadata.robots=skipped`. Do not treat that override as a way around a site’s exclusion rules; fetch only what you are allowed to fetch.

Two concurrent cache misses may both GET once; `putIfAbsent` keeps the first value. Blocking I/O is outside `computeIfAbsent` so `ConcurrentHashMap` cannot hang.

### HTML → text

Regex only (no Jsoup), in this order:

1. Strip `<script>…</script>` (`CASE_INSENSITIVE` + `DOTALL`)
2. Strip `<style>…</style>`
3. Strip `<!-- … -->`
4. Strip `<[^>]+>`
5. Collapse `\s+` to a single space and trim

Then truncate to `max_chars`.

### Timeout / error mapping

| Condition | Outcome |
| --- | --- |
| Missing / blank `url` | `INVALID_INPUT` `url is required.` |
| Scheme not `http`/`https`, unparseable URI, or blank host | `INVALID_INPUT` `url must be http or https.` |
| robots.txt disallows this User-Agent + URL | success `blocked_by_robots` / `Fetch disallowed by robots.txt` |
| robots.txt timeout / network / non-2xx | page still fetched; `metadata.robots=unreachable` |
| Page GET non-2xx | `HTTP_<status>` `Web browser HTTP error: HTTP <code> for url: <finalUrl>` |
| Timeout / I/O on the page GET (`IOException`, `InterruptedException`, `TimeoutException`) | `REQUEST_FAILED` `Web browser HTTP error: <message>` |
| Any other exception | `REQUEST_FAILED` `Web browser error: <TypeName>: <message>` |

Helper logs record host, timeout, status code, content length, and robots outcome — never `helperToken`, cookies, or full HTML.

## Security & legal

- **robots.txt and target-site terms.** This node consults robots.txt by default. That is a politeness/exclusion signal, not a license. The **operator** is responsible for the target site’s terms of service, copyright, and applicable law. Fetch only URLs the workflow is allowed to retrieve. StudioQuack does not grant rights to third-party content.
- **Not a browser.** Pages that require JavaScript, login cookies, or CSRF tokens are out of scope. Do not use this node to submit forms or scrape authenticated / cookie-gated surfaces.
- **SSRF / egress.** The handler will attempt any `http`/`https` URL the workflow supplies. It cannot open a hole in the daemon’s sandbox network posture, which is set per engine and which no node or workflow can change: outbound internet is allowed and the private network is denied. On a `DEGRADED` host the daemon reports that its host rules are not applied; that is a host fact, not a node bypass.
- **Secrets.** This node has no API-key parameter. Do not put bearer tokens in `url`, `user_agent`, or logs. `helperToken` is used only for the helper channel and is never logged.
- **DoS bounds.** Raw HTML 2_000_000 bytes, robots.txt 524_288 bytes, stripped text `max_chars` (default 12_000). Timeouts on every HTTP call. These caps exist so a hostile origin cannot fill the sandbox heap.
- **TLS.** Certificate verification is always on. There is no “skip verify” knob on this node.

## Examples

### Successful fetch

**Input** (`input` port):

```json
{
  "url": "https://example.com/",
  "max_chars": 8000
}
```

**Output** (`output` port):

```json
{
  "status": "ok",
  "url": "https://example.com/",
  "title": "Example Domain",
  "content": "Example Domain This domain is for use in illustrative examples in documents. You may use this domain in literature without prior coordination or asking for permission.",
  "metadata": {
    "content_type": "text/html; charset=UTF-8",
    "status_code": 200,
    "fetched_at": "2026-08-20T18:00:00Z",
    "robots": "allowed"
  }
}
```

### Validation error

**Input:**

```json
{
  "url": ""
}
```

Raises `INVALID_INPUT` with message `url is required.`

### Disallowed by robots.txt

When robots.txt forbids this User-Agent on the requested path, the page GET does not run:

```json
{
  "status": "blocked_by_robots",
  "url": "https://example.com/secret/",
  "error": "Fetch disallowed by robots.txt",
  "metadata": {
    "robots": "disallowed",
    "fetched_at": "2026-08-20T18:02:11.123Z"
  }
}
```
