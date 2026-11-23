# Adding a node

This image is a **deck**: one Containerfile, many nodes. A new node is a
definition JSON, a Java `NodeHandler`, a registry entry, and an
`SQ_TEMPLATE_ID_<KEY>` environment variable. Do not add a second image.

Read [architecture.md](architecture.md) first (listener, `templateId`, HTTP 200
contract, helper socket, thread safety).

## 1. Create the folder and definition

Create `nodes/<kebab-name>/node_definition.json`.

`<kebab-name>` is lowercase, hyphenated, stable (`google-trends`, not
`GoogleTrends` or `google_trends_v2`). It must match the `docs/NODE_CATALOG.md`
catalog row and the `SQ_TEMPLATE_ID_<KEY>` suffix style below.

Copy an existing definition and change every id. **Never reuse `_id` values.**

## 2. Schema walkthrough

Top-level fields the designer and daemon care about:

| Field | Role |
| --- | --- |
| `_id` | Template id. This **is** `templateId` on the invoke body. UUID (or a stable `tpl_…` token). Must match `ENV SQ_TEMPLATE_ID_<KEY>`. |
| `version` | Semver or integer string (`1`, `1.0.0`). Bump when ports or behaviour change in a breaking way. |
| `name` | Catalog name (`HTTP Request`). |
| `label` | Designer label (may be longer than `name`). |
| `description` | What the node does, when to use it, and what it does not do. |
| `icon` | Material icon name (`http`, `psychology`, `bug_report`). |
| `tags` | Search keywords. |
| `category` | Designer grouping (Agents, AI Models, Connectivity, …). |
| `ports` | INPUT / OUTPUT (and FUNCTION only when the node is really a hub). |
| `configurationParameters` | Instance-level knobs (API keys, timeouts, defaults). |

### Ports

Each port:

```json
{
  "_id": "unique-port-id",
  "name": "input",
  "type": "INPUT",
  "value": "input",
  "description": "Purpose of this port, what the handler reads, and the shape.",
  "required": true,
  "schema": []
}
```

- `type`: `INPUT` or `OUTPUT`.
- `name` / `value`: the wire key. Output-port **names** are the JSON keys the
  handler must return.
- INPUT `schema[]` lists parameters the upstream node (or the designer) fills.
- OUTPUT `schema[]` lists fields in that output object.

### Every parameter description

Reviews bounce definitions that omit any of these three pieces on **every**
`schema` child and **every** `configurationParameters` entry:

1. **Purpose** — why the field exists and what the handler does with it.
2. **Accepted values** — exact enum, or a clearly bounded type (positive
   integer, `https` URL, secret reference). Say what happens on omit/invalid.
3. **Example** — one realistic example (and a second if there are two common
   modes).

Bad: `"description": "The URL."`

Good:

```text
Absolute request URL. REQUIRED. Empty or missing fails with result='URL is missing'.
Accepted schemes: http and https. Include path, query, and port as needed.
Examples: 'https://httpbin.org/get', 'https://api.example.com/v1/items?limit=10'.
```

Nested `children[]` (objects) follow the same rule on every child.

### configurationParameters

Instance config, not per-call input. Typical entries: API keys (bind a StudioQuack
secret, never a literal in the committed JSON), timeouts, default page sizes,
`verifyTls`.

```json
{
  "_id": "unique-param-id",
  "shortCode": "timeoutSeconds",
  "label": "Timeout (seconds)",
  "type": "integer",
  "required": false,
  "value": 20,
  "description": "Purpose: cap connect+read time for every call. Accepted values: positive integers. Default: 20. Example: 20. Example for a slow API: 45."
}
```

The handler reads these from
`executionContext.currentNode.data.configurationParameters[]` by `shortCode`.
Password-typed values may be resolved secrets — **never log them**.

Set `executionRequirementsDto.ociImageRef` to the library image, exactly as every
other built-in definition does (see `nodes/http-request/node_definition.json`):

```json
"executionRequirementsDto": { "ociImageRef": "sq-local/sq-builtin-node-library:1.0.0" }
```

That ref routes the node to the platform-managed library instance.

