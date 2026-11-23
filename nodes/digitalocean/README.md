# DigitalOcean

Handler: `OfficialApiNode`. Docs: https://docs.digitalocean.com/reference/api/api-reference/#operation/droplets_create

`droplet_create` body: official `name`, `region`, `size`, `image` (slug/id string), `ssh_keys`, `backups`, `ipv6`, `monitoring`, `tags`, `user_data`, `volumes`, `vpc_uuid`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
