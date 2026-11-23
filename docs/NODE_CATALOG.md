# SQ Builtin Node Library — Node Catalog

The master index of built-in nodes: nodes that ship in this library today and the
planned provider nodes that bring the deck to parity with leading
workflow-automation platforms.

## Design rules (how a node is shaped)

- **One node per API provider.** A provider's operations (e.g. Google Sheets
  `read` / `append` / `update` / `clear`) are selected through an `operation`
  configuration parameter — there are no per-operation nodes.
- **Ports.** Almost every node is one INPUT port in, one OUTPUT port out.
  **FUNCTION ports** exist only when a node must invoke another node or
  sub-process N times during its own run (the AI Agent's `llm` and `tool`
  ports are the canonical example).
- **Provider listen is an operation; StudioQuack webhook is not a node.**
  A third-party provider that can push events (Stripe, GitHub, Slack, …)
  exposes a `listen` / `trigger` operation on that provider's node (usually
  to register the subscription). Inbound StudioQuack webhooks themselves
  are **not** a catalog node and never appear as "Webhook" / "Respond to
  Webhook": external senders hit the StudioQuack engine's message-event ingress,
  which correlates onto BPMN message start / intermediate catch / boundary
  message events.
- **Flow control is not a node.** Branching, merging, waiting, scheduling,
  error handling, and sub-process invocation are BPMN constructs of the
  engine (exclusive / inclusive / parallel / complex / event-based gateways,
  timer / error / message events, call activity). The catalog never lists
  them as nodes.
- **File payloads use the `file` type.** Where a node transfers or transforms
  a file, both the ports and the configuration parameters use the platform
  `file` type (a ref-id, never inline bytes).
- **Official APIs first.** Nodes call the provider's official, documented API.
  Where no official API exists the row is flagged in its Notes column; the one
  shipped exception (Google Trends) is opt-in behind an explicit terms
  acceptance. See [LEGAL.md](../LEGAL.md).

Status legend: `done` — registered in this image (`nodes/<key>/` + handler) ·
`planned` — catalogued only (no official HTTP surface, DO NOT BUILD, or
protocol that OfficialApiNode cannot speak).

Planned HTTP providers become nodes as `nodes/<slug>/{node_definition.json,
api_spec.json, README.md}`. The shared `OfficialApiNode` loads `api_spec.json`
and dispatches by `templateId`. Custom Java handlers (AI Agent, OpenAI Models,
Local LLM, Web Browser, Sheets, …) still win when both exist.

## Shipped nodes (status: done)

