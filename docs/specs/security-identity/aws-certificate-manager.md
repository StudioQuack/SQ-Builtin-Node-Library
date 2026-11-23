# AWS Certificate Manager

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/aws-certificate-manager/`
- **Researched:** 2026-08-20
- **Official API:** AWS Certificate Manager API version **2015-12-08** (`acm`)
- **Docs:** https://docs.aws.amazon.com/acm/latest/APIReference/Welcome.html
- **Auth:** `awsAccessKey` or `iamRole` — secret bindings: `accessKeyId`, `secretAccessKey`, `sessionToken` (optional), `externalId` (optional). Config: `region`.

## Scope

Read, renew (eligible certs), list, describe, and delete **ACM-managed certificates** in one Region. Private keys **never leave ACM** on these actions. `GetCertificate` returns PEM **certificate** and **chain** strings only — not a private key. Type those fields as `string`, not `file` (ACM documents them as strings with a PEM pattern).

**DO NOT BUILD:** `RequestCertificate` (issue/request via console or IaC), `ImportCertificate`, `ExportCertificate` (export returns the private key — forbidden on OUTPUT), `RevokeCertificate`, `ResendValidationEmail`, tag APIs, account-configuration APIs, ACME endpoint APIs.

## Wire protocol

Official samples (e.g. [GetCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_GetCertificate.html)):

- URL: `https://acm.{region}.amazonaws.com/` (path `/`)
- Method: `POST`
- `Content-Type: application/x-amz-json-1.1`
- `X-Amz-Target: CertificateManager.{ActionName}`
- Body: JSON
- Auth: [Signature Version 4](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html) — service `acm`, signing region = config `region`
- HTTPS required ([Welcome](https://docs.aws.amazon.com/acm/latest/APIReference/Welcome.html))

This is AWS JSON 1.1 (target header), not IAM Query `Action=` form. JDK HTTP only — **no AWS SDK**.

`iamRole`: STS Query `AssumeRole` (`Action=AssumeRole&Version=2011-06-15`) on `https://sts.{region}.amazonaws.com/`, then sign ACM with temporary credentials. Never emit those credentials.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `get-certificate` | POST | `X-Amz-Target: CertificateManager.GetCertificate` | PEM cert + chain (no private key) |
| `list-certificates` | POST | `…ListCertificates` | Page ARNs / summaries |
| `get-metadata` | POST | `…DescribeCertificate` | CertificateDetail metadata |
| `renew-certificate` | POST | `…RenewCertificate` | Renew an **eligible** ACM certificate |
| `delete-certificate` | POST | `…DeleteCertificate` | Delete cert + ACM-held key (not in use) |

Action docs: [GetCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_GetCertificate.html), [ListCertificates](https://docs.aws.amazon.com/acm/latest/APIReference/API_ListCertificates.html), [DescribeCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_DescribeCertificate.html), [RenewCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_RenewCertificate.html), [DeleteCertificate](https://docs.aws.amazon.com/acm/latest/APIReference/API_DeleteCertificate.html).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `get-certificate` | no | Row above |
| `auth` | select | yes | `awsAccessKey` | no | `awsAccessKey` or `iamRole` |
| `accessKeyId` | password | if `awsAccessKey` | | yes | Access key id |
| `secretAccessKey` | password | if `awsAccessKey` | | yes | Secret access key |
| `sessionToken` | password | no | | yes | STS session token |
| `roleArn` | string | if `iamRole` | | no | Role to assume |
| `roleSessionName` | string | no | `studioquack-acm` | no | STS session name |
| `externalId` | password | no | | yes | STS ExternalId |
| `region` | string | yes | | no | ACM Region (certificates are regional) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No credentials on the edge |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | PEM cert/chain are public material; **never** a private key |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = get-certificate` → `GetCertificate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `CertificateArn` | string | yes | `arn:aws:acm:region:account:certificate/uuid` (20–2048) |

### `operation = list-certificates` → `ListCertificates`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `CertificateStatuses` | array of string | no | `PENDING_VALIDATION` \| `ISSUED` \| `INACTIVE` \| `EXPIRED` \| `VALIDATION_TIMED_OUT` \| `REVOKED` \| `FAILED` |
| `CertificateKeyPairOrigins` | array of string | no | `AWS_MANAGED` \| `ACME` \| `CUSTOMER_PROVIDED` (1–3). Default list omits ACME unless `ACME` is included |
| `Includes` | object | no | ACM `Filters` ([API_Filters](https://docs.aws.amazon.com/acm/latest/APIReference/API_Filters.html)): `keyTypes` (`RSA_1024` \| `RSA_2048` \| `RSA_3072` \| `RSA_4096` \| `EC_prime256v1` \| `EC_secp384r1` \| `EC_secp521r1`; default list is RSA 1024/2048 with a domain — pass the full set to see others), `keyUsage`, `extendedKeyUsage`, `exportOption` (`ENABLED` \| `DISABLED`), `managedBy` (`CLOUDFRONT`) |
| `MaxItems` | number | no | 1–1000 |
| `NextToken` | string | no | Pagination |
| `SortBy` | string | no | Only `CREATED_AT` (requires `SortOrder`) |
| `SortOrder` | string | no | `ASCENDING` \| `DESCENDING` (requires `SortBy`) |

### `operation = get-metadata` → `DescribeCertificate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `CertificateArn` | string | yes | Certificate ARN |

Just-created requests can lag several seconds before Describe succeeds.

### `operation = renew-certificate` → `RenewCertificate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `CertificateArn` | string | yes | Eligible certificate ARN |

Managed renewal / Private CA permission: [managed renewal](https://docs.aws.amazon.com/acm/latest/userguide/managed-renewal.html). Ineligible certs → `{ error }` (`ValidationException` / similar).

### `operation = delete-certificate` → `DeleteCertificate`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `CertificateArn` | string | yes | Certificate ARN |

Cannot delete a cert in use (`ResourceInUseException`) or ACME-origin certs (ACM deletes those one year after expiry). Deletion is eventually consistent with List/Get.

## Output fields (by operation)

### `get-certificate`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Certificate` | string | success | Leaf PEM (`-----BEGIN CERTIFICATE-----` …). Max 32768. **Not** `file` |
| `CertificateChain` | string | success | Issuer/intermediate PEMs. Max 2097152. **Not** `file` |

There is no private-key field. If a future ACM response ever includes one, drop it and fail closed.

### `list-certificates`

| Field | Type | When | Purpose |
|---|---|---|---|
| `CertificateSummaryList` | array | success | `CertificateSummary`: `CertificateArn`, `DomainName`, `Status`, `Type`, `KeyAlgorithm`, `NotBefore`, `NotAfter`, `CreatedAt`, `IssuedAt`, `ImportedAt`, `RevokedAt`, `RenewalEligibility`, `InUse`, `Exported`, `ExportOption`, `ExtendedKeyUsages`, `KeyUsages`, `SubjectAlternativeNameSummaries`, `HasAdditionalSubjectAlternativeNames`, `ManagedBy`, `CertificateKeyPairOrigin` |
| `NextToken` | string | more pages | Pass back as input |

### `get-metadata`

| Field | Type | When | Purpose |
|---|---|---|---|
| `Certificate` | object | success | `CertificateDetail`: `CertificateArn`, `DomainName`, `Subject`, `SubjectAlternativeNames`, `Serial`, `Status`, `Type`, `Issuer`, `SignatureAlgorithm`, `KeyAlgorithm`, `CreatedAt`, `IssuedAt`, `ImportedAt`, `NotBefore`, `NotAfter`, `RevokedAt`, `RevocationReason`, `FailureReason`, `InUseBy`, `KeyUsages`, `ExtendedKeyUsages`, `DomainValidationOptions`, `Options`, `RenewalEligibility`, `RenewalSummary`, `CertificateAuthorityArn`, `CertificateKeyPairOrigin`, `ManagedBy`, `AcmeAccountId`, `AcmeEndpointArn`, `UpdateSummary` |

No PEM and no private key on this action.

### `renew-certificate` / `delete-certificate`

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | number | HTTP 200 empty body | Success |

## Legal

- [AWS Customer Agreement](https://aws.amazon.com/agreement/)
- [AWS Service Terms](https://aws.amazon.com/service-terms/)
- [AWS Acceptable Use Policy](https://aws.amazon.com/aup/)
- Certificates bind domain names (may be personal or tenant identifiers). Treat ARNs, SANs, and validation emails as sensitive metadata. Private keys stay in ACM; this node must not export them.
- **Rate limits** per account per Region ([ACM quotas](https://docs.aws.amazon.com/acm/latest/userguide/acm-limits.html)): `GetCertificate` 10 rps, `ListCertificates` 8 rps, `DescribeCertificate` 10 rps, `RenewCertificate` 5 rps, `DeleteCertificate` 10 rps. Throttle → `ThrottlingException`. Resource caps include 2,500 ACM certificates (expired/revoked still count; Private CA–signed and ACME-issued do not count toward that total).
- Official-API-first.

## Implementation notes

- JDK HTTP + SigV4. No AWS SDK.
- Connect 10 s; read 30 s; cap body at **3 MiB** (`CertificateChain` max 2,097,152 chars).
- Retry 429 / `ThrottlingException` / 5xx max 3 with jitter. Do not retry `ResourceInUseException`, `ResourceNotFoundException`, `ValidationException`, `InvalidArnException`.
- Error mapping: 4xx/5xx → `{ error: { httpStatus, code, message, requestId } }`. Typical: `InvalidArnException` 400, `ResourceNotFoundException` 400, `RequestInProgressException` 400, `ValidationException` 400, `ResourceInUseException` 400, `ConflictException` 400, `AccessDeniedException` 400, `ThrottlingException` 400.
- Log ARN, status, PEM **lengths** — not PEM bodies (optional; certs are public but noisy). Never log keys.
- UNVERIFIED APIs: none of the five catalog-mapped actions. Catalog `get-metadata` → `DescribeCertificate`. Catalog does not include `request-certificate`; keep it **DO NOT BUILD**.
