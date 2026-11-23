# Ghost

Handler: `OfficialApiNode`. Docs: https://ghost.org/docs/admin-api/

- **Base:** config `base_url` (`https://{site}/ghost/api/admin`).
- **Auth:** Ghost Admin API key must be minted into a JWT outside StudioQuack (HMAC of `id:secret`). Put the JWT in `admin_api_key` if the engine sends it as a Bearer/header — this node does not mint JWTs.
- `post_create` / `post_update` take `title`/`html`/`status`/`tags` nested into `posts[0]`. Set query `source=html` when sending `html`. `posts` remains a one-object escape hatch.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
