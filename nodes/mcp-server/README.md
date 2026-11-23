# MCP Server

- **Status:** done
- **Catalog:** `docs/catalog/ai-ml.md`
- **Folder:** `nodes/mcp-server/`
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.McpServerNode`
- **Protocol:** Model Context Protocol, Streamable HTTP ([2025-06-18](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports))
- **Auth:** `none` | `bearer` | `header` — token is password-typed config only (`auth_token`)

## Scope

One call against a remote MCP server. The node is a client. It does not listen for inbound MCP clients, and it does not spawn a local process (stdio). A server on the same machine must expose Streamable HTTP.

Each invoke POSTs `initialize`, then `notifications/initialized`, then one method, then `DELETE`s the session when the server returned `Mcp-Session-Id`. The method response may be `application/json` or one `text/event-stream` body on that same POST. Redirects are not followed, so the auth header cannot leak to another host. The response body is capped at 1 MiB.

`timeout_seconds` is the budget for the whole invoke (default 30, cap 60). `verify_tls=false` is lab-only.

A JSON-RPC or HTTP failure raises a BPMN error. A tool result whose `isError` is true is still `status=ok`: the RPC succeeded and the tool reported a domain error in `data`.

## Operations (`operation` config / input values)

| operation | JSON-RPC method | When to use |
|---|---|---|
| `list_tools` | `tools/list` | Default. Optional `cursor`. |
| `call_tool` | `tools/call` | `tool_name` plus `arguments` object. |
| `list_resources` | `resources/list` | Optional `cursor`. |
| `read_resource` | `resources/read` | `uri` (sent to the server; not fetched here). |
| `list_resource_templates` | `resources/templates/list` | Optional `cursor`. |
| `list_prompts` | `prompts/list` | Optional `cursor`. |
| `get_prompt` | `prompts/get` | `prompt_name` plus optional `prompt_arguments`. |
| `ping` | `ping` | Liveness. |

Input `operation` and `server_url` win over config.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `server_url` | string | yes | | no | http(s) endpoint. No userinfo. |
| `operation` | select | no | list_tools | no | Default method |
| `auth_type` | select | no | none | no | `none`, `bearer`, or `header` |
| `auth_token` | password | when auth is set | | yes | Bearer token or header value |
| `auth_header` | string | no | X-Api-Key | no | Header name for `auth_type=header` |
| `protocol_version` | select | no | 2025-06-18 | no | Offered version. Server may negotiate within the allow-list. |
| `timeout_seconds` | number | no | 30 | no | Whole-invoke budget, cap 60 |
| `verify_tls` | select | no | true | no | `false` disables certificate checks (lab only) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `operation`, `server_url`, `tool_name`, `arguments`, `uri`, `prompt_name`, `prompt_arguments`, `cursor` | No auth token on this port |
| `output` | OUTPUT | `{ status, data, http_status }` | `data` is the JSON-RPC result |

FUNCTION ports: none.

## Legal

- The customer owns the MCP server and the decision to send tool arguments there.
- `auth_token` is never logged or copied onto the input port. Error text is redacted.
- Egress stays under the daemon network policy. This node does not add a path around it.
- Disabling TLS verification is lab-only.
