# Wikipedia / Wikidata

Official-API node driven by `OfficialApiNode` + `api_spec.json`.

| | |
|---|---|
| Folder | `nodes/wikipedia-wikidata` |
| `templateId` | `0307a029-4563-59a0-af42-f3fe5632fc4a` |
| Env override | `SQ_TEMPLATE_ID_WIKIPEDIA_WIKIDATA` |
| Category | Data & Analytics |
| Official docs | https://www.mediawiki.org/wiki/API:Main_page |
| Auth | `none` — config `api_key` |

## Operations

| operation | HTTP | Path |
|---|---|---|
| `wiki_search` | http GET | `/w/api.php?action=query&list=search&format=json&formatversion=2` |
| `wiki_page` | http GET | `/w/api.php?action=query&prop=extracts&explaintext=1&format=json&formatversion=2` |
| `wiki_summary` | http GET | `/api/rest_v1/page/summary/{title}` |
| `wikidata_entity` | http GET | `/w/api.php?action=query&prop=pageprops&ppprop=wikibase_item&format=json&formatversion=2` |

## Ports

One INPUT `input`, one OUTPUT `output`. Output is `{ status, http_status, data }` or `{ error }`.

## Legal

WMF User-Agent policy (identifying UA required; sent as `User-Agent: {user_agent}`). Wikipedia text CC BY-SA; Wikidata CC0. Do not scrape HTML. SPARQL is the Wikidata SPARQL node. Host is `https://{language}.wikipedia.org` (config `language`, default `en`). `wikidata_entity` is Wikipedia `pageprops` Q-id lookup on that host — OfficialApiNode has one `baseUrl`, so `wbgetentities` on www.wikidata.org is not in this package.

Operator accepts the provider terms. Official HTTP only — no HTML scrape. Secrets are StudioQuack bindings; never logged.
