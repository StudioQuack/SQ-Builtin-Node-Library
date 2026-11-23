# Wikipedia

In-process StudioQuack builtin (`templateId` `604ef300-e831-4a10-bf4b-0149599e4826`, override `SQ_TEMPLATE_ID_WIKIPEDIA`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

Official MediaWiki Action API + REST page summary on English Wikipedia (OfficialApiNode is single-host: en.wikipedia.org). search, get_page, get_summary, get_extract. No key. Set a contact User-Agent at the daemon. CC BY-SA attribution required. Do not scrape HTML.

Official docs: https://www.mediawiki.org/wiki/API:Main_page

## Operations

`search` list=search. `get_page` revisions content. `get_summary` REST /page/summary/{title}. `get_extract` prop=extracts.

## Ports

One INPUT `input`, one OUTPUT `output`. FUNCTION: none.

## Legal

https://foundation.wikimedia.org/wiki/Policy:Terms_of_Use — Text CC BY-SA; attribute authors/source URL. Required descriptive User-Agent with contact. https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy

## Implementation notes

- JDK HTTP via OfficialApiNode. Timeouts from `timeout_seconds` (default 25).
- Secrets only in configurationParameters. Never logged, never returned.
- HTTP 4xx/5xx → `output.error` + `http_status`. Ingress operations do not open a socket.
- Body cap and SSRF/egress are enforced by the daemon helper.
