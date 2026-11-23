# LINE

- **Status:** planned (canonical category: communication-messaging)
- **Catalog:** `docs/catalog/social-media.md` (discovery index only). Canonical row: `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/line/`
- **Researched:** 2026-08-20
- **Official API:** LINE Messaging API (`/v2/bot`)
- **Docs:** https://developers.line.biz/en/docs/messaging-api/
- **Auth:** `channelAccessToken` — secret bindings: `channelAccessToken`, `channelSecret`

## Scope

This file is a **discovery cross-ref only**, not the build contract. The canonical implementer spec is [`docs/specs/communication-messaging/line.md`](../communication-messaging/line.md) (may not exist yet). **Do not fork a second node.** When built, use the same `nodes/line/` folder the communication-messaging catalog owns.

LINE is messaging-primary; this social-media page exists only for catalog discovery. Inbound events are the `listen` operation (webhook + HMAC) on this node — not a second node.

**Do not implement LINE Notify.** Official end of service: 2025-03-31; all Notify features unavailable from 2025-04-01 ([LINE Notify notice](https://notify-bot.line.me/doc/en/)). No `nodes/line-notify/`, no Notify tokens, no `notify-api.line.me`.

## Operations (`operation` config values)

See the canonical spec and the LINE row in `docs/catalog/communication-messaging.md`. Do not keep a second operation list here.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | first verb | no | Values from the canonical spec only |
| `channelAccessToken` | password | yes | empty | yes | Channel access token (`auth=channelAccessToken`) |
| `channelSecret` | password | for `listen` | empty | yes | HMAC for webhook (`x-line-signature`) |

Reply tokens expire. Send modes are operations on this node, not extra nodes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include channel secrets |

FUNCTION ports: none. **1 IN / 1 OUT.**

Where the Messaging API uploads or downloads media (user content, rich-menu images), fields use type `file` (platform ref-id), never inline bytes.

## Input fields (by operation)

See [`docs/specs/communication-messaging/line.md`](../communication-messaging/line.md).

## Output fields (by operation)

See [`docs/specs/communication-messaging/line.md`](../communication-messaging/line.md).

## Legal

- Messaging API development guidelines: https://developers.line.biz/en/docs/messaging-api/development-guidelines/
- Reference: https://developers.line.biz/en/reference/messaging-api/
- Official Messaging API only. No unofficial wrappers. LINE Notify is retired — do not call it.
- Profile and message content are personal data. Never log `channelAccessToken`, `channelSecret`, or message bodies.
- Honor published rate limits; do not load-test through the LINE Platform. Verify webhook signatures; do not IP-allowlist LINE (addresses are unpublished and change).

## Implementation notes

- JDK HTTP only (no vendor SDK) unless the canonical spec states a reason.
- HTTPS origin: `https://api.line.me` (`/v2/bot/…`).
- Timeouts, size caps, retry, and error mapping live in the canonical spec.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
