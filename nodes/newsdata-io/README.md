# NewsData.io

In-process StudioQuack builtin (`templateId` `e93c770d-b2b7-4676-93cc-e35732d28297`, override `SQ_TEMPLATE_ID_NEWSDATA_IO`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official NewsData.io API /api/1. latest, crypto, sources, archive. Header X-ACCESS-KEY (provider also accepts query apikey). Archive is paid-plan only. Plan-based commercial use. Keep publisher links. Do not republish full articles as your own.

Official docs: https://newsdata.io/documentation

## Operations

`latest` /latest. `crypto` /crypto. `sources` /sources. `archive` /archive (from_date required; paid plan per https://newsdata.io/documentation).

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://newsdata.io/documentation — Plan-based commercial use. Full-content republish is not granted. Keep publisher URLs.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
