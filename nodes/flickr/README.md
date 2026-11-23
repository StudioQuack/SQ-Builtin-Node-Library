# Flickr

In-process StudioQuack builtin (`templateId` `6e265811-3327-4dc8-915f-7a910a4a7f73`, override `SQ_TEMPLATE_ID_FLICKR`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official Flickr REST (api_key query, format=json&nojsoncallback=1). photo_search, photo_get, people_get. photo_upload omitted (multipart + OAuth 1.0a not supported by OfficialApiNode). trigger is kind:ingress (flickr.push is experimental Pro-only PubSubHubbub — use BPMN message event). Licence is per photo.

Official docs: https://www.flickr.com/services/api/

## Operations

`photo_search` flickr.photos.search. `photo_get` flickr.photos.getInfo. `people_get` flickr.people.getInfo. `trigger` ingress. Upload not shipped (OAuth1 + multipart).

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://www.flickr.com/services/api/tos/ — Photos owned by users. Honour per-photo licence. Max 30 photos/page display. Attribution notice required. No scrape.

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `a BPMN error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
