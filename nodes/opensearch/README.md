# OpenSearch

Handler: `OfficialApiNode` via `api_spec.json`.

This node signs **Amazon OpenSearch Service managed domains** with AWS SigV4. The spec can express only one `auth.type`, so HTTP Basic was removed. Self-hosted OpenSearch (username/password or internal TLS) should use the generic HTTP Request node.

- **Managed-domain signing service:** `es` ([Making and signing OpenSearch Service requests](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/managedomains-signing-service-requests.html))
- **OpenSearch Serverless:** SigV4 service is `aoss` (not `aos`). This node is pinned to `es`. For Serverless collections use the generic AWS node with `service=aoss`, or HTTP Request. Docs: [Signing HTTP requests (Serverless)](https://docs.aws.amazon.com/opensearch-service/latest/developerguide/serverless-clients.html)
- **REST API:** https://docs.opensearch.org/latest/api-reference/
- **Verified:** 2026-08-22

`baseUrl` is `https://{endpoint}`. Set `endpoint` to the domain hostname without scheme, e.g. `search-mydomain-abc.us-east-1.es.amazonaws.com`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `document.create` | PUT | `/{index}/_doc/{id}` |
| `document.get` | GET | `/{index}/_doc/{id}` |
| `document.update` | POST | `/{index}/_update/{id}` |
| `document.delete` | DELETE | `/{index}/_doc/{id}` |
| `document.search` | POST | `/{index}/_search` |

## Config

| Name | Type | Secret | Purpose |
|---|---|---|---|
| `operation` | select | no | Default verb |
| `access_key_id` | password | yes | IAM access key ID (SigV4 credential) |
| `secret_access_key` | password | yes | IAM secret access key (SigV4 signing key) |
| `session_token` | password | yes | Optional STS session token (`x-amz-security-token`) |
| `region` | string | no | SigV4 signing region of the domain |
| `endpoint` | string | no | Domain hostname (no scheme) |
| `timeout_seconds` | number | no | Default 25 |

## Ports

`input` / `output` only. Failures: `{ error }` — never includes keys.

## Legal

- Customer is the controller of data they query or write.
- Minimize: send only the documents / ids required for the step.
- Provider terms apply on the caller account.
- Official HTTP API only.
