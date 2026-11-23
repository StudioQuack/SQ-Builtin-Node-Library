# Node Test Emulator

## Overview

Stateful BYO test harness (`templateId` `17dcc655-08e2-4b45-b3a0-08d5aa608aed`). Runs **python** / **javascript** / **typescript** scripts against a generated mock `studioquack` helper, and has the SQS daemon **build+run JAVA** Build-tab apps. Point a stateful instance at the `sq-local/sq-builtin-node-library` image.

`jobType` is `custom-script-executor` because the platform has no dedicated jobType for custom-image stateful listeners — execution is the image entrypoint (`NodeLibraryApp`), not a Script tab. `NodeLibraryApp` treats this templateId as the dispatcher self-route; other library ids bind as loopback clients.

This node does **not** call real sub-workflows. FUNCTION ports of the code under test are mocked via `mock_function_responses`. The emulator never mounts a host engine socket.

Handler: `TestEmulatorNode` → `ScriptTestRunner` / `BuildTestRunner`.

## Ports

| Port | Direction | Type | Required | Schema | How the node uses incoming data |
| --- | --- | --- | --- | --- | --- |
| `input` | INPUT | `input` | **yes** | One test object (fields below) | Parsed from `inputVariables.input` or `currentNode.inputList[]` with `targetPortValue=input`. A list-wrapped value is unwrapped to the first element. Illegal language/mode/script/files → OUTPUT `error` (emulator did not start). |
| `output` | OUTPUT | `output` | yes | Test report object | Always present, including failures. Branch on `test_passed`. |
| `error` | OUTPUT | `error` | no | **Bare string** (not wrapped) | Empty `""` after a completed test (pass **or** fail). Non-empty only when the emulator could not start the test. |

### INPUT `input` fields

