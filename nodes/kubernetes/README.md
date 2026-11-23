# Kubernetes

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_KUBERNETES`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Kubernetes API](https://kubernetes.io/docs/reference/kubernetes-api/) at config `api_server` (default in-cluster `https://kubernetes.default.svc`).

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `get` | GET | `/api/v1/namespaces/{namespace}/{resource}/{name}` | Core v1 only (not apps/v1 Deployments) |
| `list` | GET | `/api/v1/namespaces/{namespace}/{resource}` | Official HTTP |
| `apply` | POST | `/api/v1/namespaces/{namespace}/{resource}` | Official HTTP |
| `delete` | DELETE | `/api/v1/namespaces/{namespace}/{resource}/{name}` | Official HTTP |
| `patch` | PATCH | `/api/v1/namespaces/{namespace}/{resource}/{name}` | JSON merge patch (`application/merge-patch+json`) of `spec` |
| `scale` | PATCH | `/apis/apps/v1/namespaces/{namespace}/{resource}/{name}/scale` | Merge patch `spec.replicas` from `replicas` |
| `logs` | GET | `/api/v1/namespaces/{namespace}/pods/{name}/log` | Official HTTP |
| `job_create` | POST | `/apis/batch/v1/namespaces/{namespace}/jobs` | Official HTTP |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Secret | Purpose |
|---|---|---|---|---|
| `operation` | select | yes | no | Chooses the row above |
| `token` | password | yes | yes | Service-account or user bearer token. Never log. Do not mount host kubeconfig paths into the sandbox. |
| `api_server` | string | no | no | API server origin, e.g. `https://k8s.example.com:6443`. Empty = in-cluster `https://kubernetes.default.svc`. TLS must chain to the JDK trust store. |
| `timeout_seconds` | number | no | no | Default 25 |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.token`. Never logged, never returned.
- [Kubernetes API docs](https://kubernetes.io/docs/reference/kubernetes-api/). Terms: https://www.linuxfoundation.org/legal/terms.
- Customer cluster RBAC. Packaged host is in-cluster DNS. Never Docker.sock. Secret objects: do not log values. listen is Watch — engine treats it as BPMN ingress, not a streaming watch.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
