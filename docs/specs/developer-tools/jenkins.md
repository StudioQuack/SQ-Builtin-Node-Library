# Jenkins

- **Status:** planned
- **Catalog:** `docs/catalog/developer-tools.md`
- **Folder when built:** `nodes/jenkins/`
- **Researched:** 2026-08-20
- **Official API:** Jenkins Remote Access API (JSON flavor, unversioned tree under `…/api/json`)
- **Docs:** https://www.jenkins.io/doc/book/using/remote-access-api/
- **Auth:** api_key (HTTP Basic `user:apiToken`) — secret binding: `apiToken`
- **CSRF:** https://www.jenkins.io/doc/book/security/csrf-protection/

## Scope

One node for a **customer Jenkins controller**. The handler calls that controller’s Remote Access API over HTTPS (`baseUrl`). Operations cover job create/copy, build trigger (fire-and-forget and wait), job/build/queue reads, quiet-down, restart, and `listen`.

**Never** talk to Docker Engine (`/var/run/docker.sock` or the Engine HTTP API). **Never** scrape the Jenkins HTML UI. Use JSON (`/api/json`), not Python flavor.

`listen` is **not** a Jenkins-core inbound webhook. The Notification Plugin is **outbound** (Jenkins POSTs job status to a URL). There is no core “register webhook” verb. `listen` therefore **polls** `build_list` / `queue_get` (documented limitation) unless the operator points Jenkins Notification / Generic Webhook Trigger at StudioQuack message-event ingress (BPMN message catch — not this node receiving a socket).

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `job_build` | POST | `/job/{jobPath}/build` or `/job/{jobPath}/buildWithParameters` | Trigger a build; return immediately with queue location |
| `job_build_wait` | POST then GET | same POST, then poll `/job/{jobPath}/lastBuild/api/json` | Trigger and wait until `building=false` or timeout |
| `job_get` | GET | `/job/{jobPath}/api/json` | Job metadata, last build, next build number |
| `job_copy` | POST | `/createItem?name={newName}&mode=copy&from={fromJob}` | Copy an existing job |
| `job_create` | POST | `/createItem?name={newName}` | Create from config XML body |
| `build_list` | GET | `/job/{jobPath}/api/json?tree=builds[number,url,result,timestamp,building]` | Recent builds for a job |
| `queue_get` | GET | `/queue/api/json` | Items waiting to run |
| `quiet_down` | POST | `/quietDown` | Prepare controller for restart (optional `reason`) |
| `restart` | POST | `/safeRestart` (default) or `/restart` | Restart controller |
| `listen` | GET (poll) | `/job/{jobPath}/api/json?tree=builds[…]` and/or `/queue/api/json` | Poll for new builds/queue items. **Limitation:** not a push subscription. |

Folder jobs: encode each folder segment as `/job/{folder}/job/{name}` (Jenkins “job path”). Do not use a single slash path.

Dropped (not core Remote Access API / not official for this node): Docker.sock, HTML scrape, Groovy script console, Notification Plugin *outbound* as a handler verb, undocumented crumb query params on GET.

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `job_get` | no | Chooses the row above |
| `baseUrl` | string | yes | empty | no | Controller origin, e.g. `https://ci.example.com`. No trailing slash. HTTPS required in production. |
| `user` | string | yes | empty | no | Jenkins username for Basic auth |
| `apiToken` | password | yes | empty | yes | User API token (not account password). Header `Authorization: Basic base64(user:apiToken)`. Never on INPUT. |
| `fetchCrumb` | boolean | no | `false` | no | If `true`, GET `/crumbIssuer/api/json` and send `{crumbRequestField}: {crumb}` plus session cookie on mutating POSTs. API-token Basic auth is CSRF-exempt on Jenkins ≥ 2.96 weekly / 2.107 LTS; leave `false` unless the controller still requires a crumb. |
| `timeout_seconds` | number | no | `30` | no | Connect + request timeout. Floor 1. `job_build_wait` uses this as **per poll** timeout; overall wait is `wait_timeout_seconds`. |
| `insecureTls` | boolean | no | `false` | no | Lab-only. Default verifies TLS. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No secrets |
| `output` | OUTPUT | `{ status, … }` or `{ error }` | Never echo `apiToken` or crumb |

FUNCTION ports: none.

## Input fields (by operation)

