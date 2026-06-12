# Inline Schemas (Global Defaults)

Demonstrates the [`inlineRequestSchemas`](../../../README.md#compiler-options) and [`inlineResponseSchemas`](../../../README.md#compiler-options) compiler options, which set the default inlining behavior for every RPC.
Both options default to `true`, so by default every request/response body schema is inlined at the use site rather than emitted as a `$ref` into `components/schemas`.
Per-method `inline_request` / `inline_response` annotations override the global default for a specific RPC.

This example leaves the global defaults untouched (both `true`) and demonstrates the override semantics by setting `inline_request: false` / `inline_response: false` on two of the four RPCs.

## What it exercises

Four RPCs over a `Widget` / `Gadget` graph:

| RPC             | Annotation                          | Result                                                                                                |
| --------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `GetWidget`     | none                                | Response is the inline Widget schema (global default).  Widget never lands in `components/schemas`.   |
| `GetGadget`     | `inline_response: false`            | Response is `$ref: "#/components/schemas/Gadget"`.  Gadget is kept in components because of this RPC. |
| `UpdateWidget`  | none                                | `requestBody` schema is the inline Widget schema (global default).                                    |
| `UpdateGadget`  | `inline_request: false`             | `requestBody` schema is `$ref: "#/components/schemas/Gadget"`.                                        |

`Widget` is reachable only via inlined paths so it never lands in components.
`Gadget` is reachable through `GetGadget`'s non-inlined response, so it appears in components and the other (inlined) Gadget call sites `$ref` into it.

## Precedence

Per-method annotation wins over the global option, regardless of either value:

* `inline_request: true` inlines even when `inlineRequestSchemas = false`.
* `inline_request: false` keeps the `$ref` even when `inlineRequestSchemas = true`.
* Same for `inline_response` / `inlineResponseSchemas`.

Methods with no `inline_request` / `inline_response` annotation inherit the global option.

## See also

* [`inlineSchemas`](../inlineSchemas/README.md) — same per-method annotations, but with the global options pinned to `false` so only the annotated RPCs inline.
