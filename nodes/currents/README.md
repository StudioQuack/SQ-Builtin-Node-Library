# Currents

In-process StudioQuack builtin (`templateId` `64e68ec8-c4e1-44df-8236-4d21be0b440c`, override `SQ_TEMPLATE_ID_CURRENTS`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Currents News API v1 (currentsapi.services — not currents.dev). Bearer auth. search, latest, available catalogues. Keep publisher URLs. No paywall bypass.

Official docs: https://currentsapi.services/en/docs/

## Operations

`search` GET /search. `latest` GET /latest-news. `available` GET /available/{catalog} where **catalog** must be exactly **`languages`**, **`regions`**, or **`categories`** (official API paths). Values like `news` or `all` are rejected locally before HTTP. Alias INPUT keys: `kind` → same as `catalog`; `query` → `search` keywords.

Empty or whitespace-only **`operation`** on INPUT is rejected with INVALID_INPUT (does not silently fall back to config default).

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://currentsapi.services/terms — Processing/previews/attribution and publisher links. Publisher copyright. Free ~250 req/day.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
