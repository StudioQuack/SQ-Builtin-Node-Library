# Unsplash

In-process StudioQuack builtin (`templateId` `c75381e5-ad8e-493a-a292-acabf6b5cc1a`, override `SQ_TEMPLATE_ID_UNSPLASH`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Unsplash API v1. Client-ID header. search_photos, photo_get, random, download_trigger (/photos/{id}/download), user_get. Hotlink urls; attribution required. No scrape.

Official docs: https://unsplash.com/documentation/

## Operations

`search_photos` /search/photos. `photo_get` /photos/{id}. `random` /photos/random. `download_trigger` /photos/{id}/download (required when a user uses a photo). `user_get` /users/{username}.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines — Hotlink photo.urls. Ping download on use. Attribute photographer + Unsplash with UTM. No scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
