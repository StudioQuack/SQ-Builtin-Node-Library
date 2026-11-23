# Email

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/email/`
- **Researched:** 2026-08-20
- **Official API:** SMTP RFC 5321, IMAP4rev1 RFC 3501 / IMAP4rev2 RFC 9051, RFC 8314 TLS
- **Docs:** https://www.rfc-editor.org/rfc/rfc5321
- **Auth:** password / app password — secret binding: `password`
- **Handler:** `EmailNode` (`com.studioquack.nodelibrary.nodes.protocol.EmailNode`)

## Scope

Official SMTP + IMAP only. `send` submits a message. `search` / `get` / `move` / `mark` talk IMAP. `listen_imap` does not open a mailbox — use a BPMN message start / catch / boundary event. `send_and_wait` is not an operation. No webmail scrape.

Attachments use the platform `file` type (ContentBlock with name / mime_type / source / data, or a sandbox path under `/sandbox/files`). They are resolved the same way as other file-typed ports (`FilesSupport.readFileValue`). `get` returns attachment ContentBlocks on `data.attachments` and the first one on output `file`.

TLS is required by default: SMTPS on port 465, STARTTLS required on other SMTP ports, IMAPS for mailbox ops. Set `tls=false` only for lab plain sockets. Passwords are never logged or echoed.

## Operations (`operation` config values)

- `send` — SMTP submit (RFC 5321)
- `listen_imap` — ingress; do not open IMAP here
- `search` — IMAP UID SEARCH (capped)
- `get` — IMAP UID FETCH
- `move` — IMAP UID MOVE
- `mark` — IMAP UID STORE (`seen` / `flagged` / `deleted`)

## Configuration parameters

- `operation` (select, required, default send) — verb
- `smtp_host` (string) — required for send
- `smtp_port` (number, default 587)
- `imap_host` (string) — required for mailbox ops
- `imap_port` (number, default 993)
- `user` (string) — mailbox username
- `password` (password, secret) — app password
- `tls` (select, default true) — RFC 8314; `false` is lab plain only
- `from` (string) — default From
- `timeout_seconds` (number, default 25) — connect+read budget

## Ports

- INPUT `input`: to, cc, bcc, from, subject, text, html, attachments, folder, uid, query, target_folder, flag. Input wins over config. Failures never include secrets.
- OUTPUT `output`: `{ status, data, file? }`. Errors raise a BPMN error (INVALID_INPUT, AUTH_FAILED, REQUEST_FAILED); secrets are redacted.

FUNCTION ports: none.

## Caps

- Recipients: 50
- Body: 200000 characters
- Search results: 100 UIDs
- Attachments: 8, 8 MiB each
- Query: 1024 characters, no control characters

## Legal

- Official SMTP/IMAP. No webmail HTML scrape.
- Prefer app passwords. TLS required in production (RFC 8314).
- Message bodies may be personal data.

## Implementation notes

- In-process Jakarta Mail (Angus Mail). One Session per invoke; `Session.setDebug(false)`.
- `listen_imap` raises INVALID_INPUT pointing at a BPMN message event (same text as OfficialApiNode).
- Timeouts from `timeout_seconds` (default 25, max 60).
