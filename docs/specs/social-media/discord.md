# Discord

- **Status:** planned (canonical category: communication-messaging)
- **Catalog:** `docs/catalog/social-media.md` (discovery index only). Canonical row: `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/discord/`
- **Researched:** 2026-08-20
- **Official API:** Discord API v10
- **Docs:** https://discord.com/developers/docs
- **Auth:** `botToken` \| `oauth2` \| `webhookUrl` — secret bindings: `botToken`, OAuth tokens, `webhookUrl`; `publicKey` when verifying interactions

## Scope

This file is a **discovery cross-ref only**, not the build contract. The canonical implementer spec is [`docs/specs/communication-messaging/discord.md`](../communication-messaging/discord.md) (may not exist yet). **Do not fork a second node.** When built, use the same `nodes/discord/` folder the communication-messaging catalog owns.

Incoming webhooks are an auth mode on this node (channel-scoped send-only), not a second node. Inbound events are the `listen` operation (Gateway / HTTP interactions).

## Operations (`operation` config values)

See the canonical spec and the Discord row in `docs/catalog/communication-messaging.md`. Do not keep a second operation list here.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | first verb | no | Values from the canonical spec only |
| `auth` | select | yes | `botToken` | no | `botToken` \| `oauth2` \| `webhookUrl` |
| `botToken` | password | when `auth=botToken` | empty | yes | Bot token (`Authorization: Bot`) |
| `webhookUrl` | password | when `auth=webhookUrl` | empty | yes | Incoming webhook URL (send-only) |
| `publicKey` | string | for interaction `listen` | empty | no | Verify interaction signatures |

OAuth access / refresh tokens follow the canonical spec and are secrets. Privileged intents (members, message content) are Discord-portal flags, not extra nodes.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens |

FUNCTION ports: none. **1 IN / 1 OUT.**

Where the API uploads or downloads message attachments / files, fields use type `file` (platform ref-id), never inline bytes.

## Input fields (by operation)

See [`docs/specs/communication-messaging/discord.md`](../communication-messaging/discord.md).

## Output fields (by operation)

See [`docs/specs/communication-messaging/discord.md`](../communication-messaging/discord.md).

## Legal

- https://discord.com/terms
- Developer Policy as published from https://discord.com/developers/docs
- Versioning: https://discord.com/developers/docs/reference#api-versioning — API **v10** listed Available (2026-08-20); REST base `https://discord.com/api/v10`
- Official Discord API only. No unofficial wrappers, undocumented client endpoints, or user-token impersonation.
- Message content and member lists are personal data. Never log tokens or message bodies. Honor published per-route rate limits.

## Implementation notes

- JDK HTTP only (no vendor SDK) unless the canonical spec states a reason.
- Timeouts, size caps, retry, and error mapping live in the canonical spec.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