| Field | Type | Required | Default | Accepted values | How it is used |
| --- | --- | --- | --- | --- | --- |
| `language` | string | **yes** | — | `python` \| `javascript` \| `typescript` \| `java` (case-insensitive, stored lower-case) | Runtime. Must also appear in instance `allowed_languages`. Other strings fail before the test starts. |
| `mode` | string | **yes** (inferred if omitted) | inferred: java→`build`, else `script` | `script` \| `build` | `python`/`javascript`/`typescript` **must** be `script`. `java` **must** be `build`. Mismatch → emulator error. |
| `script` | string | **yes** when `mode=script` | — | Source text | Written to `script.py` / `script.js` / `script.ts` after a wrapper that puts `studioquack` in scope. Ignored in build mode. Must call `returnResult`. No CLI/`__main__`. |
| `files` | list `{path, content}` | **yes** when `mode=build` | — | Relative POSIX paths; one root `Containerfile` or `Dockerfile` | Staged by the daemon. `path` rejected if empty, absolute, contains `..`, or `\`. `content` is file bytes as UTF-8 text, not a host path. |
| `input_data` | object | no | `{}` | Key = INPUT port `value` of the node under test | Mock `getInputValuesByPortValue`. Each value is wrapped as a one-element list unless it is already a list. Build-mode: becomes `inputVariables` / `inputList[].targetPortValue`. |
| `configuration` | object | no | `{}` | shortCode → value | Overrides **both** the node-under-test params and this emulator’s instance params (`default_timeout_seconds`, `max_log_chars`, `verbose`, `allowed_languages`). Mock `getParameterValueByShortCode` returns this first. |
| `mock_function_responses` | object | no | `{}` | Key = FUNCTION port `value`; value = what `execute()` returns | Unmocked `execute` logs `Emulator Warning: No mock response for function port '<name>'` on stderr, appends `{port_value, payload, mocked:false}` to `invocation_log`, returns `{}`. |
| `expected_output` | object / null | no | no assertion when null/blank | Deep-compared to actual port→value map | `null` / omitted / blank string → `output_matches=true` if the process succeeded. JSON string defaults are parsed. |
| `timeout_seconds` | number | no | instance `default_timeout_seconds` (60) when omitted or ≤ 0 | Positive int | Wall clock for the script subprocess, or daemon build+run+invoke. Do not exceed the daemon stateful request budget (default 300s). |

### OUTPUT `output` report

| Field | Type | When | Meaning |
| --- | --- | --- | --- |
| `test_passed` | boolean | always | `script_success && output_matches && error is blank` |
| `script_success` | boolean | always | Process completed (exit 0 / `output.json` written, or image built and produced a body/markers). Independent of compare. |
| `output` | object / null | always | Actual port→value. Script: `returnResult` map. Build: invoke JSON or stdout markers. `null` if nothing produced. |
| `output_matches` | boolean | always | `true` when expected is null or deep-compare found zero diffs. |
| `differences` | string[] | always | Paths like `Value mismatch at root.sum: expected 8, got 7`. Empty when matched. |
| `invocation_log` | list | always | Mock helper calls `{port_value, payload, mocked}` plus build-mode `{kind:result\|log\|fail, …}`. |
| `stdout` / `stderr` | string | always | Truncated to `max_log_chars`. |
| `error` | string | always | In-report failure (`timed out after 60s`, `image build failed`, missing `returnResult`). Empty on a clean run (compare mismatches do **not** set this). |
| `execution_time_ms` | number | always | Wall ms after input parse through cleanup. |
| `language` / `mode` | string | always | Echo of what ran. Empty only on parse failure before dispatch. |
| `build_log` | string | **build only** | Daemon image-build stdout+stderr, truncated. Omitted in script mode. |
| `image_built` | boolean | **build only** | Daemon tagged the image. Omitted in script mode. |

Compare rules (`OutputComparer`): type mismatch, missing key, unexpected key, array length, scalar equality. JSON `1` and `1.0` compare equal.

### OUTPUT `error`

The port **value is the string itself**. Downstream error-boundary nodes should treat non-empty as “do not trust `output`”.

Examples: `input.language is required: python | javascript | typescript | java`; `language java must use mode=build`; `files[].path must be a relative POSIX path without '..'`.

## Configuration parameters

| shortCode | Type | Required | Default | Accepted values | Purpose |
| --- | --- | --- | --- | --- | --- |
| `default_timeout_seconds` | number | no | `60` | Positive int. ≤ 0 → 60 | Used when `input.timeout_seconds` omitted or ≤ 0. Keep ≤ daemon `sqs.sandbox.oci.requestTimeoutMs` (default 300000). |
| `max_log_chars` | number | no | `20000` | Positive int. ≤ 0 → 20000 | Cap `stdout` / `stderr` / `build_log`. Payload/DoS bound. Suffix `…[truncated, max_log_chars=N]`. |
| `verbose` | select | no | `false` | `true` \| `false` | One-line dispatch trace on **emulator** stderr (`language`, timeout, temp dir / image tag). Captured in instance logs, not `output.stderr`. |
| `allowed_languages` | string | no | `python,javascript,typescript,java` | CSV of those tokens | Allow-list. Empty string → default four. Unknown tokens never match. Example disable Java: `python,javascript,typescript`. |

Per-test `input.configuration` overrides these for that invoke (`EmulatorConfig.from` merges test map last).

## How it works

### Dispatch

`NodeHandlers.registerAll` registers `TestEmulatorNode` (catalog `node-test-emulator` / fallback id above). `NodeLibraryApp` uses this id as `SQ_SELF_TEMPLATE_ID` default. Invoke JSON is routed by `templateId`. Reply keys are `output` and `error`. Never throws past the dispatcher; never logs `helperToken`.

`StudioQuackHelper` is unused for the test itself. Logging and build-mode use `HelperClient` (`curl --unix-socket $SQ_HELPER_SOCKET`, `Authorization: Bearer` from the invoke body’s `helperToken` — token stays out of JSON bodies).

### Script mode (`ScriptTestRunner`)

1. Create temp dir `sq-emu-script-*`.
2. Generate `studioquack_helper.py` or `studioquack_helper.js` with `input_data`, `configuration`, `mock_function_responses`.
3. Wrap source so `studioquack` is in scope (`from studioquack_helper import studioquack` / `require('./studioquack_helper.js')`).
4. Run `python3 script.py` / `node script.js` / `tsx script.ts` with wall-clock timeout (`ProcessRunner`; kill `destroyForcibly` on expiry).
5. Read `output.json` (port → value) and `invocation_log.json`.
6. Deep-compare to `expected_output`. Delete the temp dir.

Success: exit 0 **or** `output.json` written. Missing `returnResult` after a clean exit → `script_success=false`, report error `Script did not produce output.json (returnResult was not called)`.

Mock API the script may use:

- `getInputValuesByPortValue(port)` → list
- `getParameterValueByShortCode(shortCode)`
- `execute(portValue, payload)` → mock or `{}`
- `returnResult([ResultItem(port, value), …])` → writes `output.json`

Variable/provider ports do not exist here.

### Build mode (`BuildTestRunner`)

Requires `SQ_HELPER_SOCKET` + `helperToken`. Otherwise report error `build-mode requires the daemon helper channel (SQ_HELPER_SOCKET + helperToken)`.

`POST /v1/helper/test/build-run` with `{files, mock_function_responses, input_data, configuration, execution_context, request, timeout_seconds, build_wall_clock_seconds:600}`. Curl `--max-time` = invoke timeout + 660s. `--fail` is **not** used so 401/403/400/503 JSON is mapped.

The **daemon** (not this sandbox) stages the context, builds on its build network with caps, runs the image as a hardened job (`SQ_STATEFUL_RUNNER=true`, daemon-side mocked `studioquack.execute`), POSTs invoke JSON to `http://127.0.0.1:${SQ_APP_PORT}/invoke`, collects outputs, removes container and image.

