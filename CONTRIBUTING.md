# Contributing to SQ Builtin Node Library

Thank you for helping keep StudioQuack’s official built-in node deck correct,
safe, and documented. This project is open source under the
[Apache License, Version 2.0](LICENSE). Every commit must carry a Developer
Certificate of Origin sign-off (`git commit -s`), and you agree to follow the
[Code of Conduct](CODE_OF_CONDUCT.md). No separate contributor agreement is
required (see [License of contributions](#license-of-contributions)).

## Developer setup

You need:

- **JDK 21+** on `JAVA_HOME` (Temurin 21 is what the image is built on).
- **Apache Maven** 3.9+ (for compiling the dispatcher and node handlers).
- **Podman** (the daemon executes this image with Podman, not Docker).
- Git, and a text editor.

Clone this repository, then from the repo root:

```bash
mvn -q test                                                             # compile and run the tests
podman build -t sq-local/sq-builtin-node-library:dev -f Containerfile .  # local image build
```

A local image is for development only. Engines build and run the library
themselves from the signed StudioQuack release (it is platform-managed), so a
local build never replaces the instance an engine runs.

## Branch and pull-request flow

1. Open an issue (or reference an existing one) for anything larger than a
   typo.
2. Branch from `main`: `git checkout -b feat/<short-name>` or
   `fix/<short-name>`.
3. Keep the change small: one node, one dispatcher fix, or one docs patch per
   PR.
4. Open a pull request against `main`. Fill in the template: what changed, how
   you tested, and which `templateId` / folder you touched.
5. A maintainer reviews definition JSON, handler code, Containerfile `ENV`
   keys, and whether secrets can leak to logs.
6. Squash or rebase as requested; do not force-push shared branches.

## Commit style

Use [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | Use for |
| --- | --- |
| `feat:` | a new node or dispatcher capability |
| `fix:` | a bug in a handler, definition, or docs that were wrong |
| `docs:` | README, `docs/`, comments-only |
| `chore:` | build, ignore files, non-functional maintenance |
| `security:` | a vulnerability fix (coordinate via [SECURITY.md](SECURITY.md) first) |

Subject line in the imperative, ~72 characters, explain *why* in the body when
the diff is not obvious. Example: `feat: add arxiv handler and definition`.

Every commit must include a **Developer Certificate of Origin** sign-off:

```
Signed-off-by: Your Name <you@example.com>
```

Configure the trailer with `git commit -s`. `-s` certifies the DCO below.

## Developer Certificate of Origin

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

Everyone is permitted to copy and distribute verbatim copies of this
license document, but changing it is not allowed.


Developer's Certificate of Origin 1.1

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

The “open source license indicated in the file” for this repository is the
Apache License, Version 2.0.

## Definition JSON conventions

Every node lives under `nodes/<kebab-name>/node_definition.json`. Follow
[docs/adding-a-node.md](docs/adding-a-node.md) for the full schema.

Rules that reviews will bounce:

- **Every parameter needs a `description`.** That includes each `ports[].schema`
  child and each `configurationParameters[]` entry.
- The description must state **purpose**, the **accepted values** (exact set or
  clearly bounded type), and at least one **example**.
- Port `type` is `INPUT` or `OUTPUT` (FUNCTION ports are allowed only where the
  node really is an agent/tool hub).
- `_id` is stable. Do not recycle an id onto a different node.
- `name` / `label` match the catalog row in `docs/NODE_CATALOG.md`.

Never put live API keys, tokens, or sample secrets in definitions or fixtures.
Use obvious placeholders (`YOUR_API_KEY`, `$secretRef`).

## Security expectations

- Do not log `helperToken`, `SQVM_HELPER_TOKEN`, `SQ_HELPER_BEARER`, or any
  credential a node receives. See [SECURITY.md](SECURITY.md).
- Handlers must be thread-safe: the daemon may overlap requests on one
  container when the image is dispatch-capable.
- Bound payloads and timeouts; do not follow redirects to `file:` or
  `localhost` unless the node’s contract says so.

## License of contributions

Contributions are licensed under the Apache License, Version 2.0, exactly as
its §5 provides: unless you state otherwise in writing, any contribution you
intentionally submit for inclusion is licensed to StudioQuack and to every
recipient under Apache-2.0, including its patent grant (§3). You **retain
copyright** in your work. Your DCO sign-off certifies that you have the right to
make that contribution; if your employer owns your work, obtain its permission
first.

Never mention competitor product or company names anywhere in a contribution —
not in code, comments, docs, example values, or tests. Describe behaviour
neutrally (“other workflow-automation tools”) or omit the reference.
