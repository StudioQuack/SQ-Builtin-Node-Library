# Amazon SES


send-email / send-templated-email take `ToAddresses` / `CcAddresses` / `BccAddresses` merged into the official `Destination` envelope via bodyMap. send-templated-email also maps `TemplateName` to `Content.Template.TemplateName`; pass the variable values as the JSON-encoded string `Content.Template.TemplateData`. Custom verification template create/update require `TemplateName`, `FromEmailAddress`, `TemplateSubject`, `TemplateContent` (HTML string), `SuccessRedirectionURL` and `FailureRedirectionURL`. Docs: https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html

In-process StudioQuack builtin (`templateId` `d3f3870a-c90a-5384-a432-ec56565822c2`, override with `SQ_TEMPLATE_ID_AMAZON_SES`). Handler: `OfficialApiNode` + `api_spec.json`.

## Overview

Calls the **official AWS SESv2 REST API** at `https://email.{region}.amazonaws.com`. Auth: AWS SigV4 (service `ses`) using `access_key_id` + `secret_access_key`. Optional `session_token` becomes `x-amz-security-token`.

Provider docs: [SendEmail](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html) · [SESv2 endpoints](https://docs.aws.amazon.com/general/latest/gr/ses.html)

Terms: [https://aws.amazon.com/service-terms/](https://aws.amazon.com/service-terms/)

Sandbox until production access. `listen` = SNS/configuration-set events as BPMN message events.

`jobType` is `custom-script-executor`. Needs outbound HTTPS to the provider host.

## Ports

| Port | Direction | Type | Required | Data flow |
| --- | --- | --- | --- | --- |
| `input` | INPUT | object | yes | `operation` plus per-verb fields. Secrets are not on this port. |
| `output` | OUTPUT | object | yes | `{status,http_status,data}`. |

FUNCTION ports: none.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `send-email` | POST | `/v2/email/outbound-emails` | SESv2 SendEmail |
| `send-templated-email` | POST | `/v2/email/outbound-emails` | Same path; `Content.Template` on INPUT |
| `create-template` | POST | `/v2/email/templates` | CreateEmailTemplate |
| `get-template` | GET | `/v2/email/templates/{TemplateName}` | GetEmailTemplate |
| `list-templates` | GET | `/v2/email/templates` | ListEmailTemplates (`PageSize`, `NextToken`) |
| `update-template` | PUT | `/v2/email/templates/{TemplateName}` | UpdateEmailTemplate |
| `delete-template` | DELETE | `/v2/email/templates/{TemplateName}` | DeleteEmailTemplate |
| `create-custom-verification-template` | POST | `/v2/email/custom-verification-email-templates` | Official HTTP |
| `get-custom-verification-template` | GET | `/v2/email/custom-verification-email-templates/{TemplateName}` | Official HTTP |
| `list-custom-verification-templates` | GET | `/v2/email/custom-verification-email-templates` | `PageSize`, `NextToken` |
| `update-custom-verification-template` | PUT | `/v2/email/custom-verification-email-templates/{TemplateName}` | Official HTTP |
| `delete-custom-verification-template` | DELETE | `/v2/email/custom-verification-email-templates/{TemplateName}` | Official HTTP |
| `create-identity` | POST | `/v2/email/identities` | CreateEmailIdentity |
| `listen` | ingress | — | BPMN message event; OfficialApiNode does not open a socket |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Verb above |
| `access_key_id` | password | yes | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | no | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | yes | no | Host and signing region (`email.{region}.amazonaws.com`) |
| `ConfigurationSetName` | string | no | no | Default SESv2 body `ConfigurationSetName` when INPUT omits it |
| `timeout_seconds` | number | no | no | Default 25 |

## Security & legal

Official API only. Sandbox until production access. Do not scrape unofficial clients. Never log tokens, message bodies, or file bytes. PHI/PII in messages leaves the sandbox toward the provider; the operator is the data controller.

listen / send-and-wait: use a BPMN message start, intermediate catch, or boundary message event. This node is not a Webhook node.
