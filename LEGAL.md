# Legal & compliance

This document states how the SQ Builtin Node Library treats third-party terms,
unofficial endpoints, secrets, and personal data. It is not legal advice. The
operator of a StudioQuack deployment — and of each workflow — is responsible
for complying with applicable law and with each provider’s terms.

## Official-API-first policy

Built-in nodes call **documented, official provider APIs** (REST, GraphQL, XRPC,
Atom, OpenAI-compatible chat completions) using credentials the operator
supplies. The library does not ship a general-purpose non-consensual scraper,
and it does not bypass the StudioQuack daemon’s network posture (SSRF guard
and egress policy).

Prefer, in order:

1. The provider’s official API and developer programme.
2. A licensed data partner or official export.
3. An unofficial HTTP endpoint **only** when the node is explicitly marked
   unofficial **and** the operator has opted in on that node instance (see
   [Unofficial endpoints](#unofficial-endpoints)).

`robots.txt` is honored by default on the Web Browser node. This library does
not document a bypass.

## User responsibility

By placing a node on a workflow and running it, **you** (the workflow operator)
are responsible for:

- accepting and complying with the provider terms linked below;
- holding any required developer account, API key, OAuth grant, or attribution;
- using retrieved content only as those terms and applicable law allow
  (copyright, database rights, privacy, publicity, anti-scraping rules);
- not using these nodes for non-consensual collection of personal data, for
  circumventing technical access controls, or for any unlawful purpose;
- configuring daemon egress so the node can reach only the hosts you intend.

StudioQuack provides the runtime. It does not accept the provider’s terms on
your behalf and does not supervise how you use a provider’s data.

## Unofficial endpoints

Unofficial endpoints are **opt-in only**, clearly marked on the node, and
**off by default**.

| Node | Status | Gate |
| --- | --- | --- |
| `google-trends` | Unofficial Java port of pytrends HTTP endpoints under `trends.google.com` (not a Google Cloud API) | `configurationParameters.accept_google_terms` must be `true`. Otherwise the handler returns the error output port and **does not call Google**. Prefer [Google Ads API](https://developers.google.com/google-ads/api/docs/start), [Search Console API](https://developers.google.com/webmaster-tools), or a licensed data partner. |

No other built-in node is classified as an unofficial scraper. Social nodes
call the provider’s public developer APIs (YouTube Data API v3, Meta Graph API,
Reddit OAuth / public JSON hosted by Reddit, Bluesky AT Protocol XRPC, X API
v2). Reddit’s unauthenticated public JSON path is a compatibility fallback;
operators should use OAuth2 (`oauth.reddit.com`) for production.

## Per-provider terms

Confirm the live URL before relying on it; providers move policy pages.

| Node | Category | Terms / developer policy |
| --- | --- | --- |
| `ai-agent` | AI & Machine Learning | Terms of the **connected** model node (OpenAI, local engine, or other). Prompts and context leave this container to that provider. |
| `openai-models` | AI & Machine Learning | [OpenAI Terms of Use](https://openai.com/policies/row-terms-of-use), [Usage policies](https://openai.com/policies/usage-policies), [API data usage](https://openai.com/policies/api-data-usage-policies) |
| `local-llm` | AI & Machine Learning | Operator’s local OpenAI-compatible engine (Ollama, vLLM, llama.cpp, TGI, or BYO). Traffic stays on the daemon’s LLM provider proxy; it does not go to OpenAI cloud. |
| `http-request` | Developer Tools | Terms of the **target URL** you configure. Egress is governed by the daemon’s network posture (SSRF guard); this node never bypasses engine network rules. |
| `web-browser` | Developer Tools | Terms of the **target site**, plus that site’s `robots.txt`. Honored by default. |
| `github` | Developer Tools | [GitHub Terms of Service](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service), [GitHub Terms for additional products](https://docs.github.com/en/site-policy/github-terms/github-terms-for-additional-products-and-features) (APIs) |
| `devto` | Developer Tools | [DEV Community (Forem) Terms](https://dev.to/terms), [Forem API](https://developers.forem.com/api) |
| `web-search` | Data & Analytics | [Tavily Terms](https://www.tavily.com/terms) (default search backend) |
| `newsapi` | News & Reference | [NewsAPI Terms](https://newsapi.org/terms) — developer tier requires attribution and restricts production use |
| `hackernews` | News & Reference | [HN Search API (Algolia)](https://hn.algolia.com/api), [Hacker News guidelines](https://news.ycombinator.com/newsguidelines.html) |
| `arxiv` | News & Reference | [arXiv API Terms of Use](https://info.arxiv.org/help/api/tou.html) |
| `openalex` | News & Reference | [OpenAlex about / license](https://openalex.org/about), [API docs](https://docs.openalex.org) |
| `semantic-scholar` | News & Reference | [Semantic Scholar API license](https://www.semanticscholar.org/product/api/license) |
| `google-trends` | News & Reference | [Google Terms of Service](https://policies.google.com/terms) — **unofficial, opt-in** (see above) |
| `producthunt` | News & Reference | [Product Hunt Terms](https://www.producthunt.com/legal/terms-of-service), [API](https://api.producthunt.com/v2/docs) |
| `google-sheets` | Productivity & Collaboration | [Google APIs Terms of Service](https://developers.google.com/terms), [Sheets API](https://developers.google.com/workspace/sheets/api/guides/authorizing) |
| `youtube` | Social Media | [YouTube API Services Terms](https://developers.google.com/youtube/terms/api-services-terms-of-service), [Google APIs Terms](https://developers.google.com/terms) |
| `instagram` | Social Media | [Meta Platform Terms](https://developers.facebook.com/terms), [Commercial Terms](https://www.facebook.com/legal/commercial_terms) |
| `reddit` | Social Media | [Reddit Developer Terms](https://www.redditinc.com/policies/developer-terms), [Reddit User Agreement](https://www.reddit.com/policies/user-agreement) |
| `bluesky` | Social Media | [Bluesky Terms of Service](https://bsky.social/about/support/tos), [AT Protocol](https://atproto.com) |
| `twitter` | Social Media | [X Developer Agreement and Policy](https://developer.x.com/en/developer-terms/agreement-and-policy) |
| `discourse` | Social Media | [Discourse REST docs](https://docs.discourse.org/), [Discourse legal portal](https://www.discourse.org/legal) — plus the target forum’s own terms |
| `google-business-profile` | Social Media | [Google APIs Terms](https://developers.google.com/terms), [Business Profile APIs](https://developers.google.com/my-business) |
| `spotify` | Social Media | [Spotify Developer Terms](https://developer.spotify.com/terms), [Developer Policy](https://developer.spotify.com/policy) |
| `redis-cache` | Databases & Storage | Customer-owned Redis. [Redis license / terms](https://redis.io/legal/licenses/) of the server you run. StudioQuack does not host Redis. |
| `pgvector` | Databases & Storage | Customer-owned PostgreSQL + [pgvector](https://github.com/pgvector/pgvector). Postgres and extension licenses of the instance you run. StudioQuack does not host Postgres. |
| `postgresql` | Databases & Storage | Customer-owned PostgreSQL. [PostgreSQL License](https://www.postgresql.org/about/licence/) of the server you run. StudioQuack does not host PostgreSQL. |
| `mysql` | Databases & Storage | Customer-owned MySQL or MariaDB. License and terms of the server you run. StudioQuack does not host MySQL. Driver: MariaDB Connector/J (LGPL-2.1). |
| `microsoft-sql-server` | Databases & Storage | Customer-owned SQL Server or Azure SQL. Microsoft license terms of the instance you run. StudioQuack does not host SQL Server. |
| `sqlite` | Databases & Storage | SQLite is in the [public domain](https://www.sqlite.org/copyright.html); driver sqlite-jdbc (Apache-2.0). The database file is customer data supplied by the workflow; StudioQuack stores no copy. |
| `oracle-database` | Databases & Storage | Customer-owned Oracle Database. Oracle license terms of the database you run. StudioQuack does not host Oracle Database. Driver: Oracle JDBC, **not bundled** — the operator installs it and accepts the [Oracle Free Use Terms and Conditions](https://www.oracle.com/downloads/licenses/oracle-free-license.html) (see `nodes/oracle-database/README.md`). |

## Data protection

- **AI nodes (`ai-agent`, `openai-models`):** prompts, attachments, tool
  payloads, and conversation context **leave the host** to the configured model
  provider. Treat that as a disclosure of personal data under GDPR and similar
  laws unless you have a lawful basis, a processor agreement with the provider,
  and minimization in the workflow.
- **`redis-cache` / `pgvector`:** conversation turns and retrieved documents
  leave this container for the **customer-owned** Redis or PostgreSQL the
  operator configured. StudioQuack does not host those stores. Passwords stay
  in `configurationParameters` and are never logged.
- **`local-llm`:** prompts and context stay on the **daemon’s provider proxy**
  (Engine Settings). They do not go to OpenAI cloud. They still leave this
  container for the operator-chosen local engine.
- **Minimize.** Do not send government IDs, health data, or secrets in prompts
  unless the workflow is designed for that and the provider contract covers it.
- **Logs.** Handlers log shapes, ids, status codes, and sizes — not prompt
  bodies, file bytes, cookies, or secret values.

## Secrets custody

- Secrets (API keys, tokens, passwords, service-account JSON) come **only** from
  `configurationParameters` (password fields resolved by the daemon).
- They must **never** be placed on INPUT ports / edges. Instagram and Twitter
  still list legacy token fields on the input schema for graph compatibility;
  the Java handlers **ignore** those fields.
- Secrets are daemon-held, resolved just-in-time, and **never logged** or
  returned on output ports.
- Rotation is the operator’s duty (refs stay stable; values move in the secret
  store).

## DMCA / takedown

Copyright complaints about content this library’s operators retrieve or store
should be directed to the operator of that deployment first — StudioQuack does
not host workflow outputs.

To notify StudioQuack about alleged infringement **in this repository**
(source, docs, or bundled node definitions), email:

**legal@studioquack.com**

or write to:

**StudioQuack, Inc.**
1111b South Governors Ave, STE 37760
Dover, DE 19904, United States

Include: your contact details, the work claimed, the material’s URL or path in
this repo, a statement of good-faith belief, and a statement under penalty of
perjury that you are authorized to act. We will acknowledge designated-agent
notices in line with 17 U.S.C. § 512. Takedown notices for material hosted on
GitHub can also be submitted through GitHub’s DMCA takedown process.

Security vulnerabilities: **security@studioquack.com** ([SECURITY.md](SECURITY.md)).
License: [LICENSE](LICENSE) (Apache-2.0). Trademarks: [TRADEMARKS.md](TRADEMARKS.md).
