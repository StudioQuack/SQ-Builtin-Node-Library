# OpenThesaurus

In-process StudioQuack builtin (`templateId` `ea6af0ce-7831-4ce5-826d-b6e92d056452`, override `SQ_TEMPLATE_ID_OPENTHESAURUS`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official OpenThesaurus JSON API (German). synonyms only. No key. Required User-Agent with contact. Link to openthesaurus.de. Max 60 req/min/IP.

Official docs: https://www.openthesaurus.de/about/api

## Operations

`synonyms` GET /synonyme/search?format=application/json.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://www.openthesaurus.de/about/api — CC BY-SA 4.0 or LGPL 2.1 (response metaData.license). Visible link to www.openthesaurus.de without nofollow. Contact in User-Agent. 60 req/min/IP. Notify feedback@openthesaurus.de for lasting use.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