Channel errors: `unauthorized`, `test_build_forbidden` (appends hint that `sqs.testbuild.allowed-images` must include `sq-local/sq-builtin-node-library`), `test_build_unavailable`. The emulator never talks to a host engine socket.

### Retry / backoff

None. One attempt per invoke. Timeout kills the subprocess / stops the wait loop.

### Error mapping

| Failure | `error` port | `output.error` / `test_passed` |
| --- | --- | --- |
| Bad/missing input, illegal language/mode pairing, path escape | non-empty string | report also has `script_success=false`; `test_passed=false` |
| Completed test that failed compare or runtime | `""` | `output.error` set for process/build failures; compare-only failures leave `output.error` empty and set `differences` |
| Unexpected exception | `SimpleName: message` | same |

## Security & legal

- **Secrets:** no provider API keys. `helperToken` is request-scoped, header-only, never logged, never placed in `test/build-run` JSON. Do not put production secrets in `script` / `files[]` / `input_data` — they appear in reports and logs.
- **Data:** test payloads stay on the daemon host (script temp dir inside this sandbox; Java builds on the daemon build network). Nothing is sent to OpenAI or other SaaS unless the **code under test** does so (and script-mode has no real FUNCTION ports).
- **Sandbox:** this node runs in Podman. Script subprocesses inherit that jail. Build-mode images are separate hardened jobs with daemon-side mocks. `files[].path` cannot escape the workspace (`..` / absolute / `\` rejected).
- **Isolation:** build-mode allow-list (`sqs.testbuild.allowed-images`) prevents arbitrary image builds from untrusted templates. A compromised emulator instance must not reach another env’s helper socket.
- **DoS:** `timeout_seconds`, `max_log_chars`, and daemon request timeout bound CPU/RAM/log size. Do not raise `max_log_chars` unbounded.
- **GDPR:** treat fixture data as customer data. Do not paste live PHI into emulator inputs. Reports (`stdout`, `output`) are retained with the run history of that env.
- **ToS / liability:** developer tool. Customer owns fixtures and any licenses of the code under test. StudioQuack’s duty is containment (path allow-list, helper auth, image allow-list) so one test cannot pivot to another tenant.

## Examples

### 1. Python script (sum)

`input`:

```json
{
  "language": "python",
  "mode": "script",
  "script": "vals = studioquack.getInputValuesByPortValue('in')\nresult = vals[0]['a'] + vals[0]['b']\nstudioquack.returnResult([studioquack.ResultItem('sum', result)])",
  "input_data": {"in": {"a": 3, "b": 5}},
  "expected_output": {"sum": 8},
  "timeout_seconds": 30
}
```

`output` (abridged): `{ "test_passed": true, "script_success": true, "output": {"sum": 8}, "output_matches": true, "differences": [], "error": "", "language": "python", "mode": "script" }`. OUTPUT `error` = `""`.

### 2. Java build (emulator-level failure)

`input` with `language":"java"`, `mode":"build"` but `files` omitted → OUTPUT `error` = `input.files is required in mode=build: array of {path, content} including a Containerfile or Dockerfile`. Do not trust `output` for assertions in that case.

Happy-path build `files` must include a root `Containerfile`/`Dockerfile` plus sources; the daemon returns `image_built` / `build_log` on the report.
