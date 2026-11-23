# Openverse

In-process StudioQuack builtin (`templateId` `d38a94cb-9797-4e04-bd44-d97e17c72d73`, override `SQ_TEMPLATE_ID_OPENVERSE`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Openverse API v1 (anonymous). search_images, search_audio. thumbnail is per-work on the result (no separate host switch). Respect EACH work's CC licence. OAuth optional for higher limits is not wired (OfficialApiNode auth is none so anonymous works).

Official docs: https://api.openverse.org/

## Operations

`search_images` GET /images/. `search_audio` GET /audio/. `thumbnail` GET /images/{identifier}/thumb/ if the live API still serves it.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://docs.openverse.org/terms_of_service.html — Each work has its own CC licence (BY/SA/NC/ND). Attribute per work. Do not assume CC0.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
