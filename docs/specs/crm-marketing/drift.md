# Drift

- **Status:** planned
- **Catalog:** `docs/catalog/crm-marketing.md`
- **Folder when built:** `nodes/drift/`
- **Researched:** 2026-08-20
- **Official API:** Drift REST (Salesloft-owned). Host **`https://driftapi.com`**. No numeric version in the path; current developer portal is **devdocs.drift.com** (fetched 2026-08-20).
- **Docs:** https://devdocs.drift.com/docs/using-drift-apis · backend: https://devdocs.drift.com/docs/platform-apis · auth: https://devdocs.drift.com/docs/authentication-and-scopes · index: https://devdocs.drift.com/llms.txt
- **Auth:** OAuth 2.0 authorization code (`POST https://driftapi.com/oauth2/token`) **or** Personal Access Token — secret bindings: `clientSecret`, `accessToken`, `refreshToken`. Header `Authorization: Bearer`.

## Scope

**Confirmed live (2026-08-20):** Salesloft still publishes Drift backend APIs at `driftapi.com` and `devdocs.drift.com`. Contacts (end users / visitors — **not** seat holders), conversations, messages, custom attribute catalog, and app webhooks. SCIM (`/scim/Users`) is IT provisioning — out of this node.

One node. Catalog `contact.customAttributes` = list attribute **definitions** (`GET /contacts/attributes`), not a free-form dump of every PII attribute on a person. `trigger.listen` uses the Drift **app Events URL** (dev.drift.com) pointing at StudioQuack ingress; verify `X-Verification-Token`. Do not scrape app.drift.com.

Unauthenticated probe 2026-08-20: both `https://driftapi.com/conversations/list` and `https://api.drift.com/conversations/list` return **HTTP 401** `WWW-Authenticate: Bearer` (Kong). Use **`https://driftapi.com`** as the single base (same as contacts/messages). Do not dual-call.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `contact.create` | POST | `/contacts` | Create; required `attributes.email` |
| `contact.get` | GET | `/contacts/{contactId}` or `/contacts?email=` | By id (one) or email (list) |
| `contact.update` | PATCH | `/contacts/{contactId}` | Patch `attributes` |
| `contact.delete` | DELETE | `/contacts/{contactId}` | Delete contact |
| `contact.customAttributes` | GET | `/contacts/attributes` | Attribute schema (`all_contact_read` scope) |
| `conversation.list` | GET | `/conversations/list` | Page conversations (`limit` ≤ 100, `page_token`, `statusId`) |
| `message.send` | POST | `/conversations/{conversationId}/messages` | Chat or private note |
| `trigger.listen` | — | App Events URL (dev.drift.com) | Subscribe to event types |

Dropped: `conversation_status_updated` webhook (deprecated). Do not use guessable `externalId` values.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `contact.get` | no | Chooses the row above |
| `clientId` | string | if oauth | | no | Drift app client id |
| `clientSecret` | password | if oauth | | yes | OAuth secret |
| `accessToken` | password | yes | | yes | Bearer |
| `refreshToken` | password | no | | yes | OAuth refresh |
| `verificationToken` | password | trigger.listen | | yes | App verification token (compare to header/body `token`) |
| `timeoutMs` | number | no | `30000` | no | Read timeout |
| `maxResponseBytes` | number | no | `1048576` | no | Response cap |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo Bearer or verification token |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = contact.create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email` | string | yes | Unique unless `externalId` overrides |
| `externalId` | string | no | Unguessable id for `drift.identify` |
| `name` | string | no | Display name |
| `attributes` | object | no | Extra attributes — minimize |

### `operation = contact.get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | number | if no email | Drift contact id |
| `email` | string | if no id | May return multiple |
| `idType` | string | no | `external` when looking up by your id |
| `externalId` | string | with idType | Query `id=` |

### `operation = contact.update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | number | yes | Contact id |
| `attributes` | object | yes | Patch map (success only if something changed) |

### `operation = contact.delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `contactId` | number | yes | Contact id |

### `operation = conversation.list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `limit` | number | no | Max 100, default 25 |
| `statusId` | number | no | `1` OPEN, `2` CLOSED, `3` PENDING |
| `pageToken` | string | no | Empty on first page |

### `operation = message.send`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `conversationId` | number | yes | Conversation |
| `type` | select | yes | `chat` or `private_note` |
| `body` | string | no | HTML (no JavaScript) |
| `userId` | number | no | Agent; default bot user |
| `buttons` | object[] | no | Reply buttons |

Conversation transcripts and attachments exist on other endpoints — **out of catalog**; do not add silent download of recordings.

### `operation = trigger.listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `eventTypes` | string[] | yes | Subset of current types: `contact_identified`, `contact_updated`, `new_message`, `new_conversation`, `conversation_push`, … (skip deprecated `conversation_status_updated`) |
| `requestUrl` | string | yes | HTTPS ingress — operator also pastes this into the Drift app Events field |

Handler confirms `verificationToken` is bound and returns `{ status: "ready" }`. Webhook **create** is app-portal configuration, not a documented public POST for arbitrary URLs beyond the app.

## Output fields (by operation)

### `operation = contact.create` / `contact.update` / `contact.get` (by id)

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | number | 200 | Contact id |
| `data` | object | 200 | Contact wrapper — log id only |

### `operation = contact.get` (by email)

| Field | Type | When | Purpose |
|---|---|---|---|
| `contacts` | object[] | 200 | Possible duplicates |

### `operation = contact.delete`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | 200 | Deleted |

### `operation = contact.customAttributes`

| Field | Type | When | Purpose |
|---|---|---|---|
| `properties` | object[] | 200 | `{ name, displayName, type }` |

### `operation = conversation.list`

| Field | Type | When | Purpose |
|---|---|---|---|
| `conversations` | object[] | 200 | Summaries |
| `nextPageToken` | string | if more | From `links.next` |

### `operation = message.send`

| Field | Type | When | Purpose |
|---|---|---|---|
| `id` | number | 200 | Message id |
| `data` | object | 200 | Message — do not log HTML |

### `operation = trigger.listen`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | ready | `ready` |
| `eventTypes` | string[] | ready | Filter |

Failures: `{ error }`.

## Legal

- Docs: https://devdocs.drift.com/docs/using-drift-apis
- GDPR retrieve/delete APIs exist for admins: https://devdocs.drift.com/docs/automating-gdpr-retrieval-and-deletion — not catalog verbs; customer remains controller.
- Chat transcripts, emails, phones (`phone_captured`), and geo on messages are PII. Minimize attributes. Never log tokens, message HTML, or IP/geo from message `context`.
- StudioQuack does not accept Drift/Salesloft ToS. Customer owns the Drift org.
- Rate limits: plan-dependent (public summaries cite ~200 req/min on some plans). Honor 429.

## Implementation notes

- JDK HTTP only. Base `https://driftapi.com`. Refresh via `POST /oauth2/token` (`grant_type=refresh_token`).
- Timeouts 10s/`timeoutMs`; request ≤ 256 KiB; response ≤ `maxResponseBytes`.
- Retry `429`/`5xx` max 3. Other 4xx → `{ error }`.
- Ingress: compare `X-Verification-Token` (and body `token`) to `verificationToken` with constant-time equality.
