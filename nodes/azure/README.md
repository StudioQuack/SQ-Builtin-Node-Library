# Azure

## Overview

StudioQuack builtin (`templateId` from `node_definition.json` `_id`, override `SQ_TEMPLATE_ID_AZURE`). Handler: `OfficialApiNode` + `api_spec.json`. Calls the official [Azure Resource Manager REST](https://learn.microsoft.com/en-us/rest/api/azure/) at `https://management.azure.com`. Every request sends `x-ms-version: 2026-04-06` and `x-ms-date` (RFC 1123) via spec `extraHeaders`.

Inbound provider webhooks are **not** handled by this node. `listen` / `trigger` operations are `kind: ingress`: use a BPMN message start, intermediate catch, or boundary message event. StudioQuack webhook ingress correlates onto those events.

## Ports

| Port | Direction | Type | Required | Schema / flow |
|---|---|---|---|---|
| `input` | INPUT | object | yes | Per-operation fields. Never put `access_token` here. |
| `output` | OUTPUT | object | yes | Success: `status=ok`, `http_status`, `data`. Failure: `{error}` only. |

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `blob_get` | GET | `{account}.blob.core.windows.net/{container}/{blob}` | extraHeaders `x-ms-version: 2026-04-06` + `x-ms-date` |
| `blob_put` | PUT | `{account}.blob.core.windows.net/{container}/{blob}` | extraHeaders `x-ms-version` + `x-ms-date` + `x-ms-blob-type: BlockBlob`; raw body from input `content` |
| `blob_delete` | DELETE | `{account}.blob.core.windows.net/{container}/{blob}` | extraHeaders `x-ms-version` + `x-ms-date` |
| `blob_list` | GET | `{account}.blob.core.windows.net/{container}` | query restype, comp |
| `functions_invoke` | POST | `{functionApp}.azurewebsites.net/api/{functionName}` | JSON body from input `payload`; `x-functions-key` from config `function_key` |
| `servicebus_send` | POST | `{namespace}.servicebus.windows.net/{queue}/messages` | Raw body from input `message` |
| `servicebus_receive` | POST | `{namespace}.servicebus.windows.net/{queue}/messages/head` | Official HTTP |
| `vm_get` | GET | `management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/providers/Microsoft.Compute/virtualMachines/{vm}` | query api-version |
| `vm_power` | POST | `management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/providers/Microsoft.Compute/virtualMachines/{vm}/{powerAction}` | query api-version |
| `aks_get` | GET | `management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/providers/Microsoft.ContainerService/managedClusters/{cluster}` | query api-version |
| `keyvault_get` | GET | `{vault}.vault.azure.net/secrets/{secret}` | query api-version |
| `monitor_query` | GET | `management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/providers/{providerNamespace}/{resourceType}/{resourceName}/providers/Microsoft.Insights/metrics` | GET (not POST); query api-version, metricnames, timespan |
| `resourcegroup_get` | GET | `management.azure.com/subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}` | query api-version |
| `listen` | ingress | — | BPMN message event (no socket) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | blob_get | no | Official API operation. EXACT: blob_get, blob_put, blob_delete, blob_list, functions_invoke, servicebus_send, servicebus_receive, vm_get, vm_power, aks_get, keyvault_get, monito... |
| `access_token` | password | yes |  | yes | OAuth access token minted outside StudioQuack (client-credentials or other). OfficialApiNode sends Authorization Bearer. Never logged. |
| `subscriptionId` | string | yes |  | no | Subscription id. |
| `container` | string | no |  | no | Blob container for blob_*. |
| `blob` | string | no |  | no | Blob name for blob_get/put/delete. |
| `account` | string | no |  | no | Storage account name for blob_*. |
| `comp` | string | no |  | no | Query-string parameter `comp` for operation: blob_list. |
| `restype` | string | no |  | no | Query-string parameter `restype` for operation: blob_list. |
| `functionApp` | string | no |  | no | Function App name for functions_invoke. |
| `functionName` | string | no |  | no | Function name for functions_invoke. |
| `function_key` | password | no |  | yes | Function or host key for functions_invoke (`x-functions-key`). Empty for anonymous functions. Never logged. |
| `namespace` | string | no |  | no | URL path parameter `namespace` for operations: servicebus_send, servicebus_receive. Required by: servicebus_send, servicebus_receive. |
| `queue` | string | no |  | no | URL path parameter `queue` for operations: servicebus_send, servicebus_receive. Required by: servicebus_send, servicebus_receive. |
| `timeout` | string | no |  | no | Query-string parameter `timeout` for operation: servicebus_receive. |
| `resourceGroup` | string | no |  | no | ARM resource group for VM / AKS / monitor ops. Example: prod-rg. |
| `vm` | string | no |  | no | Virtual machine name for vm_get / vm_power. |
| `api-version` | string | no |  | no | ARM api-version. Example: 2024-07-01. |
| `powerAction` | string | no |  | no | URL path parameter `powerAction` for operation: vm_power. Required by: vm_power. |
| `cluster` | string | no |  | no | URL path parameter `cluster` for operation: aks_get. Required by: aks_get. |
| `vault` | string | no |  | no | URL path parameter `vault` for operation: keyvault_get. Required by: keyvault_get. |
| `secret` | string | no |  | no | URL path parameter `secret` for operation: keyvault_get. Required by: keyvault_get. |
| `resourceType` | string | no |  | no | URL path parameter `resourceType` for operation: monitor_query. Required by: monitor_query. |
| `aggregation` | string | no |  | no | Query-string parameter `aggregation` for operation: monitor_query. |
| `interval` | string | no |  | no | Query-string parameter `interval` for operation: monitor_query. |
| `timespan` | string | no |  | no | Query-string parameter `timespan` for operation: monitor_query. |
| `resourceName` | string | no |  | no | URL path parameter `resourceName` for operation: monitor_query. Required by: monitor_query. |
| `metricnames` | string | no |  | no | Query-string parameter `metricnames` for operation: monitor_query. |
| `providerNamespace` | string | no |  | no | URL path parameter `providerNamespace` for operation: monitor_query. Required by: monitor_query. |
| `timeout_seconds` | number | no | 25 | no | Connect + read timeout. Default 25. Keep at or below the daemon stateful budget (~300s). |

## How it works

1. `OfficialApiHandlers` registers this folder key because `api_spec.json` exists.
2. Path `{placeholders}` are taken from input or config and URL-encoded.
3. JDK HTTP via `DataSupport.exchange`. Response cap is engine-bounded.
4. HTTP 2xx → `status=ok`. Other statuses → `error` plus `http_status`.
5. Ingress operations return an instruction to use BPMN message events — no HTTP call.

## Security & legal

- Secrets only from `configurationParameters.access_token`. Never logged, never returned.
- [Azure Resource Manager REST docs](https://learn.microsoft.com/en-us/rest/api/azure/). Terms: https://www.microsoft.com/licensing/terms/.
- Microsoft product terms. Distinct from Azure DevOps. Cosmos lives in the databases catalog. Event Grid listen is BPMN ingress.
- Official API only. No HTML scrape. File fields use type `file` (ref-id).