| Node | Category | Folder |
|---|---|---|
| AI Agent | AI & Machine Learning | `nodes/ai-agent` |
| MCP Server | AI & Machine Learning | `nodes/mcp-server` |
| OpenAI Models | AI & Machine Learning | `nodes/openai-models` |
| Local LLM | AI & Machine Learning | `nodes/local-llm` |
| HTTP Request | Developer Tools | `nodes/http-request` |
| Web Browser | Developer Tools | `nodes/web-browser` |
| GitHub | Developer Tools | `nodes/github` |
| Dev.to | Developer Tools | `nodes/devto` |
| GitLab | Developer Tools | `nodes/gitlab` |
| Bitbucket | Developer Tools | `nodes/bitbucket` |
| Azure DevOps | Developer Tools | `nodes/azure-devops` |
| Jira | Developer Tools | `nodes/jira` |
| Linear | Developer Tools | `nodes/linear` |
| Sentry | Developer Tools | `nodes/sentry` |
| Datadog | Developer Tools | `nodes/datadog` |
| New Relic | Developer Tools | `nodes/new-relic` |
| PagerDuty | Developer Tools | `nodes/pagerduty` |
| Opsgenie | Developer Tools | `nodes/opsgenie` |
| Statuspage | Developer Tools | `nodes/statuspage` |
| CircleCI | Developer Tools | `nodes/circleci` |
| Jenkins | Developer Tools | `nodes/jenkins` |
| Travis CI | Developer Tools | `nodes/travis-ci` |
| Rundeck | Developer Tools | `nodes/rundeck` |
| Docker Hub | Developer Tools | `nodes/docker-hub` |
| Kubernetes | Developer Tools | `nodes/kubernetes` |
| npm | Developer Tools | `nodes/npm` |
| PyPI | Developer Tools | `nodes/pypi` |
| SonarQube | Developer Tools | `nodes/sonarqube` |
| Codecov | Developer Tools | `nodes/codecov` |
| LaunchDarkly | Developer Tools | `nodes/launchdarkly` |
| Postman | Developer Tools | `nodes/postman` |
| OpenAPI | Developer Tools | `nodes/openapi` |
| ngrok | Developer Tools | `nodes/ngrok` |
| Cloudflare | Developer Tools | `nodes/cloudflare` |
| Vercel | Developer Tools | `nodes/vercel` |
| Netlify | Developer Tools | `nodes/netlify` |
| Heroku | Developer Tools | `nodes/heroku` |
| Render | Developer Tools | `nodes/render` |
| DigitalOcean | Developer Tools | `nodes/digitalocean` |
| AWS | Developer Tools | `nodes/aws` |
| GCP | Developer Tools | `nodes/gcp` |
| Azure | Developer Tools | `nodes/azure` |
| Web Search (Tavily) | Data & Analytics | `nodes/web-search` |
| Brave Search | Data & Analytics | `nodes/brave-search` |
| SerpAPI | Data & Analytics | `nodes/serpapi` |
| Google Programmable Search | Data & Analytics | `nodes/google-programmable-search` |
| DuckDuckGo | Data & Analytics | `nodes/duckduckgo` |
| Exa | Data & Analytics | `nodes/exa` |
| Perplexity Sonar | Data & Analytics | `nodes/perplexity-sonar` |
| Wikipedia / Wikidata | Data & Analytics | `nodes/wikipedia-wikidata` |
| Crossref | Data & Analytics | `nodes/crossref` |
| Open Library | Data & Analytics | `nodes/open-library` |
| Google Books | Data & Analytics | `nodes/google-books` |
| OpenWeatherMap | Data & Analytics | `nodes/openweathermap` |
| Tomorrow.io | Data & Analytics | `nodes/tomorrow-io` |
| WeatherAPI | Data & Analytics | `nodes/weatherapi` |
| Mapbox | Data & Analytics | `nodes/mapbox` |
| Google Maps / Places | Data & Analytics | `nodes/google-maps-places` |
| HERE Maps | Data & Analytics | `nodes/here-maps` |
| OpenStreetMap / Nominatim | Data & Analytics | `nodes/openstreetmap-nominatim` |
| ipinfo | Data & Analytics | `nodes/ipinfo` |
| ip-api | Data & Analytics | `nodes/ip-api` |
| Numverify | Data & Analytics | `nodes/numverify` |
| Abstract API | Data & Analytics | `nodes/abstract-api` |
| APILayer | Data & Analytics | `nodes/apilayer` |
| Alpha Vantage | Data & Analytics | `nodes/alpha-vantage` |
| Finnhub | Data & Analytics | `nodes/finnhub` |
| Polygon.io | Data & Analytics | `nodes/polygon-io` |
| CoinGecko | Data & Analytics | `nodes/coingecko` |
| CoinMarketCap | Data & Analytics | `nodes/coinmarketcap` |
| ExchangeRate-API | Data & Analytics | `nodes/exchangerate-api` |
| World Bank | Data & Analytics | `nodes/world-bank` |
| data.gov | Data & Analytics | `nodes/data-gov` |
| Eurostat | Data & Analytics | `nodes/eurostat` |
| Wikidata SPARQL | Data & Analytics | `nodes/wikidata-sparql` |
| DBpedia | Data & Analytics | `nodes/dbpedia` |
| GNews | Data & Analytics | `nodes/gnews` |
| Mediastack | Data & Analytics | `nodes/mediastack` |
| Currents API | Data & Analytics | `nodes/currents-api` |
| The Guardian | Data & Analytics | `nodes/the-guardian` |
| NYTimes | Data & Analytics | `nodes/nytimes` |
| RSS | Data & Analytics | `nodes/rss` |
| NASA | Data & Analytics | `nodes/nasa` |
| Wolfram Alpha | Data & Analytics | `nodes/wolfram-alpha` |
| Jina AI | Data & Analytics | `nodes/jina-ai` |
| SearXNG | Data & Analytics | `nodes/searxng` |
| marketstack | Data & Analytics | `nodes/marketstack` |
| FRED | Data & Analytics | `nodes/fred` |
| Nasdaq Data Link | Data & Analytics | `nodes/nasdaq-data-link` |
| Twelve Data | Data & Analytics | `nodes/twelve-data` |
| US Census Bureau | Data & Analytics | `nodes/us-census-bureau` |
| OECD | Data & Analytics | `nodes/oecd` |
| Google Sheets | Productivity & Collaboration | `nodes/google-sheets` |
| Google Docs | Productivity & Collaboration | `nodes/google-docs` |
| Google Drive | Productivity & Collaboration | `nodes/google-drive` |
| Google Calendar | Productivity & Collaboration | `nodes/google-calendar` |
| Google Tasks | Productivity & Collaboration | `nodes/google-tasks` |
| Gmail | Productivity & Collaboration | `nodes/gmail` |
| Microsoft Excel | Productivity & Collaboration | `nodes/microsoft-excel` |
| Microsoft OneDrive | Productivity & Collaboration | `nodes/microsoft-onedrive` |
| Microsoft SharePoint | Productivity & Collaboration | `nodes/microsoft-sharepoint` |
| Microsoft To Do | Productivity & Collaboration | `nodes/microsoft-to-do` |
| Notion | Productivity & Collaboration | `nodes/notion` |
| Airtable | Productivity & Collaboration | `nodes/airtable` |
| Trello | Productivity & Collaboration | `nodes/trello` |
| Asana | Productivity & Collaboration | `nodes/asana` |
| Monday.com | Productivity & Collaboration | `nodes/monday-com` |
| ClickUp | Productivity & Collaboration | `nodes/clickup` |
| Basecamp | Productivity & Collaboration | `nodes/basecamp` |
| Todoist | Productivity & Collaboration | `nodes/todoist` |
| OneNote | Productivity & Collaboration | `nodes/onenote` |
| Confluence | Productivity & Collaboration | `nodes/confluence` |
| Coda | Productivity & Collaboration | `nodes/coda` |
| Slite | Productivity & Collaboration | `nodes/slite` |
| Nuclino | Productivity & Collaboration | `nodes/nuclino` |
| Linear | Productivity & Collaboration | `nodes/linear` |
| Jira | Productivity & Collaboration | `nodes/jira` |
| Smartsheet | Productivity & Collaboration | `nodes/smartsheet` |
| Wrike | Productivity & Collaboration | `nodes/wrike` |
| Teamwork | Productivity & Collaboration | `nodes/teamwork` |
| Hive | Productivity & Collaboration | `nodes/hive` |
| Miro | Productivity & Collaboration | `nodes/miro` |
| FigJam | Productivity & Collaboration | `nodes/figjam` |
| Lucidchart | Productivity & Collaboration | `nodes/lucidchart` |
| Whimsical | Productivity & Collaboration | `nodes/whimsical` |
| Slack | Productivity & Collaboration | `nodes/slack` |
| Calendly | Productivity & Collaboration | `nodes/calendly` |
| Cal.com | Productivity & Collaboration | `nodes/cal-com` |
| Clockify | Productivity & Collaboration | `nodes/clockify` |
| Toggl | Productivity & Collaboration | `nodes/toggl` |
| Harvest | Productivity & Collaboration | `nodes/harvest` |
| RescueTime | Productivity & Collaboration | `nodes/rescuetime` |
| Microsoft Outlook | Communication & Messaging | `nodes/microsoft-outlook` |
| Discord | Communication & Messaging | `nodes/discord` |
| Telegram | Communication & Messaging | `nodes/telegram` |
| WhatsApp | Communication & Messaging | `nodes/whatsapp` |
| Microsoft Teams | Communication & Messaging | `nodes/microsoft-teams` |
| Google Chat | Communication & Messaging | `nodes/google-chat` |
| Zoom | Communication & Messaging | `nodes/zoom` |
| Webex | Communication & Messaging | `nodes/webex` |
| Twilio | Communication & Messaging | `nodes/twilio` |
| Vonage | Communication & Messaging | `nodes/vonage` |
| MessageBird | Communication & Messaging | `nodes/messagebird` |
| SendGrid | Communication & Messaging | `nodes/sendgrid` |
| Mailgun | Communication & Messaging | `nodes/mailgun` |
| Postmark | Communication & Messaging | `nodes/postmark` |
| Amazon SES | Communication & Messaging | `nodes/amazon-ses` |
| Brevo | Communication & Messaging | `nodes/brevo` |
| Mailjet | Communication & Messaging | `nodes/mailjet` |
| Mandrill | Communication & Messaging | `nodes/mandrill` |
| Intercom | Communication & Messaging | `nodes/intercom` |
| Zendesk Chat | Communication & Messaging | `nodes/zendesk-chat` |
| Matrix | Communication & Messaging | `nodes/matrix` |
| Mattermost | Communication & Messaging | `nodes/mattermost` |
| Rocket.Chat | Communication & Messaging | `nodes/rocket-chat` |
| LINE | Communication & Messaging | `nodes/line` |
| WeCom | Communication & Messaging | `nodes/wecom` |
| Pushover | Communication & Messaging | `nodes/pushover` |
| Pushbullet | Communication & Messaging | `nodes/pushbullet` |
| ntfy | Communication & Messaging | `nodes/ntfy` |
| RingCentral | Communication & Messaging | `nodes/ringcentral` |
| Aircall | Communication & Messaging | `nodes/aircall` |
| Email (SMTP/IMAP) | Communication & Messaging | `nodes/email` |
| Gotify | Communication & Messaging | `nodes/gotify` |
| SIGNL4 | Communication & Messaging | `nodes/signl4` |
| seven | Communication & Messaging | `nodes/seven` |
| Mocean | Communication & Messaging | `nodes/mocean` |
| MSG91 | Communication & Messaging | `nodes/msg91` |
| YouTube | Social Media | `nodes/youtube` |
| Instagram | Social Media | `nodes/instagram` |
| Reddit | Social Media | `nodes/reddit` |
| Bluesky | Social Media | `nodes/bluesky` |
| Twitter / X | Social Media | `nodes/twitter` |
| NewsAPI | News & Reference | `nodes/newsapi` |
| HackerNews | News & Reference | `nodes/hackernews` |
| arXiv | News & Reference | `nodes/arxiv` |
| OpenAlex | News & Reference | `nodes/openalex` |
| Semantic Scholar | News & Reference | `nodes/semantic-scholar` |
| Product Hunt | News & Reference | `nodes/producthunt` |
| Google Trends (opt-in) | News & Reference | `nodes/google-trends` |
| Shopify | E-Commerce & Payments | `nodes/shopify` |
| WooCommerce | E-Commerce & Payments | `nodes/woocommerce` |
| Magento | E-Commerce & Payments | `nodes/magento` |
| BigCommerce | E-Commerce & Payments | `nodes/bigcommerce` |
| PrestaShop | E-Commerce & Payments | `nodes/prestashop` |
| Squarespace Commerce | E-Commerce & Payments | `nodes/squarespace` |
| Wix Stores | E-Commerce & Payments | `nodes/wix` |
| Etsy | E-Commerce & Payments | `nodes/etsy` |
| Amazon SP-API | E-Commerce & Payments | `nodes/amazon-sp-api` |
| eBay | E-Commerce & Payments | `nodes/ebay` |
| Walmart Marketplace | E-Commerce & Payments | `nodes/walmart` |
| AliExpress | E-Commerce & Payments | `nodes/aliexpress` |
| Printful | E-Commerce & Payments | `nodes/printful` |
| Printify | E-Commerce & Payments | `nodes/printify` |
| ShipStation | E-Commerce & Payments | `nodes/shipstation` |
| Shippo | E-Commerce & Payments | `nodes/shippo` |
| EasyPost | E-Commerce & Payments | `nodes/easypost` |
| AfterShip | E-Commerce & Payments | `nodes/aftership` |
| Sendcloud | E-Commerce & Payments | `nodes/sendcloud` |
| Narvar | E-Commerce & Payments | `nodes/narvar` |
| Loop Returns | E-Commerce & Payments | `nodes/loop-returns` |
| Yotpo | E-Commerce & Payments | `nodes/yotpo` |
| Judge.me | E-Commerce & Payments | `nodes/judgeme` |
| Okendo | E-Commerce & Payments | `nodes/okendo` |
| Stamped | E-Commerce & Payments | `nodes/stamped` |
| ReCharge | E-Commerce & Payments | `nodes/recharge` |
| Bold Commerce | E-Commerce & Payments | `nodes/bold` |
| DHL | E-Commerce & Payments | `nodes/dhl` |
| Onfleet | E-Commerce & Payments | `nodes/onfleet` |
| Unleashed | E-Commerce & Payments | `nodes/unleashed` |
| Webflow | E-Commerce & Payments | `nodes/webflow` |
| Tapfiliate | E-Commerce & Payments | `nodes/tapfiliate` |
| Turso | Databases & Storage | `nodes/turso` |
| ClickHouse | Databases & Storage | `nodes/clickhouse` |
| CrateDB | Databases & Storage | `nodes/cratedb` |
| QuestDB | Databases & Storage | `nodes/questdb` |
| Snowflake | Databases & Storage | `nodes/snowflake` |
| Google BigQuery | Databases & Storage | `nodes/google-bigquery` |
| Databricks | Databases & Storage | `nodes/databricks` |
| Supabase | Databases & Storage | `nodes/supabase` |
| CouchDB | Databases & Storage | `nodes/couchdb` |
| Couchbase | Databases & Storage | `nodes/couchbase` |
| Elasticsearch | Databases & Storage | `nodes/elasticsearch` |
| OpenSearch | Databases & Storage | `nodes/opensearch` |
| InfluxDB | Databases & Storage | `nodes/influxdb` |
| Neo4j | Databases & Storage | `nodes/neo4j` |
| SurrealDB | Databases & Storage | `nodes/surrealdb` |
| Firebase | Databases & Storage | `nodes/firebase` |
| Azure Cosmos DB | Databases & Storage | `nodes/azure-cosmos-db` |
| Pinecone | Databases & Storage | `nodes/pinecone` |
| Weaviate | Databases & Storage | `nodes/weaviate` |
| Qdrant | Databases & Storage | `nodes/qdrant` |
| Milvus | Databases & Storage | `nodes/milvus` |
| Chroma | Databases & Storage | `nodes/chroma` |
| Google Pub/Sub | Databases & Storage | `nodes/google-pubsub` |
| RabbitMQ | Databases & Storage | `nodes/rabbitmq` |

