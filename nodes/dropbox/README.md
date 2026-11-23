# Dropbox

Handler: `OfficialApiNode`. Docs: https://www.dropbox.com/developers/documentation/http/documentation#files-upload

RPC calls use `https://api.dropboxapi.com/2`. `upload` / `download` use `https://content.dropboxapi.com/2` with header `Dropbox-API-Arg: {"path":"{path}"}`. Upload body is INPUT `file` (octet-stream). Extra upload modes (overwrite, mute) need a richer Arg JSON — not built by this engine (path only).

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
