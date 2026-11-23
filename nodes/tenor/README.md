# Tenor

In-process StudioQuack builtin (`templateId` `d0919c03-4fc9-490b-a165-167e38be8afc`, override `SQ_TEMPLATE_ID_TENOR`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Tenor API v2 (tenor.googleapis.com). search, featured, register_share, gif_get (posts). Query key + client_key. Call register_share when a user sends a GIF. No scrape.

Official docs: https://developers.google.com/tenor/guides/endpoints

## Operations

`search` /v2/search. `featured` /v2/featured. `register_share` GET /v2/registershare. `gif_get` GET /v2/posts?ids=.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://developers.google.com/tenor/guides/quickstart + Google APIs ToS. register_share when a user shares. contentfilter recommended.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
