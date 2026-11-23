# How a planned catalog row becomes a library node

Every planned provider is added under `nodes/<kebab-slug>/` in this repo. The
same OCI image serves all of them: dispatch is by `templateId` (the definition
`_id`, overridable with `SQ_TEMPLATE_ID_<KEY>`).

Do **not** write a new Java class per provider. The spec-driven
`OfficialApiNode` loads `api_spec.json` and performs the HTTP call.

**Non-HTTP nodes are the exception.** Local transforms (sort, json, text, …)
live as real handlers in `nodes/transform/`; local codec/security/file nodes
(jwt, totp, compression, files, …) in `nodes/codec/`. They have **no**
`api_spec.json` — `OfficialApiHandlers` skips folders without one. Protocol
nodes (SMTP, FTP, SSH, LDAP, git) stay `planned` until a protocol handler
exists. `baseUrl` may template config values: `https://{dc}.api.example.com`
or fully `{endpoint}` (graphql).

## Files (all three required)

```
nodes/<slug>/
  node_definition.json   # designer contract (ports, params, UUID _id)
  api_spec.json          # machine-readable official HTTP surface (HTTP nodes only)
  README.md              # operator docs (params, legal, official docs URL)
```

Skip: shipped (`done`) folders, BPMN-overlap names, **Webhook** /
**Respond to Webhook** (delivered through the StudioQuack engine's message-event
ingress onto BPMN message events), and any `DO NOT BUILD` row.

## `api_spec.json`

```json
{
  "baseUrl": "https://api.example.com/v1",
  "defaultTimeout": 25,
  "timeoutConfigKey": "timeout_seconds",
  "auth": {
    "type": "bearer",
    "header": "Authorization",
    "prefix": "Bearer ",
    "configKey": "api_key"
  },
  "operations": {
    "list": {
      "kind": "http",
      "method": "GET",
      "path": "/items",
      "queryFrom": ["limit", "cursor"],
      "required": []
    },
    "get": {
      "method": "GET",
      "path": "/items/{id}",
      "required": ["id"]
    },
    "create": {
      "method": "POST",
      "path": "/items",
      "bodyFrom": ["name", "email"],
      "required": ["name"]
    },
    "trigger": { "kind": "ingress" }
  }
}
```

`auth.type`: `none` | `bearer` | `header` | `basic` | `query`.
`kind: ingress` (or operation name `listen` / `trigger` / `webhook`) does **not**
open a socket — the handler tells the operator to use a BPMN message event.

Field resolution: every path placeholder, `queryFrom`, `bodyFrom`, `required`
and `extraHeaders` placeholder reads the INPUT port first; a blank or absent
input falls back to the config parameter with the same `shortCode` (typed by
its declared `type`). So each such field must be declared on the INPUT schema,
as a config parameter, or both. The operation is INPUT `operation`, else config
`operation`; INPUT `action` is an alias only when it names a real operation.
A node with a single operation needs no `operation` field at all.

Wire mapping (per operation, all optional):

- `bodyMap`: `{"<input key>": "<wire location>"}` — a dotted path in a JSON body
  (`"temperature": "generationConfig.temperature"`) or the literal field / part name in a
  form or multipart body (`"order_id": "metadata[order_id]"`). Lets the INPUT schema use
  purpose-oriented names while the provider gets its own shape. JSON paths may index
  arrays (`"to": "personalizations[0].to[0].email"`, index ≤ 99). Target `"$"` makes the
  input the whole body: an object is merged into the root (named fields win), an array is
  sent as the body as-is.
- `queryMap`: `{"<input key>": "<query name>"}` (`"page_size": "$top"`).
- `mime`: `{"target": "raw", "encoding": "base64url" | "base64" | "none"}` builds an
  RFC 5322 message from INPUT `from, to, cc, bcc, reply_to, subject, text, html,
  in_reply_to, references, attachments` (file blocks) and sends it in body field `target`
  (Gmail `raw`). A non-blank `target` on the input is sent as-is.

Errors: a node never puts `{error}` on its output port — that counts as success. Every
failure raises a BPMN error for the workflow's error boundary event: `INVALID_INPUT`
(validation before any request), `AUTH_FAILED` (401/403), `RATE_LIMITED` (429),
`HTTP_<status>` (other provider statuses, with data `{http_status}`), `REQUEST_FAILED`
(transport). Messages never contain credentials. OUTPUT schemas therefore carry no
`error` field.

## Checks

`NodeContractAuditTest` (definition ↔ spec) and `NodeBehaviourAuditTest` (every
operation against a loopback server) run in `mvn test` and write
`target/node-audit/report.md` / `behaviour.md`. `-Dnodeaudit.strict=true` fails
on any ERROR in any node.

`NodeLiveSmokeTest` is opt-in (`-Dnodeaudit.live=true`) and calls the real provider
for read-only `GET` operations only, writing `target/node-audit/live.md`. Sample
inputs for public APIs live in `src/test/resources/node-live-samples.json`;
credentials and account-specific inputs go in `~/.studioquack/node-live.json`
(never in the repo), same shape: `{"<node>": {"config": {…}, "input": {"<op>": {…}}}}`.

## `node_definition.json` rules

- Top-level `_id` and every port `_id`: RFC-4122 UUID (generate fresh).
- `category` + `tags` from the catalog row.
- One INPUT `input` + one OUTPUT `output` (FUNCTION ports only if the node
  must invoke another node N times — almost never here).
- Config includes `operation` (select of the operations, only when there is more than one) + auth secret
  (`type: password`) + `timeout_seconds` (`type: number`).
- File payloads use `type: file` on ports and config.
- Parameter descriptions: purpose + accepted values (see any shipped node).
- No competitor product names anywhere.

## After adding folders

Add `ENV SQ_TEMPLATE_ID_<KEY>=<_id>` to `Containerfile` (matching the
definition `_id`), update `docs/NODE_CATALOG.md` and the category file under
`docs/catalog/`, and run `mvn -q test` to validate the package (see
[../adding-a-node.md](../adding-a-node.md)).
