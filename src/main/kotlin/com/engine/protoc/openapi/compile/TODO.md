# Compiler Gap Analysis

Fields and messages defined in the model that the compiler currently silently ignores.
Organized by severity. All items reference the compiler file where the fix belongs.

---

## 1. `Components` — missing sections (OpenApiSerializer.kt · `Components.toJson`)

`Components.toJson()` only emits `requestBodies` and `securitySchemes`. The following
map fields are defined in `components.proto` but never written to JSON:

| Field        | Type                                | Notes                                                     |
|--------------|-------------------------------------|-----------------------------------------------------------|
| `responses`  | `map<string, ResponseOrReference>`  | Reusable response objects                                 |
| `parameters` | `map<string, ParameterOrReference>` | Reusable parameter objects                                |
| `examples`   | `map<string, ExampleOrReference>`   | Requires Example serializer (see §4)                      |
| `headers`    | `map<string, HeaderOrReference>`    | Already have `Header.toJson`, trivial to add              |
| `links`      | `map<string, LinkOrReference>`      | Requires Link serializer (see §5)                         |
| `callbacks`  | `map<string, CallbackOrReference>`  | Requires Callback serializer (see §6)                     |
| `path_items` | `map<string, PathItemOrReference>`  | Already have `PathItemOrReference.toJson`, trivial to add |

`schemas` is intentionally omitted here — it is handled separately by `SchemaBuilder`.

---

## 2. `Operation.callbacks` — field silently dropped (OpenApiSerializer.kt · `Operation.toJson`)

`operation.proto` field 9 is `map<string, Reference> callbacks`. The `Operation.toJson()`
serializer does not emit it. Because this field holds `Reference` (not `CallbackOrReference`,
due to the protobuf circular-dependency constraint documented in the proto), the fix is
straightforward: iterate the map and call `Reference.toJson()` for each value.

---

## 3. `PathItem` — missing `parameters` field and `ref_type` oneof (OpenApiSerializer.kt · `PathItem.toJson`)

`path_item.proto` defines two things that `PathItem.toJson()` currently ignores:

- **`parameters` (field 14)**: A list of `ParameterOrReference` that applies to all
  operations on the path. These are path-level defaults and must appear in the JSON output.
  Already have `ParameterOrReference.toJson`, so this is a one-liner addition.

- **`ref_type` oneof (fields 1–2, `uri_ref` / `proto_rpc_ref`)**: Per the OpenAPI spec,
  a Path Item may embed a `$ref`. `PathItem.toJson()` never checks `refTypeCase`, so the
  `$ref` is silently dropped. Emit it the same way `Reference.toJson()` does.

---

## 4. `Example` / `ExampleOrReference` / `ExampleOrReferenceMap` — no serializers
(OpenApiSerializer.kt — new functions needed)

These three model messages have no `toJson()` functions at all:

- `Example` (fields: `summary`, `description`, oneof `value` / `external_value`)
- `ExampleOrReference` (oneof `example` / `reference`)
- `ExampleOrReferenceMap` (map `examples`)

Once serializers exist, the following gaps that depend on them can be closed:

| Location          | Field                                     | Type                              |
|-------------------|-------------------------------------------|-----------------------------------|
| `ParameterSchema` | `example` / `examples` (oneof fields 5–6) | `Any` / `ExampleOrReferenceMap`   |
| `HeaderSchema`    | `example` / `examples` (oneof fields 4–5) | `Any` / `ExampleOrReferenceMap`   |
| `MediaType`       | `example` / `examples` (oneof fields 2–3) | `Any` / `ExampleOrReferenceMap`   |
| `Components`      | `examples` (field 4)                      | `map<string, ExampleOrReference>` |

Note: `ParameterSchema.toJson()` is a stub (`@Suppress("UnusedParameter", "unused")`)
that only emits `schema`. When adding example support, the style/explode/allowReserved
fields should be added at the same time (see §8).

---

## 5. `Link` / `LinkOrReference` — no serializers (OpenApiSerializer.kt — new functions needed)

No `Link.toJson()` or `LinkOrReference.toJson()` exists. Fields to serialize:

- `operation_locator_type` oneof: `operation_ref` → `"operationRef"`, `operation_id` →
  `"operationId"`, `proto_rpc_ref` → resolved via `ctx.resolveProtoRef()` → `"operationRef"`
- `parameters` (`map<string, Any>`) → object of runtime expressions; use `Any.toJson(ctx)`
- `request_body` (`Any`) → use `Any.toJson(ctx)`
- `description` (optional string)
- `server` (`Server`) → use `Server.toJson(ctx)`

Once `LinkOrReference.toJson()` exists, add it to:

| Location                  | Field             |
|---------------------------|-------------------|
| `ResponseObject.toJson()` | `links` (field 4) |
| `Components.toJson()`     | `links` (field 8) |

---

## 6. `Callback` / `CallbackOrReference` — no serializers (OpenApiSerializer.kt — new functions needed)

