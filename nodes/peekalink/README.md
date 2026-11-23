# Peekalink

In-process StudioQuack builtin (`templateId` `b56dbf7a-88e2-49f6-9e48-df003b6fe1fc`, override `SQ_TEMPLATE_ID_PEEKALINK`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Peekalink Link Preview API. preview POST / with {link}. Bearer auth. No paywall bypass. No login cookies. Do not log preview bodies (PII may appear).

Official docs: https://docs.peekalink.io/quickstart

## Operations

`preview` POST / {"link":"https://…"}.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://docs.peekalink.io/introduction — Fetches the target URL. No paywall bypass, no cookie forwarding. Operator owns target-site ToS. Minimize logging.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
