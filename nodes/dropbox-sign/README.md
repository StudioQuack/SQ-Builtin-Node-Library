# Dropbox Sign

Handler: `OfficialApiNode`. Docs: https://developers.hellosign.com/api/reference/operation/signatureRequestSend/

- **Auth:** HTTP Basic, API key as username (empty password). Fixed from key:key.
- `signature_request_send` form body: official `signers`, `title`, `subject`, `message`, `file_urls`, `test_mode`, `cc_email_addresses`, `metadata`.
- Multipart local `file[]` uploads are not wired (engine form, not file parts) — use `file_urls`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
