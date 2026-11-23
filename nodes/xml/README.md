# XML

- **Status:** done (package)
- **Catalog:** `docs/catalog/utilities-core.md`
- **Folder:** `nodes/xml/`
- **Researched:** 2026-08-20
- **Official API:** W3C XML 1.0 Fifth Edition
- **Docs:** https://www.w3.org/TR/xml/
- **Auth:** none — secret binding: `(none)`
- **Handler:** `OfficialApiNode` via `api_spec.json` (`SQ_TEMPLATE_ID_XML`)

## Scope

In-process XML 1.0 conversion. XXE disabled: no external entities, no DTD network fetch.


## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `json_to_xml` | in-process | XML 1.0 | JSON → XML |
| `xml_to_json` | in-process | XML 1.0 | XML → JSON |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | json_to_xml | no | Which capability to run. EXACT accepted values: json_to_xml / xml_to_json. Required. Example: 'json_to_xml'. |
| `property` | string | no |  | no | Wrapper property name when converting a fragment. |
| `root_name` | string | no | root | no | Root element name for json_to_xml. Default 'root'. |
| `attr_key` | string | no | @ | no | JSON key prefix treated as attributes. Default '@'. |
| `cdata` | select | no | false | no | Wrap text in CDATA. Default false. |
| `item` | object | no |  | no | JSON object or array to encode. INPUT wins. |
| `xml` | string | no |  | no | XML document. INPUT wins. |
| `timeout_seconds` | number | no | 25 | no | Connect+read / run budget in seconds. Positive integer. Blank/0/negative → 25. Keep at or below the sandbox request budget. Example: 25. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | Failures never include secrets |
| `output` | OUTPUT | object — `{ status, … }` or `{ error }` | JSON string from the handler |

FUNCTION ports: none.

## Legal

- Official XML. No scrape. Operator owns document contents.

## Implementation notes

- Disable DTDs/external entities on the parser.
- Size cap.
- Timeouts from `timeout_seconds` (default 25). Size-cap payloads.
- Errors map to `{ error }` on OUTPUT; never throw past dispatch; never echo secrets.
- Do not write a new Java class; `OfficialApiNode` loads this folder.
