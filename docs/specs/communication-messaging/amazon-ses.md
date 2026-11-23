# Amazon SES

- **Status:** planned
- **Catalog:** `docs/catalog/communication-messaging.md`
- **Folder when built:** `nodes/amazon-ses/`
- **Researched:** 2026-08-20
- **Official API:** Amazon SES **API v2** (JSON REST). **This node does not call SES API v1.**
- **Docs:** [API v2 Welcome](https://docs.aws.amazon.com/ses/latest/APIReference-V2/Welcome.html) (published 2026-08-20) · [SendEmail](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html) · [CreateEmailTemplate](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_CreateEmailTemplate.html) · [CreateEmailIdentity](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_CreateEmailIdentity.html) · [Custom verification templates](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_CreateCustomVerificationEmailTemplate.html) · [Endpoints](https://docs.aws.amazon.com/general/latest/gr/ses.html) · [Event publishing](https://docs.aws.amazon.com/ses/latest/dg/monitor-using-event-publishing.html) · [SNS event destination](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-add-event-destination-sns.html) · [AWS Signature Version 4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv.html)
- **Auth:** `api_key` (IAM access key) — secret binding: `secret_access_key`

## Scope

Uses **only SES API v2** over HTTPS: host `email.{region}.amazonaws.com`, paths under `/v2/email/…`. SigV4 signing service name is **`ses`** (not `email` — that yields 403 `SignatureDoesNotMatch`). No AWS SDK. New accounts start in the **sandbox** (verified identities only, tight volume). Production access is an AWS support request. `listen` is configuration-set **event publishing** (bounce, complaint, delivery, …) via SNS HTTPS subscription to StudioQuack ingress — same node, not a sibling.

**Why v2, not v1:** AWS documents API v2 as the current programmatic surface (`SendEmail` carries Simple, Raw, **or Template** content). v1 `SendEmail` / `SendTemplatedEmail` query API is not used. Seed verb `send-templated-email` stays; it maps to v2 `SendEmail` with `Content.Template`, not v1 `SendTemplatedEmail`. Template CRUD uses `CreateEmailTemplate` et al. (`/v2/email/templates`), not v1 `CreateTemplate`.

**Do not build:** SES v1 JSON/query API; SMTP interface (Email node); AWS SDK (`software.amazon.awssdk:sesv2`); `SendBulkEmail`; receipt rules / inbound; dedicated IP pools; v1 template names (`CreateTemplate`).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `send-email` | POST | `/v2/email/outbound-emails` | `Content.Simple` (or Raw). |
| `send-templated-email` | POST | `/v2/email/outbound-emails` | Same `SendEmail`; `Content.Template` + `TemplateData`. |
| `create-template` | POST | `/v2/email/templates` | `CreateEmailTemplate` |
| `get-template` | GET | `/v2/email/templates/{TemplateName}` | `GetEmailTemplate` |
| `list-templates` | GET | `/v2/email/templates` | `ListEmailTemplates` (`PageSize`, `NextToken`) |
| `update-template` | PUT | `/v2/email/templates/{TemplateName}` | `UpdateEmailTemplate` |
| `delete-template` | DELETE | `/v2/email/templates/{TemplateName}` | `DeleteEmailTemplate` |
| `create-custom-verification-template` | POST | `/v2/email/custom-verification-email-templates` | Custom verify-address mail |
| `get-custom-verification-template` | GET | `/v2/email/custom-verification-email-templates/{TemplateName}` | |
| `list-custom-verification-templates` | GET | `/v2/email/custom-verification-email-templates` | |
| `update-custom-verification-template` | PUT | `/v2/email/custom-verification-email-templates/{TemplateName}` | |
| `delete-custom-verification-template` | DELETE | `/v2/email/custom-verification-email-templates/{TemplateName}` | |
| `create-identity` | POST | `/v2/email/identities` | Start verify of email or domain (`CreateEmailIdentity`) |
| `listen` | inbound HTTPS | SES → config set → SNS HTTPS → ingress | Bounce/complaint/delivery events. Confirm SNS `SubscriptionConfirmation`. |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `send-email` | no | Chooses the row above |
| `access_key_id` | password | yes | — | **yes** | IAM access key id |
| `secret_access_key` | password | yes | — | **yes** | IAM secret. SigV4 only. Never log. |
| `region` | string | yes | — | no | e.g. `us-east-1`. Identities and config sets are **per region**. |
| `ConfigurationSetName` | string | recommended | — | no | Default SESv2 body field when INPUT omits it. Required for useful `listen`. |
| `from_email` | string | send if input omits | — | no | Verified From |
| `timeout_seconds` | number | no | `25` | no | 0 / negative / blank → 25 |

IAM: least privilege `ses:SendEmail`, `ses:CreateEmailTemplate`, `ses:GetEmailTemplate`, `ses:ListEmailTemplates`, `ses:UpdateEmailTemplate`, `ses:DeleteEmailTemplate`, custom-verification-template verbs, `ses:CreateEmailIdentity` as used. No `iamRole` STS assume-role in v1 of this node (catalog mentioned it — **deferred**; access keys only).

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | Never keys |

FUNCTION ports: none. Attachments: `file` ref-ids. Simple send uses MIME built by the handler (Raw `Content.Raw`) when attachments are present; without attachments use `Content.Simple`. Never inline bytes on the port.

## Input fields (by operation)

### `operation = send-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | if config blank | Verified address |
| `to` | array of string | yes | ToAddresses |
| `cc` / `bcc` | array of string | no | |
| `reply_to` | array of string | no | |
| `subject` | string | yes | Simple subject |
| `html` / `text` | string | one of | Body |
| `attachments` | array of `{file, filename?, mime_type?}` | no | Forces Raw MIME path |
| `email_tags` | array of `{name, value}` | no | Event-publishing tags |
| `ConfigurationSetName` | string | no | Overrides config |

### `operation = send-templated-email`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from` | string | if config blank | |
| `to` / `cc` / `bcc` | array of string | to yes | |
| `template_name` | string | yes | Stored template |
| `template_data` | object | yes | JSON merge data (stringified for API) |
| `ConfigurationSetName` | string | no | Overrides config |

### Template CRUD

| Field | Type | Required | Operations | Purpose |
|---|---|---|---|---|
| `template_name` | string | yes | all but list | Name |
| `subject` / `html` / `text` | string | create/update | | `TemplateContent` |
| `page_size` / `next_token` | number / string | list | | Pagination |

### Custom verification templates

| Field | Type | Required | Purpose |
|---|---|---|---|
| `template_name` | string | yes (except list) | |
| `from_email_address` | string | create/update | |
| `template_subject` / `template_content` | string | create/update | Body may include HTML; size &lt; 10 MB |
| `success_redirection_url` / `failure_redirection_url` | string | create/update | |

### `operation = create-identity`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `email_identity` | string | yes | Address or domain |
| `ConfigurationSetName` | string | no | Default set for this identity |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `payload` | object | inbound | SNS envelope (`Type`, `Message`, `SigningCertURL`, …) |

`listen` does **not** call `CreateConfigurationSet` in v1 (operator creates the set + SNS topic + HTTPS subscription). The node verifies SNS signatures and unpacks SES event JSON. Document the operator steps in the node README when built.

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `message_id` | string | send | SES `MessageId` |
| `template` | object | get-template | Name + content |
| `templates` | array | list-templates | Summaries |
| `next_token` | string | list-* | Pagination |
| `identity` | object | create-identity | Dkim attributes / status |
| `event` | object | listen | Normalized `{ event_type, recipients, diagnostic_code, message_id }` |
| `sns_type` | string | listen | `SubscriptionConfirmation` \| `Notification` \| `UnsubscribeConfirmation` |
| `error` | string | failure | Never keys |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/) · [AWS Acceptable Use](https://aws.amazon.com/aup/)
- Sandbox until production access. Sending to unverified addresses in sandbox fails.
- Bounce and complaint handling is required; high complaint rates trigger AWS enforcement. Use `listen` + a configuration set on every production send.
- Message bodies and events may be personal data / PHI. Region choice is a residency decision. This node does not add encryption beyond TLS to AWS.
- CAN-SPAM / CASL still apply to the operator’s content.

## Implementation notes

- **JDK HTTP + JDK crypto only.** Implement SigV4: canonical request, `AWS4-HMAC-SHA256`, credential scope `{date}/{region}/ses/aws4_request`, header `Authorization`. Content-Type `application/json`. Unsigned payload hash of the exact body bytes.
- Host: `email.{region}.amazonaws.com`. Path as in the table. Do not use `ses.{region}.amazonaws.com` for v2 REST.
- Timeouts and 20 MiB-class body caps. Retry 429/`Throttling` and 5xx with jitter; no retry on 400 validation / 403 sandbox.
- SNS `listen`: verify `x-amz-sns-message-type` and signature per [SNS sig verify](https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html) (fetch cert from `SigningCertURL` only if host is `sns.{region}.amazonaws.com`). On `SubscriptionConfirmation`, GET `SubscribeURL` once. Then parse `Message` as SES event JSON (`eventType` bounce/complaint/delivery/…).
- Errors → `{ error }` on OUTPUT. Never throw past dispatch. Redact access keys.

### Example

Send success: `{ "status": "ok", "message_id": "0100…" }`.
