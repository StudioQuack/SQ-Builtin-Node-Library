# SSH

- **Status:** done
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/ssh/`
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.SshNode` (in-process JSch SSH-2)
- **Official API:** SSH-2 (RFC 4251–4254); RSA SHA-2 signatures RFC 8332
- **Docs:** https://www.rfc-editor.org/rfc/rfc4253 · https://www.rfc-editor.org/rfc/rfc8332
- **Auth:** password | private_key — password-typed config only (`password` / `private_key` / `passphrase`)

## Scope

Opens an SSH-2 session to `host`. `exec` runs one command string on the **remote** host (never a local process). `download` / `upload` move a platform `file` ref over SFTP.

`file` on ports is type `file` (platform ref-id or ContentBlock). Download returns that block on `file` and `outputFiles`. Upload reads `file` from input via the same platform file-ref path as other file nodes (`FilesSupport.readFileValue`).

Host-key verification is mandatory: `known_hosts` (OpenSSH known_hosts content or a SHA256/MD5 fingerprint) is required. Unknown or mismatched keys are refused. There is no trust-on-first-use and `StrictHostKeyChecking=no` is never set. `ssh-rsa` (SHA-1) host keys are not offered; the session accepts Ed25519, ECDSA, and RFC 8332 `rsa-sha2-256` / `rsa-sha2-512`.

## Operations (`operation` config / input values)

| operation | Kind | When to use |
|---|---|---|
| `exec` | SSH-2 exec | Remote command (`command` input). Result `data.stdout` / `data.stderr` / `data.exit_code`. |
| `download` | SSH-2 SFTP | Remote path → platform file |
| `upload` | SSH-2 SFTP | Platform file → remote path |

Input wins over config when both are set (`operation`, `host`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | exec | no | Verb |
| `host` | string | yes | | no | Remote host (input `host` overrides) |
| `port` | number | no | 22 | no | SSH port |
| `user` | string | yes | | no | Username |
| `auth_type` | select | no | key | no | `password` or `key` |
| `password` | password | no | | yes | Password auth |
| `private_key` | password | no | | yes | Key auth PEM |
| `passphrase` | password | no | | yes | Encrypted key |
| `known_hosts` | string | yes | | no | Host-key pin |
| `timeout_seconds` | number | no | 25 | no | Connect+read budget (cap 60) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `operation`, `host`, `command`, `remote_path`, `file` | Secrets never on input |
| `output` | OUTPUT | `{ status, data, file?, outputFiles? }` | Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_\<status\>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Legal

- Official SSH-2. Customer owns the remote host and its access policy.
- Private keys, passwords, and passphrases are never logged or echoed.
- `known_hosts` is required on every invoke (reject unknown hosts).
- `ssh-rsa` SHA-1 signatures are not offered. Prefer RFC 8332 `rsa-sha2-256` / `rsa-sha2-512` or Ed25519.

## Implementation notes

- In-process `SshNode`. Connect/read timeout from `timeout_seconds` (default 25, cap 60). Transfer / exec output caps: 8 MiB files, 2 MiB exec streams.
- Remote path segment `..` is rejected. Local upload bytes resolve through `FilesSupport` (sandbox `/sandbox/files` or ContentBlock).
- Register the handler yourself — this package does not edit `NodeHandlers`.
