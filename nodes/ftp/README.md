# FTP

- **Status:** done
- **Catalog:** `docs/catalog/utilities-core.md` · `docs/catalog/files-documents.md`
- **Folder:** `nodes/ftp/`
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.FtpNode` (SFTP via JSch; FTP/FTPS via JDK sockets)
- **Official API:** FTP (RFC 959), explicit FTPS (RFC 4217 AUTH TLS + PROT P), SFTP (SSH File Transfer Protocol)
- **Docs:** https://www.rfc-editor.org/rfc/rfc959 · https://www.rfc-editor.org/rfc/rfc4217
- **Auth:** password | key (SFTP) — password-typed config only (`password` / `private_key` / `passphrase`)

## Scope

Talks to a customer FTP, explicit-FTPS, or SFTP server. **Prefer SFTP.** Cleartext FTP is refused unless `allow_plain_ftp=true`. Explicit FTPS wraps the control channel after `AUTH TLS`, then `PBSZ 0` / `PROT P` (data channel TLS) with JDK certificate + hostname verification.

`file` on ports is type `file`. Download returns the ContentBlock on `file` and `outputFiles`. Upload reads a platform file ref from input (`FilesSupport.readFileValue`).

SFTP requires `known_hosts` (content or fingerprint). Unknown or mismatched host keys are refused (no TOFU).

## Operations (`operation` config / input values)

| operation | Kind | When to use |
|---|---|---|
| `list` | FTP LIST / SFTP readdir | Directory listing in `data.entries` |
| `download` | RETR / SFTP get | → platform file |
| `upload` | STOR / SFTP put | platform file → |
| `delete` | DELE / SFTP rm | Remove |
| `rename` | RNFR/RNTO / SFTP rename | Rename (`new_path`) |

Input wins over config when both are set (`operation`, `host`).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | list | no | Verb |
| `protocol` | select | no | sftp | no | `sftp` \| `ftps` \| `ftp` |
| `host` | string | yes | | no | Server (input `host` overrides) |
| `port` | number | no | 22 | no | Default 22 SFTP / 21 FTP and FTPS |
| `user` | string | yes | | no | Username |
| `password` | password | no | | yes | Password |
| `private_key` | password | no | | yes | SFTP key PEM |
| `passphrase` | password | no | | yes | Encrypted SFTP key |
| `known_hosts` | string | SFTP | | no | SFTP host-key pin |
| `allow_plain_ftp` | boolean | no | false | no | Permit cleartext `protocol=ftp` |
| `passive` | select | no | true | no | Passive only (PORT is rejected) |
| `timeout_seconds` | number | no | 25 | no | Connect+read budget (cap 60) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `operation`, `host`, `remote_path`, `new_path`, `file` | Secrets never on input |
| `output` | OUTPUT | `{ status, data, file?, outputFiles? }` | Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_\<status\>, REQUEST_FAILED); secrets are redacted. |

FUNCTION ports: none.

## Legal

- Official FTP / FTPS / SFTP. Customer owns the server and its access policy.
- Prefer SFTP. Cleartext FTP is not for production and is opt-in (`allow_plain_ftp`).
- Credentials are never logged or echoed. Do not scrape FTP directory HTML.

## Implementation notes

- In-process `FtpNode`. Passive mode only; the data connection uses the control-channel address (PASV advertised IP is ignored).
- Remote path segment `..` is rejected. Transfers capped at 8 MiB. Listings capped at 1000 entries.
- Register the handler yourself — this package does not edit `NodeHandlers`.
