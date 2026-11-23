# Planned-node development specs

Implementer-ready specs for every **planned** catalog row. Shipped (`done`) nodes
are documented next to their code (`nodes/<key>/README.md`) and are **not**
duplicated here.

Each spec is researched against the provider's **current official API** (docs
fetched at write time). The catalog table in `docs/catalog/` remains the index;
this folder is the build contract.

## Layout

`docs/specs/<category-slug>/<node-slug>.md`

Category slugs match `docs/catalog/*.md` filenames without the extension.

## Spec contract (every file)

Copy the skeleton in [`_TEMPLATE.md`](_TEMPLATE.md). A spec is incomplete if any
of these are missing:

1. Official API name + version + canonical docs URL (verified, not guessed).
2. Auth surface and which config params are StudioQuack secrets.
3. `operation` values that exist on the **current** API (drop retired verbs).
4. Per-operation input fields and output fields.
5. Ports (default 1 INPUT + 1 OUTPUT). File payloads use type `file`.
6. Legal / ToS / data-residency notes. No unofficial scrape unless the catalog
   already flags the row unofficial.
7. Researched-on date (ISO).

## Design rules (do not regress)

Same as [`docs/NODE_CATALOG.md`](../NODE_CATALOG.md): one node per provider;
FUNCTION ports only when the node must invoke another node N times; triggers
are operations; flow control is BPMN, never a node; official-API-first
([LEGAL.md](../../LEGAL.md)); competitor product names never appear.
