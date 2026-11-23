# Datamuse

In-process StudioQuack builtin (`templateId` `6ff576a4-6ece-4ab5-be7d-4fef33663a8b`, override `SQ_TEMPLATE_ID_DATAMUSE`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Datamuse API v1.1. words and suggest. Optional `api_key` query param (https://www.datamuse.com/api/). **Required from 2027-01-01**; until then keyless still works (100k req/day). Attribution appreciated.

Official docs: https://www.datamuse.com/api/

## Operations

`words` GET /words. `suggest` GET /sug.

`api_key` is included in each operation's `queryFrom`. When the config value is non-blank it is sent as `?api_key=`; when blank it is omitted (keyless). `auth.type=query` is not used because OfficialApiNode rejects a blank query secret — that would break keyless use before 2027-01-01. From that date, bind a key (or flip the spec to `auth.type: query`).

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none. Never put `api_key` on INPUT.

## Legal

https://www.datamuse.com/api/ — Acknowledge Datamuse in public apps. Key required starting 2027-01-01. ~100k req/day. No scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
