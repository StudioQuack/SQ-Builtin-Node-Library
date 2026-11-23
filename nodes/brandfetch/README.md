# Brandfetch

In-process StudioQuack builtin (`templateId` `45d185cc-968d-4953-99cc-99e4de2b7c0e`, override `SQ_TEMPLATE_ID_BRANDFETCH`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Brandfetch Brand API v2. brand_get / logo_get / color_get all GET /v2/brands/domain/{domain} (logos[] and colors[] are fields). Bearer token. Logo CDN is embed-only (not fetched here). Trademarks remain the brand's.

Official docs: https://docs.brandfetch.com/brand-api/overview

## Operations

All three ops GET /v2/brands/domain/{domain}. Extract logos[] or colors[] in the workflow. Logo CDN (cdn.brandfetch.io) forbids automated fetches.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://docs.brandfetch.com/brand-api/overview — Logos/trademarks remain the brand owner's. No endorsement. Do not scrape brand websites. Logo API CDN is embed-only.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
