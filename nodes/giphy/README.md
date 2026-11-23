# Giphy

In-process StudioQuack builtin (`templateId` `a3a5bc9e-72a1-4a7b-b7f7-3318439c9446`, override `SQ_TEMPLATE_ID_GIPHY`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official GIPHY API v1 GIFs only. search, trending, translate, random, gif_get. Query api_key. Beta keys ~100 calls/hour. Do not scrape giphy.com. No sticker ops in this version.

Official docs: https://developers.giphy.com/docs/api/endpoint/

## Operations

`search` /v1/gifs/search. `trending` /trending. `translate` /translate. `random` /random. `gif_get` /v1/gifs/{gif_id}.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://developers.giphy.com/docs/api/ — Third-party licensed content. Beta ~100/hour. Production keys need approval. No scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `a BPMN error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
