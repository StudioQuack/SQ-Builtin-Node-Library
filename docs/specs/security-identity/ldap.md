# LDAP

- **Status:** planned
- **Catalog:** `docs/catalog/security-identity.md`
- **Folder when built:** `nodes/ldap/`
- **Researched:** 2026-08-20
- **Official API:** LDAP version 3 (LDAPv3) technical specification — RFC 4510 road map
- **Docs:** [RFC 4510](https://www.rfc-editor.org/rfc/rfc4510.html) (road map), [RFC 4511](https://www.rfc-editor.org/rfc/rfc4511.html) (protocol), [RFC 4512](https://www.rfc-editor.org/rfc/rfc4512.html) (directory information models), [RFC 4513](https://www.rfc-editor.org/rfc/rfc4513.html) (authentication and StartTLS), [RFC 4519](https://www.rfc-editor.org/rfc/rfc4519.html) (user-application schema). Supporting: [RFC 4514](https://www.rfc-editor.org/rfc/rfc4514.html) (DN strings), [RFC 4515](https://www.rfc-editor.org/rfc/rfc4515.html) (search-filter strings). IANA: `ldap` TCP **389**, `ldaps` TCP **636**.
- **Auth:** simple Bind (RFC 4511 §4.2, RFC 4513 §5.1) — secret binding: `bindPassword`

## Scope

Directory **protocol client** for LDAPv3. The designer picks `operation`; the handler maps it to the matching LDAP PDU in RFC 4511 (Bind, Search, Compare, Add, Modify, Modify DN, Delete) against the configured host. This is not a vendor IdP product and not an HTTP API.

**Allowed exception to “JDK HTTP only”:** LDAP is not HTTP. Use the JDK LDAP stack (`javax.naming` / `javax.naming.ldap` or the JDK’s built-in LDAP provider). **No vendor directory SDK.** Do not wrap password-spray, bind-spray, subtree-exfil, or other attack tools. Do not add Unbind / Abandon / arbitrary Extended operations as catalog verbs (Unbind is session teardown; StartTLS is transport, not an `operation`).

v1 Bind is **simple** name/password or anonymous only (RFC 4513 §5.1). SASL mechanisms (RFC 4513 §5.2), including EXTERNAL, are out of v1.

## Operations (`operation` config values)

| operation | HTTP | Path / RPC | When to use |
|---|---|---|---|
| `bind` | LDAP | BindRequest `[APPLICATION 0]` RFC 4511 §4.2 | Authenticate this session (or prove credentials). Version 3. |
| `search` | LDAP | SearchRequest `[APPLICATION 3]` RFC 4511 §4.5 | Read one entry, children, or a subtree. Filter string: RFC 4515. |
| `compare` | LDAP | CompareRequest `[APPLICATION 14]` RFC 4511 §4.10 | Test one attribute value assertion without returning the value. |
| `create` | LDAP | AddRequest `[APPLICATION 8]` RFC 4511 §4.7 | Add an entry that MUST NOT already exist. |
| `update` | LDAP | ModifyRequest `[APPLICATION 6]` RFC 4511 §4.6 | Atomic add/delete/replace of attribute values. |
| `modify-dn` | LDAP | ModifyDNRequest `[APPLICATION 12]` RFC 4511 §4.9 | Rename (new RDN) and/or move (`newSuperior`). |
| `delete` | LDAP | DelRequest `[APPLICATION 10]` RFC 4511 §4.8 | Remove a **leaf** entry only. |

Catalog verbs kept. **Not built (RFC 4511, omitted on purpose):** Unbind §4.3, Abandon §4.11, Extended §4.12 except StartTLS used internally when `security=starttls`, Modify increment (RFC 4525).

## Configuration parameters

| Name | Type | Required | Default | Secret | Purpose |
|---|---|---|---|---|---|
| `operation` | select | yes | `search` | no | Chooses the row above |
| `host` | string | yes | empty | no | Directory host (DNS or literal). Not a URL. |
| `port` | number | no | `636` if `security=tls`, else `389` | no | TCP port. RFC 4511 §5.2: LDAP/TCP **389**. LDAPS (TLS from first byte) is typically **636**. |
| `bindDn` | string | no | empty | no | Bind name (LDAPDN, RFC 4514). Empty + empty password → anonymous simple Bind (RFC 4513 §5.1.1). A service Bind DN is **high privilege** — least privilege, rotate. |
| `bindPassword` | password | no | empty | yes | Simple Bind password (UTF-8; SASLprep SHOULD, RFC 4511 §4.2). Never on INPUT. Never logged. Required when `bindDn` is non-empty. |
| `security` | select | yes | `tls` | no | `tls` (implicit TLS / LDAPS) \| `starttls` (RFC 4513 §3 / RFC 4511 §4.14 on the LDAP port) \| `none`. **`none` is cataloged but runtime-rejected** — RFC 4513 §6.3.3: implementations SHOULD NOT support cleartext passwords without TLS. |
| `allowUnauthorizedCerts` | boolean | no | `false` | no | When `true`, skip TLS certificate verification. **Off by default.** Lab-only; production must keep `false`. |
| `timeout` | number | no | `15` | no | Connect + LDAP timeLimit budget in seconds (floor 1, cap 120). Maps to SearchRequest.timeLimit when searching. |
| `baseDn` | string | no | empty | no | Default SearchRequest.baseObject (RFC 4511 §4.5.1.1) when input `baseDn` omitted. RFC 4514 string. |

## Ports

| Port | Direction | Schema | Notes |
|---|---|---|---|
| `input` | INPUT | object — per-operation fields | No passwords, no bind secrets |
| `output` | OUTPUT | object — `{ status, resultCode, … }` or `{ error }` | Failures never include `bindPassword` or attribute values that look like `userPassword` |

FUNCTION ports: none.

## Input fields (by operation)

Every operation first establishes the TCP session, applies `security`, then performs a simple Bind with config `bindDn`/`bindPassword` (except `operation=bind`, which may override name/password from input as specified below). After the PDU, the handler Unbinds and closes. One node run = one short session. Do not keep a pooled bind across workflow steps in v1.

### `operation = bind`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `name` | string | no | BindRequest.name. Default: config `bindDn`. Empty → anonymous if password empty. |
| `password` | string | no | **Rejected on INPUT.** Bind password is config `bindPassword` only (secret binding). If present on input → `{ error }` no network. |
| `version` | number | no | BindRequest.version. Default `3`. Any other value → `{ error }` (RFC 4511 v3 only). |

This operation exists to **test credentials** or to confirm Bind success (`resultCode=success`). It is not a spray loop: one Bind per node run.

### `operation = search`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `baseDn` | string | no | SearchRequest.baseObject. Default: config `baseDn`. Empty after default → `{ error }`. |
| `scope` | string | no | `baseObject` \| `singleLevel` \| `wholeSubtree` (RFC 4511 §4.5.1.2). Default `baseObject`. |
| `derefAliases` | string | no | `neverDerefAliases` (default) \| `derefInSearching` \| `derefFindingBaseObj` \| `derefAlways` (§4.5.1.3). |
| `sizeLimit` | number | no | SearchRequest.sizeLimit. `0` = server unlimited — **this node treats 0 as omit and applies its own cap 100**. Floor 1, cap **100**. |
| `timeLimit` | number | no | Seconds. Default config `timeout`. Cap 120. |
| `typesOnly` | boolean | no | RFC 4511 §4.5.1.6. Default `false`. |
| `filter` | string | yes | RFC 4515 filter, e.g. `(uid=alice)` or `(objectClass=inetOrgPerson)`. Empty → `{ error }`. Reject filters longer than **4096** chars. |
| `attributes` | array | no | Attribute Selection (§4.5.1.8). `["*"]` user attrs; `["+"]` operational if the server allows. Default `["*"]`. Cap 64 names. |

Do not add a “dump directory” helper. `wholeSubtree` + `(objectClass=*)` is allowed but still capped by `sizeLimit`.

### `operation = compare`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dn` | string | yes | CompareRequest.entry (LDAPDN). |
| `attribute` | string | yes | AVA attribute description (e.g. `uid`). |
| `value` | string | yes | Assertion value. Never log. |

RFC 4511 §4.10: some servers allow compare on `userPassword` without search. That is still a **credential check** — do not log `value`.

### `operation = create`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dn` | string | yes | AddRequest.entry. Parent MUST exist (§4.7). |
| `attributes` | object | yes | Map of attribute type → string or array of strings (AttributeList). MUST include `objectClass` (RFC 4512). Do not send NO-USER-MODIFICATION operational attrs (`createTimestamp`, `creatorsName`, …). |

Schema for user applications (RFC 4519): `cn`, `sn`, `uid`, `mail`, `ou`, `dc`, etc. The server enforces schema; the node does not invent classes.

### `operation = update`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dn` | string | yes | ModifyRequest.object. |
| `changes` | array | yes | Ordered list, applied atomically (§4.6). Each item: `{ "operation": "add"\|"delete"\|"replace", "attribute": "mail", "values": ["a@example.com"] }`. `delete` with empty `values` removes the whole attribute. `replace` with empty `values` deletes the attribute if present. Cap 32 changes. Cannot modify RDN values — use `modify-dn`. |

### `operation = modify-dn`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dn` | string | yes | ModifyDNRequest.entry. |
| `newrdn` | string | yes | RelativeLDAPDN (e.g. `uid=alice`). |
| `deleteoldrdn` | boolean | no | Default `true`. |
| `newSuperior` | string | no | New parent DN. Omit to rename in place. Target parent MUST exist. |

### `operation = delete`

| Field | Type | Required | Purpose |
|---|---|---|---|
| `dn` | string | yes | DelRequest LDAPDN. Leaf only (§4.8). Non-leaf → server `notAllowedOnNonLeaf`. |

## Output fields (by operation)

| Field | Type | When | Purpose |
|---|---|---|---|
| `status` | string | success | `ok` |
| `resultCode` | string | always on LDAP result | RFC 4511 §4.1.8 name: `success`, `compareTrue`, `compareFalse`, `noSuchObject`, `invalidCredentials`, `confidentialityRequired`, `sizeLimitExceeded`, … |
| `matchedDn` | string | LDAP result | LDAPResult.matchedDN when present |
| `diagnostic` | string | LDAP result | Truncated diagnosticMessage (240 chars). Redact DNs that contain `userPassword` values — they should not appear. |
| `boundAs` | string | `bind` success | Bind name used (not the password) |
| `entries` | array | `search` | `{ dn, attributes }` objects. Cap body **1_000_000** bytes total. Strip `userPassword` / `unicodePwd` / `userPKCS12` from attributes before OUTPUT. |
| `count` | number | `search` | Number of entries returned (after cap) |
| `matched` | boolean | `compare` | `true` if `compareTrue`, `false` if `compareFalse` |
| `dn` | string | `create` / `modify-dn` / `delete` / `update` success | Target DN (new DN after `modify-dn`) |
| `error` | string | failure | Actionable; no password |
| `retry_after_seconds` | number | timeout / busy | `30` on timeout / `busy` / `unwillingToPerform` |

`invalidCredentials` → `{ error }` with `resultCode`; do not distinguish “bad DN” vs “bad password” beyond what the server sent (avoid extra oracle).

## Legal

- Protocol specs: IETF RFC 4510–4519 (Standards Track, June 2006). No vendor ToS for the wire protocol.
- Directory **contents** are personal data (names, emails, employee numbers, often passwords). GDPR: minimize attributes requested; do not copy a subtree into another store “for convenience.”
- Bind DN with write rights can create/delete people. Operator duty: least-privilege service account, rotate `bindPassword`, restrict daemon egress to the directory hosts.
- RFC 4513 §6.3.3: cleartext passwords over the network are strongly discouraged. This node **refuses `security=none`**.
- Do not use this node to automate credential stuffing, password spraying, or unauthorized enumeration of a directory you do not operate.
- `allowUnauthorizedCerts=true` disables server authentication and is a confidentiality failure on an open network (RFC 4513 StartTLS goals). Keep default `false`.
- Official-API-first: RFCs are the official surface. No scrape of a directory web UI.

## Implementation notes

- **Client:** JDK LDAP (`InitialLdapContext` / `LdapContext`) — documented exception to JDK HTTP-only. TLS via LDAPS (`ldaps://host:port`) or StartTLS extended operation then Bind (RFC 4513 §3.1.1: StartTLS **before** Bind so the password is protected).
- `security=none` → `{ error: "LDAP requires LDAPS (security=tls) or STARTTLS (security=starttls). Cleartext is not allowed." }` — no TCP.
- `allowUnauthorizedCerts` default false. When false, verify the server certificate (hostname + trust). When true, log a **non-secret** warning flag only (`tlsInsecure=true`), never the cert bytes.
- Timeouts: connect and read, both = `timeout` seconds. SearchRequest.timeLimit set to the same budget.
- Size: search `sizeLimit` cap 100; response cap 1_000_000 bytes; filter length cap 4096.
- Redact from logs: `bindPassword`, Bind password bytes, compare `value`, `userPassword` / `unicodePwd` values, full entry dumps. Log: host, port, security, operation, `resultCode`, entry count, sizes.
- No retry loop on `invalidCredentials` (that is spray). No inner loop over password lists.
- Error mapping: LDAP result → OUTPUT `{ error, resultCode }`. Do not throw past dispatch.
- Host must not be used to open arbitrary non-LDAP protocols; port is TCP LDAP only.
