# Inline Field Schema

Demonstrates `(engine.protoc.openapi.inline_schema)`, the field-level boolean that inlines a single message-typed field's schema at the field site instead of emitting a `$ref` into `components/schemas`.
Same transitivity semantics as the method-level [inlineSchemas](../inlineSchemas/README.md) example.

## What it exercises

Two `inline_schema = true` field usages over a shared message graph:

| Field                       | Target shared with non-inline path? | Result                                                                                                |
| --------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `Archive.manifest`          | Yes — also `GetManifest`'s response | Inline expansion at the field site; nested `entry` still `$ref`s `Entry` (Entry is in components).    |
| `Preview.snapshot`          | No — only reached via this field    | Inline expansion at the field site; nested `tag` is also inline-expanded (transitively inline-only).  |

`Archive`, `Header`, `Manifest`, and `Entry` are in components.
`Snapshot` and `Tag` are absent from components — they only ever appear inline inside `Preview.snapshot`.

## Why this name (`inline_schema`, not `inline`)

The `(engine.protoc.openapi.inline)` extension already exists on `EnumOptions`.
Both extensions live in `annotations.proto`, which generates a single `Annotations.java` class with all extensions as static fields — two same-named fields would not compile.
The field-level annotation is named `inline_schema` to keep the Java symbol set unique while still being clear at the proto-source level.

## Relationship to the method-level inline flags

`inline_schema` (field-level) and `inline_request_schema` / `inline_response_schema` (method-level) cooperate.
A field marked `inline_schema = true` is *always* inlined at its emission site, whether the field is encountered inside a component schema, inside an inline-expanded request/response body, or inside another inline-expanded field.
Targets that have any non-inlined reachable path elsewhere stay in components and are `$ref`'d from the inline expansion; targets reachable only through inline boundaries are expanded inline at every reference.

## Cycle safety

Cycle handling matches [inlineSchemas](../inlineSchemas/README.md): the compiler detects when an inline expansion would recurse onto a type already being expanded, force-adds that type to `components/schemas`, and emits a `$ref` at the cycle point.
