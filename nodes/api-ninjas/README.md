# API Ninjas

In-process StudioQuack builtin (`templateId` `ec315f4b-18d2-40d8-aa66-1602d70c1810`, override `SQ_TEMPLATE_ID_API_NINJAS`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official API Ninjas reference subset only: quotes (v2), trivia, facts, jokes, riddles, bucketlist (v1). Header X-Api-Key. Not the whole 140-API catalog. Quotes may be third-party copyright.

Official docs: https://api-ninjas.com/api

## Operations

`quotes` GET /v2/quotes. `trivia` /v1/trivia. `facts` /v1/facts. `jokes` /v1/jokes. `riddles` /v1/riddles. `bucketlist` /v1/bucketlist.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://api-ninjas.com/ — Commercial use typically requires premium. Quote text may still be third-party copyright.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
