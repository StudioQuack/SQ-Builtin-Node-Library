# LDAP

- **Status:** done
- **Catalog:** `docs/catalog/utilities-core.md`, `docs/catalog/security-identity.md`
- **Folder:** `nodes/ldap/`
- **Researched:** 2026-08-20
- **Official API:** LDAP (RFC 4511), auth (RFC 4513), URL (RFC 4516), filters (RFC 4515), DNs (RFC 4514)
- **Docs:** https://www.rfc-editor.org/rfc/rfc4511
- **Auth:** bind DN + password — secret binding: `bind_password` (type password only)
- **Handler:** `com.studioquack.nodelibrary.nodes.protocol.LdapNode` (`SQ_TEMPLATE_ID_LDAP`)

## Scope

Talks to the operator’s directory with the JDK JNDI LDAP provider (`javax.naming.ldap`). Production must use `ldaps://` or StartTLS. Plain `ldap://` is refused unless `allow_plain_ldap=true`. Customer owns the DIT. Referrals are not followed.

## Operations (`operation` config values)

| operation | Kind | Path / RPC | When to use |
|---|---|---|---|
| `search` | LDAP | SearchRequest | Query. `filter` or `filter_attribute`+`filter_value`. Size cap 200. |
| `compare` | LDAP | Compare via OBJECT_SCOPE search | Assert attribute=value on a DN |
| `create` | LDAP | AddRequest | Add entry (`attributes` map) |
| `update` | LDAP | ModifyRequest REPLACE | Replace listed attributes |
| `delete` | LDAP | DelRequest | Delete entry |
| `rename` | LDAP | ModifyDN | Rename / move (`new_dn`) |

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | search | no | Verb |
| `url` | string | yes | | no | `ldap://` or `ldaps://` |
| `bind_dn` | string | yes | | no | Bind DN |
| `bind_password` | password | yes | | yes | Bind secret (password type only) |
| `start_tls` | select | no | true | no | StartTLS before bind on `ldap://` |
| `allow_plain_ldap` | select | no | false | no | Permit cleartext `ldap://` (lab) |
| `base_dn` | string | no | | no | Default search / parent DN |
| `timeout_seconds` | number | no | 25 | no | Connect+read / search timeLimit (max 60) |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | filter / filter_attribute / filter_value, dn / rdn_*, attributes, attribute+value, scope, return_attributes | Input wins over config. Built filter/DN values are escaped. |
| `output` | OUTPUT | `{ status, data }` | Failures raise a BPMN error (INVALID_INPUT, AUTH_FAILED, REQUEST_FAILED). Bind password is redacted. |

FUNCTION ports: none.

## Legal

- Official LDAP. Customer directory and its access policy.
- `bind_password` is a secret. LDAPS/StartTLS required unless the operator opts into plain LDAP.

## Implementation notes

- In-process Java. No extra Maven dependency.
- Size-limit 200 on search. Timeouts from `timeout_seconds` (default 25, cap 60).
- StartTLS binds only after the upgrade so the password is not sent in the clear.
- Binary attribute values are reported as `<binary>`.
- Errors raise a BPMN error; they are not placed on the output port.
