# Midjourney

- **Status:** planned
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder when built:** *none — do not create `nodes/midjourney/`*
- **Researched:** 2026-08-20
- **Official API:** **none** (no public self-serve API; no public enterprise API program found today)
- **Docs:** Midjourney documents the product at [docs.midjourney.com](https://docs.midjourney.com/); they do **not** publish a REST/RPC developer API
- **Auth:** n/a

## Scope

**DO NOT BUILD.**

Midjourney, Inc. provides image/video generation through **midjourney.com** and **Discord servers**. There is no official public self-serve HTTP API. Community Guidelines state that Midjourney does not provide an API and that automating interactions is prohibited. Terms of Service (effective **2026-05-27**) forbid automated tools that access, interact with, or generate assets.

Corporate billing / group plans (50+ Pro/Mega seats) are **seat billing**, not a documented developer API.

Do not invent operations. Do not wrap Discord bots, the web UI, or third-party “MJ API” proxies.

## Operations (`operation` config values)

None. Do not invent `imagine`, `upscale`, `vary`, `blend`, or Discord slash-command verbs.

## Evidence (fetched 2026-08-20)

| Source | What it says |
|---|---|
| [Terms of Service](https://docs.midjourney.com/hc/en-us/articles/32083055291277-Terms-of-Service) | “You may not use automated tools to access, interact with, or generate Assets through the Services.” Services include the Site and Discord servers. |
| [Community Guidelines](https://docs.midjourney.com/hc/en-us/articles/32013696484109-Community-Guidelines) §7 | “With a few rare exceptions that are explicitly granted, Midjourney does not provide an API, nor provide third-party apps or scripts, and automating interactions with Midjourney service is strictly prohibited.” |
| [Group / corporate billing](https://docs.midjourney.com/hc/en-us/articles/27870607078285-Group-Plans-Corporate-Billing) | Bulk seats / SSO / vendor process — **not** an API. |
| [Discord Terms](https://discord.com/terms/) | Discord APIs/SDKs are governed by Discord Developer Terms; automating Midjourney **through Discord** without Midjourney’s API is still Midjourney ToS + Discord policy. |
| [Discord Developer Terms](https://support-dev.discord.com/hc/en-us/articles/8562894815383-Discord-Developer-Terms-of-Service) | Official Discord developer surface only; not a license to scrape Midjourney’s bot. |

**Unverified / not found today:** a public Midjourney Enterprise HTTP API, OpenAPI document, or self-serve developer portal. If Midjourney later publishes an official API, write a new spec from those docs — still no Discord-bot ops.

## Ports

None.

## Legal

- Midjourney ToS: https://docs.midjourney.com/hc/en-us/articles/32083055291277-Terms-of-Service
- Community Guidelines: https://docs.midjourney.com/hc/en-us/articles/32013696484109-Community-Guidelines
- Discord ToS: https://discord.com/terms/
- Discord Developer Terms: https://support-dev.discord.com/hc/en-us/articles/8562894815383-Discord-Developer-Terms-of-Service
- Official-API-first ([LEGAL.md](../../../LEGAL.md)): no scrape, no unofficial Discord/web wrapper, no third-party proxy keys.

## Implementation notes

Do not ship a handler. Use another official image provider node (Stability AI, Ideogram, Recraft, …) when the workflow needs programmatic generation.
