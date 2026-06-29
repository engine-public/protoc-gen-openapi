# Inline Schemas (Method-Level)

Demonstrates `(engine.protoc.openapi.inline_request_schema)` and `(engine.protoc.openapi.inline_response_schema)`, the method-level annotations that inline an RPC's request or response body where it would otherwise emit a `$ref` into `components/schemas`.

## What it exercises

Four RPCs over the same `Envelope`/`Payload`/`PrivateNote`/`NoteAttachment` graph, each illustrating one corner of the inline-vs-`$ref` decision:

| RPC              | Annotation                                | Result                                                                                                 |
| ---------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `GetEnvelope`    | none                                      | Response is `$ref: "#/components/schemas/Envelope"` (control).                                         |
| `PeekEnvelope`   | `inline_response_schema = true`           | Response is the inline Envelope schema; its nested `payload` is `$ref: Payload` (Payload is in components). |
| `GetNote`        | `inline_response_schema = true`           | Response is the inline PrivateNote schema; its nested `attachment` is also inline (NoteAttachment is reached only via this inline path). |
| `UpdateEnvelope` | `inline_request_schema = true`, body=field | `requestBody` schema is the inline Envelope; the 200 response remains `$ref: Envelope`.                |

## The transitivity rule

A message lands in `components/schemas` iff it is reachable through at least one non-inlined path.
Messages reached *only* through inlined boundaries are expanded inline at every reference site.
Messages reached through both an inlined and a non-inlined path get a component entry and the inline expansion `$ref`s into it.

In this example:

* `Envelope` and `Payload` are reachable via `GetEnvelope` (non-inlined), so both appear in components.
  `PeekEnvelope`'s inline body and `UpdateEnvelope`'s inline request both `$ref` into `Payload` rather than re-expanding it.
* `PrivateNote` and `NoteAttachment` are reachable only via `GetNote`'s inline response, so neither appears in components.
  Both are expanded inline at the response site (`NoteAttachment` nested inside `PrivateNote`).

## Cycle safety

Self-referential or mutually-recursive types reachable only through inline boundaries cannot be fully expanded.
The compiler detects the cycle during expansion, force-adds the cyclic type to `components/schemas`, and emits a `$ref` at the cycle point.
The outer levels are still inlined; only the recursive back-edge becomes a `$ref`.
