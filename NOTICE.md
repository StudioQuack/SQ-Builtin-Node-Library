# Notice

SQ Builtin Node Library
Copyright 2026 StudioQuack, Inc.

This product is licensed under the Apache License, Version 2.0
([LICENSE](LICENSE)). Under Apache-2.0 §4(d), redistributions that include a
NOTICE file must carry the attribution notices below.

“StudioQuack” and “Duckbot” are trademarks of StudioQuack, Inc. The Apache
License grants no trademark rights (§6); see [TRADEMARKS.md](TRADEMARKS.md).

## Runtimes installed into the image

The `Containerfile` installs the following runtimes from their upstream
distributions. Each is used unmodified and keeps its own license.

- **Eclipse Temurin (OpenJDK)** — runs the Java dispatcher and node handlers.
  License: GPL-2.0 with the Classpath Exception (the Adoptium build tooling is
  Apache-2.0); the Classpath Exception means the GPL covers the JDK, not the
  node host. Source: https://adoptium.net/ and https://github.com/adoptium.
  The image is built from the public `eclipse-temurin` base image, so the JDK
  is obtained from Adoptium; if StudioQuack publishes a prebuilt image, the
  corresponding JDK source will be offered alongside it (GPL-2.0 §3).
- **Node.js** — JavaScript runtime installed in the image, with `tsx` for
  TypeScript. License: MIT, plus the licenses of bundled native dependencies
  listed in the Node.js distribution. Source:
  https://github.com/nodejs/node/blob/main/LICENSE
- **Python (CPython)** — runs the in-container helper broker
  (`SQ_HELPER_SOCKET`) that the daemon plants. License: Python Software
  Foundation License Version 2. Source: https://docs.python.org/3/license.html

## Libraries shaded into `app.jar`

JUnit is test-only and not shipped.

- **JSch** (`com.github.mwiede:jsch`) — SSH and SFTP. License: BSD-style (JSch
  License), plus ISC for bundled cryptography. Source:
  https://github.com/mwiede/jsch
- **Angus Mail** (`org.eclipse.angus:angus-mail`, `angus-activation`) with the
  **Jakarta Mail API** and **Jakarta Activation API** — SMTP, IMAP, and MIME.
  License: EPL-2.0 or GPL-2.0 with the Classpath Exception (used under
  EPL-2.0), and EDL 1.0 (BSD-3-Clause) for the activation API. Source:
  https://github.com/eclipse-ee4j/angus-mail
- **PostgreSQL JDBC Driver** (`org.postgresql:postgresql`) — License:
  BSD-2-Clause. Source: https://github.com/pgjdbc/pgjdbc. Runtime dependency
  **Checker Framework qualifiers** (`org.checkerframework:checker-qual`) —
  License: MIT. Source: https://github.com/typetools/checker-framework
- **MariaDB Connector/J** (`org.mariadb.jdbc:mariadb-java-client`) — MySQL
  node. License: LGPL-2.1. Used unmodified; you may replace it with another
  build of the same version. Source:
  https://github.com/mariadb-corporation/mariadb-connector-j
- **Microsoft JDBC Driver for SQL Server** (`com.microsoft.sqlserver:mssql-jdbc`)
  — License: MIT. Source: https://github.com/microsoft/mssql-jdbc
- **SQLite JDBC Driver** (`org.xerial:sqlite-jdbc`) — License: Apache-2.0; the
  bundled SQLite engine is in the public domain. Only the glibc Linux x86_64
  and aarch64 native libraries are shaded. Source:
  https://github.com/xerial/sqlite-jdbc

Not bundled: the **Oracle JDBC driver** (closed source, Oracle Free Use Terms
and Conditions). Operators who use the Oracle Database node install it
themselves; see `nodes/oracle-database/README.md`.

Regenerate the inventory with
`mvn org.codehaus.mojo:license-maven-plugin:add-third-party` after adding a
dependency, and list every new compile-scope dependency here.
