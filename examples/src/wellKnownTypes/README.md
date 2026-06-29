# Well-Known Types

Demonstrates how the plugin emits inline OpenAPI schemas for structural protobuf well-known types instead of dangling `$ref`s into `components/schemas`.

## What it exercises

The proto defines an `Envelope` message whose fields cover every structural WKT the plugin treats as inline, plus a `google.api.HttpBody` return type that internally references `google.protobuf.Any`:

| Proto type                     | Inline schema                                                      |
| ------------------------------ | ------------------------------------------------------------------ |
| `google.protobuf.Any`          | `{ "type": "object", "additionalProperties": true }`               |
| `google.protobuf.Struct`       | `{ "type": "object", "additionalProperties": true }`               |
| `google.protobuf.Value`        | `{}` (any JSON value)                                              |
| `google.protobuf.ListValue`    | `{ "type": "array", "items": {} }`                                 |
| `google.protobuf.FieldMask`    | `{ "type": "string" }`                                             |
| `google.protobuf.Empty`        | `{ "type": "object", "properties": {}, "additionalProperties": false }` |

The `DownloadEnvelope` RPC returns `google.api.HttpBody` — a user-facing component schema whose `extensions` field is `repeated google.protobuf.Any`.
Before the fix, that field emitted `$ref: "#/components/schemas/Google_Protobuf_Any"` against a target that the compiler never collected, leaving Swagger UI unable to resolve the chain.
After the fix the `extensions` items carry the Any inline schema directly, and no WKT message ever appears in `components/schemas`.

## Why these shapes

The proto3 JSON encoding of each structural WKT is *not* its proto field layout — `Any` carries `@type` + free-form members, `Struct` is a flat open object, `Value` is any JSON value, and so on.
Emitting the proto field structure (e.g. `{type_url, value}` for `Any`) would be technically faithful to the protobuf wire form but useless to a client speaking JSON, so the plugin substitutes the JSON-canonical shape at every reference.
