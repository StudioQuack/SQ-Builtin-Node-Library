# Execute Command

- **Status:** done
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/execute-command/`
- **Researched:** 2026-08-22
- **Official API:** POSIX.1-2024 (Issue 8) exec in the StudioQuack node-library container (platform — not a third-party API)
- **Docs:** https://pubs.opengroup.org/onlinepubs/9799919799/utilities/V3_chap02.html
- **Auth:** none (instance `enabled` switch; default off) — secret binding: `(none)`
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.ExecuteCommandNode` (`SQ_TEMPLATE_ID_EXECUTE_COMMAND`)

## Scope

**Sandbox-only.** The node-library process already runs inside the Podman sandbox; this node never executes on the daemon host. Default off: set configuration `enabled=true`. Executes `argv` via `ProcessBuilder` (no shell) if `argv[0]`'s basename is on `allowlist`. `working_dir` must stay under the workspace (`SQ_EXEC_WORKSPACE`, default `/sandbox/exec`). Empty allowlist is refused.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `exec` | sandbox | ProcessBuilder argv | Allowlisted command |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | exec | no | Which capability to run. EXACT accepted values: exec. Required. Example: 'exec'. |
| `enabled` | select | yes | false | no | Master switch. Default false. The node refuses every invoke until this is true. Input cannot enable it. |
| `allowlist` | string | yes |  | no | JSON array (or comma list) of allowed executable basenames. Required. Empty list refuses. Example: '["echo","true"]'. |
| `argv` | list | no |  | no | Argument vector. argv[0] must be on the allowlist. Example: ["echo","hello"]. INPUT wins. |
| `stdin` | string | no |  | no | UTF-8 bytes written to the child stdin. INPUT wins. |
| `timeout_ms` | number | no | 15000 | no | Wall-clock command budget in ms. Default 15000. Capped at 60000. Wins over timeout_seconds. |
| `working_dir` | string | no |  | no | Default workspace-relative cwd when input.working_dir is blank. Host paths rejected. |
| `timeout_seconds` | number | no | 25 | no | Fallback run budget in seconds when timeout_ms is blank/0. Positive integer. Blank/0/negative → 15s via timeout_ms default. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | `argv` (list, required), `stdin`, `working_dir` | argv is a list — never a shell string |
| `output` | OUTPUT | `{ status, exit_code, stdout, stderr, timed_out }` | Non-zero `exit_code` is `status=ok`. Refusals are INVALID_INPUT; launch failures are REQUEST_FAILED. |

FUNCTION ports: none.

## Legal

- Customer liability for commands they allowlist.
- Never a host escape. Operator must not allowlist privileged binaries or a shell unless they accept that risk.
- Default-off is the consent switch: enabling it is an explicit instance decision.

## Implementation notes

- Resolve `argv[0]` to an absolute path on `PATH=/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin`. Basename must stay on the allowlist.
- Child environment is cleared; only `PATH`, `HOME`, `LANG`, `LC_ALL`, `TMPDIR` are set.
- stdout/stderr capped at 65536 bytes each.
- `sleep` past `timeout_ms` sets `timed_out=true` and kills the child.
- Errors raise a BPMN error; they are not placed on the output port.
