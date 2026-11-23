# npm

In-process StudioQuack builtin. Handler: `OfficialApiNode` loading `nodes/npm/api_spec.json`.

## Overview

npm public registry (packument, search, dist-tags).

- **Docs:** https://github.com/npm/registry/blob/main/docs/REGISTRY-API.md
- **Dist-tag set:** https://gitlab.com/gitlab-org/ops/registry-conformance/-/blob/main/docs/specs/S06-npm-contracts.md (PUT body = `JSON.stringify(version)`)
- **Auth:** bearer `token` (required for dist_tag_set). Never on INPUT.

## Operations

| operation | HTTP | Path | Notes |
|---|---|---|---|
| `package_get` | GET | `/{pkg}` | Full packument |
| `package_versions` | GET | `/{pkg}` | Same packument (versions in `versions`) |
| `package_search` | GET | `/-/v1/search` | query `text`, `size`, `from` |
| `dist_tag_get` | GET | `/-/package/{pkg}/dist-tags` | |
| `dist_tag_set` | PUT | `/-/package/{pkg}/dist-tags/{tag}` | raw JSON string `version`; optional `otp` → `npm-otp` |

Scoped package names: send `@scope%2Fname` in `pkg`.
