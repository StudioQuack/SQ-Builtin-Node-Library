# RSS

In-process StudioQuack builtin (`templateId` `2c460ad5-518a-5053-92ee-a0cdeab03f50`, override `SQ_TEMPLATE_ID_RSS`). Handler: `OfficialApiNode` (spec-driven). No custom Java class.

## Overview

One node for RSS 2.0 and Atom (RFC 4287). `read` is HTTP GET. `listen` is `kind:ingress` — OfficialApiNode does not poll or open a socket; inbound feed events use a BPMN message start / catch / boundary event.

OfficialApiNode concatenates `baseUrl` + `path`. This package uses `baseUrl: "https://"` and path `{host_and_path}` (host + path + query, **no scheme**). Example: `feeds.bbci.co.uk/news/rss.xml`.

Official docs: https://www.rssboard.org/rss-specification · https://www.rfc-editor.org/rfc/rfc4287

## Operations

| operation | kind | Notes |
|---|---|---|
| `read` | http GET | Path `{host_and_path}` |
| `listen` | ingress | BPMN message event. Does not open a socket. |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`. Feed bodies are typically XML (returned as text unless JSON).

## Legal

Honor each feed's copyright, robots, and size limits. Protocol, not a vendor. No HTML scrape of publisher sites.

Operator accepts the provider terms. Secrets are StudioQuack bindings; never logged.