Daemon-local capabilities that ship with every engine (not nodes in this image):
**Docling** document conversion.

## Category catalogs (~620 planned rows)

Each file lists one row per provider: operations (the `operation` values to
build), configuration parameters (auth + key options), ports, and legal notes.
Implementer-ready build contracts (current official API version, per-operation
fields, file-typed ports, ToS) live under [docs/specs/](specs/README.md) — one
markdown file per planned node, researched against the provider's live docs.

| Category | File | Rows |
|---|---|---:|
| Utilities & Core | [utilities-core.md](catalog/utilities-core.md) | 46 |
| Developer Tools | [catalog/developer-tools.md](catalog/developer-tools.md) | 35 |
| Communication & Messaging | [catalog/communication-messaging.md](catalog/communication-messaging.md) | 41 |
| Security & Identity | [catalog/security-identity.md](catalog/security-identity.md) | 30 |
| CRM & Marketing | [catalog/crm-marketing.md](catalog/crm-marketing.md) | 78 |
| Productivity & Collaboration | [catalog/productivity-collaboration.md](catalog/productivity-collaboration.md) | 42 |
| Files & Documents | [catalog/files-documents.md](catalog/files-documents.md) | 37 |
| Databases & Storage | [catalog/databases-storage.md](catalog/databases-storage.md) | 50 |
| Data & Analytics | [catalog/data-analytics.md](catalog/data-analytics.md) | 59 |
| AI & Machine Learning | [catalog/ai-ml.md](catalog/ai-ml.md) | 56 |
| Finance & Accounting | [catalog/finance-accounting.md](catalog/finance-accounting.md) | 40 |
| E-Commerce & Payments | [catalog/ecommerce-payments.md](catalog/ecommerce-payments.md) | 33 |
| Social Media | [catalog/social-media.md](catalog/social-media.md) | 36 |
| News & Reference | [catalog/news-reference.md](catalog/news-reference.md) | 35 |

A few providers legitimately span two categories (e.g. Google Drive in
Productivity and Files; Discord/Telegram/WhatsApp in Communication and Social).
The category file where the provider's primary use lives holds the canonical
row; the other carries a cross-reference — there is still only ever one node.

## Contributing

When you build a planned node: move its row to the shipped table, create
`nodes/<key>/node_definition.json` (with `category` and `tags` from this
taxonomy) and `nodes/<key>/README.md` (parameter-by-parameter documentation —
see any shipped node for the format), and register the handler in
`NodeHandlers.registerAll`. See [CONTRIBUTING.md](../CONTRIBUTING.md).
