# Wikidata

In-process StudioQuack builtin (`templateId` `94267502-8f2b-45d5-9084-85b017a41965`, override `SQ_TEMPLATE_ID_WIKIDATA`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Wikibase Action API on www.wikidata.org: search (wbsearchentities) and entity_get (wbgetentities). SPARQL is not wired \u2014 OfficialApiNode cannot switch host to https://query.wikidata.org/sparql. CC0 data. Required contact User-Agent. No scrape.

Official docs: https://www.mediawiki.org/wiki/Wikibase/API

## Operations

`search` wbsearchentities. `entity_get` wbgetentities. SPARQL is omitted (single `baseUrl`; official endpoint is `https://query.wikidata.org/sparql`).

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://www.wikidata.org/wiki/Wikidata:Data_access — CC0. User-Agent policy. Rate-limit SPARQL. No HTML scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
