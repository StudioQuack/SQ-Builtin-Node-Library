# Bitbucket

Bitbucket Cloud REST 2.0 at `https://api.bitbucket.org/2.0`. Docs: [REST](https://developer.atlassian.com/cloud/bitbucket/rest/intro/)

Auth: HTTP Basic — config **`email`** (Atlassian account email, not workspace username) + secret **`access_token`** (Atlassian API token). A 401 “API token must be used with an atlassian registered email” means `email` does not match the token owner.

**`workspace` / `repo_slug`:** path parameters on **INPUT** per call (not node config secrets). Bind defaults in the workflow or pass each invocation.

`pr_create` takes official `title`, `source` `{branch:{name}}`, `destination` `{branch:{name}}`, `description`, `close_source_branch`. `pr_merge` takes `type` (`pullrequest`), `message`, `merge_strategy` (`merge_commit` | `squash` | `fast_forward`). `pipeline_run` takes `target` and `variables`.

## Operations

| operation | HTTP | Path |
|---|---|---|
| `repo_get` | GET | `/repositories/{workspace}/{repo_slug}` |
| `repo_list` | GET | `/repositories/{workspace}` |
| `file_get` | GET | `/repositories/{workspace}/{repo_slug}/src/{commit}/{path}` |
| `pr_create` | POST | `/repositories/{workspace}/{repo_slug}/pullrequests` |
| `pr_get` | GET | `/repositories/{workspace}/{repo_slug}/pullrequests/{id}` |
| `pr_merge` | POST | `/repositories/{workspace}/{repo_slug}/pullrequests/{id}/merge` |
| `pipeline_run` | POST | `/repositories/{workspace}/{repo_slug}/pipelines` |
| `pipeline_get` | GET | `/repositories/{workspace}/{repo_slug}/pipelines/{pipeline_uuid}` |
| `commit_list` | GET | `/repositories/{workspace}/{repo_slug}/commits` |
| `listen` | ingress | — |

## Ports

`input` / `output`. Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.

## Config

`operation`, `access_token` (password), `email`, `timeout_seconds`.
