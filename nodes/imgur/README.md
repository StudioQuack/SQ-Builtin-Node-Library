# Imgur

In-process StudioQuack builtin (`templateId` `39524c78-1990-4206-aac5-0cdb2e23adac`, override `SQ_TEMPLATE_ID_IMGUR`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Imgur API v3. Client-ID header for anonymous. image_upload accepts an image URL (not multipart file — OfficialApiNode JSON/URL). image_get, album_get, gallery_search. Commercial use requires RapidAPI routing per Imgur docs.

Official docs: https://apidocs.imgur.com/

## Operations

`image_upload` POST /3/image (image URL). `image_get` /3/image/{id}. `album_get` /3/album/{id}. `gallery_search` /3/gallery/search/…

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://apidocs.imgur.com/ — Free Client-ID is non-commercial. Commercial apps must use RapidAPI endpoint. NSFW/content policy. No scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