No `Callback.toJson()` or `CallbackOrReference.toJson()` exists.

`Callback` holds `map<string, PathItemOrReference> callbacks`. The serializer should
iterate the map and call `PathItemOrReference.toJson()` for each value.

Once `CallbackOrReference.toJson()` exists, add it to:

| Location              | Field                 |
|-----------------------|-----------------------|
| `Components.toJson()` | `callbacks` (field 9) |

Note: `Operation.callbacks` is `map<string, Reference>` (not `CallbackOrReference`) due to
the circular-dependency constraint — that gap is covered separately in §2.

---

## 7. `MediaType` — `example`, `examples`, `encoding` silently dropped (OpenApiSerializer.kt · `MediaType.toJson`)

`MediaType.toJson()` is a stub that only emits `schema`. Missing fields:

- `example` / `examples` oneof (fields 2–3) — depends on Example serializer (§4)
- `encoding` (`map<string, Encoding>`) — depends on Encoding serializer (§8)

---

## 8. `Encoding` — no serializer (OpenApiSerializer.kt — new function needed)

No `Encoding.toJson()` exists. Fields to serialize:

- `content_type` (optional string) → `"contentType"`
- `headers` (`map<string, Reference>`) → emit as object; use `Reference.toJson()`
- `style` (optional string)
- `explode` (optional bool)
- `allow_reserved` (optional bool) → `"allowReserved"`

Once the serializer exists, add it to `MediaType.toJson()` (§7).

---

## 9. `ParameterSchema` — stub serializer (OpenApiSerializer.kt · `ParameterSchema.toJson`)

`ParameterSchema.toJson()` is currently suppressed and only emits `schema`. The
`Parameter.toJson()` function manually reads `style`, `explode`, and `allowReserved` from
the embedded `ParameterSchema`, but `ParameterSchema.toJson()` itself is incomplete.

The `example` / `examples` oneof (fields 5–6) is silently dropped. Once the Example
serializer (§4) is in place, add example emission to `Parameter.toJson()` as well (reading
from `schema.exampleTypeCase`).

---

## 10. `HeaderSchema.example` / `examples` silently dropped (OpenApiSerializer.kt · `Header.toJson`)

`Header.toJson()` inlines `HeaderSchema` fields directly. It handles `style`, `explode`,
and `schema`, but not the `example` / `examples` oneof (fields 4–5 of `HeaderSchema`).
Once the Example serializer (§4) is in place, add emission here.

---

## 11. `Parameter.content` variant silently dropped (OpenApiSerializer.kt · `Parameter.toJson`)

`parameter.proto` has `oneof parameter_definition_type { ParameterSchema schema,
ParameterContent content }`. `Parameter.toJson()` only handles the `schema` variant.
The `content` variant (a `ParameterContent` wrapping `map<string, MediaType>`) is never
serialized.

`ParameterContent.toJson()` needs to be added (emit as `"content"` object using
`MediaType.toJson()`), and `Parameter.toJson()` must dispatch on `parameterDefinitionTypeCase`.

---

## 12. `Header.content` variant silently dropped (OpenApiSerializer.kt · `Header.toJson`)

Same pattern as §11. `header.proto` has `oneof header_definition_type { HeaderSchema schema,
HeaderContent content }`. Only the `schema` variant is handled.

`HeaderContent.toJson()` needs to be added, and `Header.toJson()` must dispatch on
`headerDefinitionTypeCase`.

---

## Summary

| Done | #  | Order | Affected message(s)                                      | Severity | Depends on                   |
|------|----|-------|----------------------------------------------------------|----------|------------------------------|
| [ ]  | 1  | 6     | `Components` (5 missing map fields)                      | High     | §4, §5, §6 for full coverage |
| [ ]  | 2  | 4     | `Operation.callbacks`                                    | Medium   | —                            |
| [ ]  | 3  | 3     | `PathItem.parameters`, `PathItem.$ref`                   | Medium   | —                            |
| [ ]  | 4  | 1     | `Example`, `ExampleOrReference`, `ExampleOrReferenceMap` | High     | —                            |
| [ ]  | 5  | 2     | `Link`, `LinkOrReference`                                | High     | —                            |
| [ ]  | 6  | 5     | `Callback`, `CallbackOrReference`                        | Medium   | —                            |
| [ ]  | 7  | 9     | `MediaType.example/examples/encoding`                    | High     | §4, §8                       |
| [ ]  | 8  | 8     | `Encoding`                                               | Medium   | —                            |
| [ ]  | 9  | 10    | `ParameterSchema.example/examples`                       | Low      | §4                           |
| [ ]  | 10 | 11    | `HeaderSchema.example/examples`                          | Low      | §4                           |
| [ ]  | 11 | 7     | `Parameter.content` variant                              | Low      | —                            |
| [ ]  | 12 | 12    | `Header.content` variant                                 | Low      | —                            |