## 3. Implement a NodeHandler

Add a Java class under `src/main/java/com/studioquack/nodelibrary/nodes/`, next
to the handlers of the same category (for example `nodes/web/HttpRequestNode`).

The handler:

- is a singleton, **thread-safe**;
- accepts the invoke JSON (or a small DTO parsed from it);
- reads `inputVariables` and configuration parameters;
- performs the work (outbound HTTP, helper LLM call, …);
- returns `Map<String, Object>` keyed by OUTPUT port names;
- never logs `helperToken`, API keys, cookies, or raw payloads that may hold
  secrets.

Use the helper channel (`SQ_HELPER_SOCKET` + `helperToken`) for
`studioquack.execute`, daemon LLM proxy, structured fail, and logs. Do not
open a TCP connection to the host.

Keep the class focused on **one** template. Shared HTTP clients belong in the
category's support class, not copied into every handler.

## 4. Register the handler

On dispatcher startup, `NodeRegistry` must resolve the node’s `_id` to the
handler. Register it in `NodeHandlers.registerAll`, where every built-in is
wired.

Startup should fail loud if `SQ_TEMPLATE_ID_<KEY>` is missing or blank: a
silent default id means two nodes can collide after a re-import.

## 5. Add `SQ_TEMPLATE_ID_<KEY>` to the Containerfile

In the image `Containerfile`, add an `ENV` whose value **equals** the
definition `_id`:

```dockerfile
# Must match nodes/http-request/node_definition.json _id
ENV SQ_TEMPLATE_ID_HTTP_REQUEST=30f59c3b-afa1-463a-9a60-9cda6ccae790
```

Naming:

- prefix `SQ_TEMPLATE_ID_`;
- suffix is the kebab folder name in `SCREAMING_SNAKE_CASE`
  (`ai-agent` → `SQ_TEMPLATE_ID_AI_AGENT`,
  `semantic-scholar` → `SQ_TEMPLATE_ID_SEMANTIC_SCHOLAR`).

The dispatcher binds `System.getenv("SQ_TEMPLATE_ID_HTTP_REQUEST")` (etc.) so
an operator can override an id at run time without rebuilding the handler
classes. Do not hard-code UUIDs in Java when an ENV key exists.

Also keep `SQ_APP_PORT`, `SQ_APP_PATH`, `SQ_STATEFUL_RUNNER`, and
`LABEL studioquack.sqs.dispatch=1` as documented in
[architecture.md](architecture.md).

## 6. Catalog, ignore rules, and secrets

- Add the node to [NODE_CATALOG.md](NODE_CATALOG.md) and its category page
  under [catalog/](catalog/).
- Do not commit API keys, session cookies, or production tokens. Use
  placeholders and `$secretRef`.
- Do not log helper tokens. See [SECURITY.md](../SECURITY.md).

## 7. Test

Run `mvn -q test`. `NodeCatalogTest` checks that every package under `nodes/`
loads with a UUID `_id` that round-trips through its `SQ_TEMPLATE_ID_<KEY>`
name, and `NodeContractAuditTest` audits every definition's parameters,
defaults, API spec and authentication. Add a unit test for the handler next to the existing ones under
`src/test/java/com/studioquack/nodelibrary/nodes/`.

Assert:

- the returned map's keys match the definition's OUTPUT port names;
- missing required input fails with a typed `StudioQuackError`, not an
  unchecked exception;
- secrets never appear in log output or returned values.

## Checklist

- [ ] `nodes/<kebab-name>/node_definition.json` with unique `_id`
- [ ] Every parameter `description` has purpose + accepted values + example
- [ ] `NodeHandler` implemented and thread-safe
- [ ] Registered in `NodeHandlers.registerAll`
- [ ] `executionRequirementsDto.ociImageRef` set to the library image
- [ ] `ENV SQ_TEMPLATE_ID_<KEY>=<_id>` in `Containerfile`
- [ ] Row in `docs/NODE_CATALOG.md` and the category page under `docs/catalog/`
- [ ] Handler unit test (happy path + one invalid-input path); `mvn -q test` passes
- [ ] No secrets in git or logs
