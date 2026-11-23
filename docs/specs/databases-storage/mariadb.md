# MariaDB

- **Status:** planned — **do not build a second node**
- **Catalog:** `docs/catalog/databases-storage.md`
- **Canonical node:** [`mysql.md`](mysql.md) (`engine=mariadb`)
- **Folder when built:** `nodes/mysql/`
- **Researched:** 2026-08-20
- **Official API:** MySQL Client/Server Protocol (MariaDB-compatible)
- **Docs:** https://mariadb.com/docs/server/connect/programming-languages/
- **Auth:** userPass — MySQL node `password`

## Scope

MariaDB speaks the MySQL protocol for password + TLS SQL operations. **Do not invent a MariaDB node.** Use `engine=mariadb`.

**Do not implement `unix_socket` auth** (host filesystem / OS user socket). ed25519 is a server auth plugin; the node still sends a password secret.

## Operations (`operation` config values)

Same as MySQL (`procedure.call` allowed on MariaDB).

## Configuration parameters

MySQL config.

## Ports

Same as MySQL.

## Legal

- https://mariadb.com/legal/
- Customer-owned server

## Implementation notes

- Same classic-protocol driver. Default TLS on. No host paths.
