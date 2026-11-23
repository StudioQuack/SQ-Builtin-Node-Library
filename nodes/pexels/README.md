# Pexels

In-process StudioQuack builtin (`templateId` `49da063b-e6d5-409a-a7bc-dba2a8c88873`, override `SQ_TEMPLATE_ID_PEXELS`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Pexels API. Authorization header is the raw API key. search_photos, search_videos, curated, photo_get. Attribution required. No unmodified dump resale. No scrape.

Official docs: https://www.pexels.com/api/documentation/

## Operations

`search_photos` GET /v1/search. `search_videos` GET /v1/videos/search. `curated` GET /v1/curated. `photo_get` GET /v1/photos/{id}.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://www.pexels.com/api/documentation/ — Attribute photographer and Pexels. No resale of unmodified dumps. Typical default ~200 req/h.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
