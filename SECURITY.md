# Security Policy

## Supported versions

This library ships as a single OCI image. Security fixes land on the current
mainline and are published as a new image tag. Older tags are not patched in
place — rebuild and redeploy.

| Version | Supported |
| --- | --- |
| `main` / latest image tag | Yes |
| Previous tagged releases | Best-effort: upgrade to the latest tag |
| Forks and modified images | No — you must apply fixes yourself |

## Reporting a vulnerability

Do **not** open a public GitHub issue for a security report.

Email **security@studioquack.com** with:

- a description of the issue and the affected image tag or commit;
- steps to reproduce, or a proof of impact that does not include live secrets;
- the impact you expect (sandbox escape, secret leak, cross-tenant access, DoS).

We will acknowledge receipt within **3 business days** and send a status update
at least every **7 days** until the issue is resolved or declined.

## Disclosure policy

StudioQuack follows coordinated disclosure:

1. We confirm the report and assign a tracking id.
2. We reproduce, assess severity, and prepare a fix.
3. We publish a patched image tag and a security advisory when the fix is
   available (or when we determine the report is not a vulnerability).
4. We credit reporters who want to be named, unless they prefer to remain
   anonymous.

Please do not disclose the issue publicly until we have released a fix or have
agreed a disclosure date with you. We aim to ship a fix within **90 days** of a
confirmed report; critical sandbox-escape or secret-exfiltration issues are
treated as urgent.

## Safe harbor for security research

StudioQuack supports good-faith security research on this repository and will
not pursue legal action against researchers who:

- test only against their own local builds or deployments (never against
  third-party hosts or production environments they do not own);
- avoid accessing, modifying, or exfiltrating third-party data;
- avoid denial-of-service testing, resource exhaustion, or social engineering;
- report findings privately to **security@studioquack.com** and give us
  reasonable time to remediate before public disclosure.

## Secrets, tokens, and logs

Node handlers talk to the StudioQuack daemon over the helper channel
(`SQ_HELPER_SOCKET`) using a **per-request** `helperToken` (also injected as
`SQVM_HELPER_TOKEN` / `SQ_HELPER_BEARER`). Those values, together with any API
keys, OAuth tokens, passwords, session cookies, or other credentials that a
node receives from configuration or secrets, **must never be logged**.

Rules for this repository:

- Log request ids, template ids, HTTP status codes, and bounded error classes —
  not payloads that may contain PHI, tokens, or file bytes.
- Do not print environment variables, the invoke JSON body, or helper-channel
  frames at INFO/DEBUG in production paths.
- Redact `Authorization`, `Cookie`, `helperToken`, `apiKey`, `api_key`,
  `token`, `password`, and similar keys if a payload must be summarized.
- A leaked helper token is scoped to one request, but logging it still violates
  this policy and may expose customer data in daemon or aggregator logs.

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to send a patch after a fix is
agreed.