### `operation = job_build` | `job_build_wait`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job` | string | yes | Job path (`my-job` or `folder/job`) |
| `parameters` | object | no | String params → POST `/buildWithParameters` as `application/x-www-form-urlencoded`. Omit → `/build`. |
| `wait_timeout_seconds` | number | no | `job_build_wait` only. Default `120`. Cap 300. Poll every 2s with jitter. |
| `cause` | string | no | Optional `cause` form field where the job accepts it |

### `operation = job_get` | `build_list`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job` | string | yes | Job path |
| `depth` | number | no | `job_get`: Jenkins `depth` query (0–2). Default 0. |
| `limit` | number | no | `build_list`: max builds to return (default 20, cap 100) |

### `operation = job_create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | yes | New item name (`createItem?name=`) |
| `config_xml` | string | yes | `config.xml` body. `Content-Type: application/xml`. Cap 1 MiB. |

### `operation = job_copy`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `from_job` | string | yes | Existing job name (`from=`) |
| `name` | string | yes | New job name |

### `operation = queue_get`

| Field | Type | Required | Purpose |
|---|---|---|---|
| *(none)* | | | Uses controller queue |

### `operation = quiet_down`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `reason` | string | no | Optional reason query/form field if the controller supports it |

### `operation = restart`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `safe` | boolean | no | Default `true` → POST `/safeRestart`. `false` → POST `/restart`. |

### `operation = listen`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `job` | string | no | If set, poll that job’s builds; else poll `/queue/api/json` |
| `since_build` | number | no | Ignore builds with `number` ≤ this |
| `poll_seconds` | number | no | Default 15. Min 5. Single poll per invoke (BPMN timer loop waits between invokes). |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | 2xx | `ok` |
| `job` | object | `job_get` | Name, URL, `buildable`, `lastBuild`, `nextBuildNumber` |
| `queue_item` | string | `job_build` | `Location` header from POST `/build` |
| `build` | object | `job_build_wait` | `number`, `result`, `url`, `building`, `duration` |
| `builds` | array | `build_list`, `listen` | Summaries: `number`, `url`, `result`, `timestamp`, `building` |
| `queue` | object | `queue_get` | `items` array (ids, tasks, stuck) |
| `created` | object | `job_create`, `job_copy` | New job name |
| `error` | string | failure | Actionable; no token/crumb |
| `http_status` | number | HTTP error | Provider status |
| `retry_after_seconds` | number | 429 / 5xx / timeout | Honor `Retry-After` if present; else 30–60 with jitter |

## Legal

- Remote Access API: https://www.jenkins.io/doc/book/using/remote-access-api/
- CSRF crumbs: https://www.jenkins.io/doc/book/security/csrf-protection/
- Authenticating scripted clients: https://www.jenkins.io/doc/book/system-administration/authenticating-scripted-clients/
- Jenkins is customer-hosted. The operator owns the controller, jobs, and tokens. StudioQuack does not accept Jenkins ToS on their behalf.
- Build logs and job configs may contain secrets or personal data — do not log bodies.
- No published global rate limit; treat 429/503 as backpressure.

## Implementation notes

- JDK `HttpClient` only. TLS verified unless `insecureTls` (lab). `Redirect.NORMAL` for GET; do not follow POST redirects blindly — capture `Location` on 201 from `/build`.
- Headers: `Authorization: Basic …`, `Accept: application/json`, `User-Agent: StudioQuack/1.0`. Mutating POSTs: empty body unless parameters/XML.
- If `fetchCrumb=true`: GET `{baseUrl}/crumbIssuer/api/json` with Basic auth; store cookie; send crumb header. Never log crumb.
- Job path helper: split `job` on `/`, join as `/job/{seg}/job/{seg}`.
- Body cap **8 MiB**. `job_build_wait` polling: max `wait_timeout_seconds`; 429 → retry with jitter (max 3) then `{ error }`.
- `restart` / `quiet_down` are destructive to the **customer controller** — succeed on 200/302/503 as Jenkins often drops the connection; map connection reset after POST as success with `{ status: "ok", note: "controller restarting" }` only when the POST was accepted.
- Logs: operation, HTTP status, job name — never token, crumb, XML, or parameter values that look like secrets.
- Errors: 4xx/5xx → `{ error }` on OUTPUT. Never throw past dispatch.
