# Telegram

- **Status:** planned (canonical category: communication-messaging)
- **Catalog:** `docs/catalog/social-media.md` (discovery index only). Canonical row: `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/telegram/`
- **Researched:** 2026-08-20
- **Official API:** Telegram Bot API 10.2
- **Docs:** https://core.telegram.org/bots/api
- **Auth:** `botToken` — secret bindings: `botToken`, `secretToken` (webhook header)

## Scope

This file is a **discovery cross-ref only**, not the build contract. The canonical implementer spec is [`docs/specs/communication-messaging/telegram.md`](../communication-messaging/telegram.md) (may not exist yet). **Do not fork a second node.** When built, use the same `nodes/telegram/` folder the communication-messaging catalog owns.

`listen` is webhook (public HTTPS) or long poll on this node — not a second node. Bots cannot DM users who have not started them.

## Operations (`operation` config values)

See the canonical spec and the Telegram row in `docs/catalog/communication-messaging.md`. Do not keep a second operation list here.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | first verb | no | Values from the canonical spec only |
| `botToken` | password | yes | empty | yes | Bot token from BotFather (`auth=botToken`) |
| `listen` | select | for inbound | `webhook` | no | `webhook` (HTTPS) \| `longPoll` |
| `secretToken` | password | webhook `listen` | empty | yes | `X-Telegram-Bot-Api-Secret-Token` header |

HTTPS webhook vs long poll is config on this node, not a fork.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include the bot token |

FUNCTION ports: none. **1 IN / 1 OUT.**

Where the Bot API uploads or downloads media, fields use type `file` (platform ref-id), never inline bytes.

## Input fields (by operation)

See [`docs/specs/communication-messaging/telegram.md`](../communication-messaging/telegram.md).

## Output fields (by operation)

See [`docs/specs/communication-messaging/telegram.md`](../communication-messaging/telegram.md).

## Legal

- https://telegram.org/tos (includes additional Bot Developers terms)
- Bot FAQ / limits: https://core.telegram.org/bots/faq
- Official Bot API only. No unofficial MTProto user-account wrappers, session export, or scrape of telegram.org.
- Chat content is personal data. Never log `botToken`, `secretToken`, or message bodies.
- Published broadcast pacing (~30 messages/second without paid broadcasts) applies; HTTP 429 must map to `{ error }`.

## Implementation notes

- JDK HTTP only (no vendor SDK) unless the canonical spec states a reason.
- HTTPS origin: `https://api.telegram.org/bot<token>/<method>` (token never logged).
- Timeouts, size caps, retry, and error mapping live in the canonical spec.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
