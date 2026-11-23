# WhatsApp

- **Status:** planned (canonical category: communication-messaging)
- **Catalog:** `docs/catalog/social-media.md` (discovery index only). Canonical row: `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/whatsapp/`
- **Researched:** 2026-08-20
- **Official API:** WhatsApp Business Cloud API (Meta Graph) v26.0
- **Docs:** https://developers.facebook.com/docs/whatsapp/cloud-api
- **Auth:** `metaSystemUserToken` — secret bindings: `metaSystemUserToken`, `appSecret`, `verifyToken`; ids: `phoneNumberId`

## Scope

This file is a **discovery cross-ref only**, not the build contract. The canonical implementer spec is [`docs/specs/communication-messaging/whatsapp.md`](../communication-messaging/whatsapp.md) (may not exist yet). **Do not fork a second node.** When built, use the same `nodes/whatsapp/` folder the communication-messaging catalog owns.

Inbound messages are the `listen` operation (app webhook + HMAC) on this node — not a second node. Session replies vs template sends are operations on the same node (24-hour customer-care window vs approved templates).

## Operations (`operation` config values)

See the canonical spec and the WhatsApp row in `docs/catalog/communication-messaging.md`. Do not keep a second operation list here.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | first verb | no | Values from the canonical spec only |
| `metaSystemUserToken` | password | yes | empty | yes | Meta system-user token (`auth=metaSystemUserToken`) |
| `phoneNumberId` | string | yes | empty | no | Cloud API phone-number id |
| `appSecret` | password | for `listen` | empty | yes | Webhook HMAC (`X-Hub-Signature-256`) |
| `verifyToken` | password | for `listen` | empty | yes | Webhook hub.verify_token |

Graph version is config on the canonical node (current: **v26.0**, released 2026-07-29). Base: `https://graph.facebook.com/v26.0/`.

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Failures never include tokens or `appSecret` |

FUNCTION ports: none. **1 IN / 1 OUT.**

Where the Cloud API uploads or downloads media, fields use type `file` (platform ref-id), never inline bytes.

## Input fields (by operation)

See [`docs/specs/communication-messaging/whatsapp.md`](../communication-messaging/whatsapp.md).

## Output fields (by operation)

See [`docs/specs/communication-messaging/whatsapp.md`](../communication-messaging/whatsapp.md).

## Legal

- WhatsApp Business Messaging Policy: https://www.whatsapp.com/legal/business-policy
- Meta Platform Terms: https://developers.facebook.com/terms
- Official Cloud API (Graph) only. No unofficial multi-device, web, or companion-app wrappers.
- Recipients must have opted in. Outside the 24-hour window, only approved templates. Message bodies and phone numbers are personal data — never log them or the system-user token.
- Business verification and quality-tier sending limits as published by Meta.

## Implementation notes

- JDK HTTP only (no vendor SDK) unless the canonical spec states a reason.
- Timeouts, size caps, retry, and error mapping live in the canonical spec.
- HTTP 4xx/5xx → `{ error }` on OUTPUT, never throw past dispatch.
