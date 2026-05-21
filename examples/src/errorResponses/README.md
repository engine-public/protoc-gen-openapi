# Error Responses

Demonstrates `(engine.protoc.openapi.method).error_responses`, the shortcut for attaching typed error scenarios to an operation without writing the full nested `responses` tree.

Each `ErrorResponse` entry expands at compile time into a `responses[status]` entry whose schema is `google.rpc.Status` (via `allOf`) carrying a `details.items` `$ref` to the named error class.
The referenced error class is added to `components/schemas` as a normal component, and `google.rpc.Status` is auto-emitted into components whenever any `error_responses` entry appears (no need to set `convertGrpcStatus`).

## What it exercises

Four RPCs over the same `Item` graph:

| RPC      | Scenario                                                                                                                                                                          |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Get`    | Single error scenario at status `404` with `grpc_code = NOT_FOUND`. The compiler emits `x-grpc-code: "NOT_FOUND"` on the response and seeds an `examples.grpc-error.value.code = 5`. |
| `List`   | Multiple error scenarios across `400`, `401`, `500` with their respective `grpc_code` values, exercising the per-code `x-grpc-code` + example seeding for each.                    |
| `Update` | Collision: an explicit `responses[409]` and an `error_responses[0] { status: "409" }` declared on the same operation. The shortcut wins, a `WARN` is logged.                       |
| `Delete` | Collision within `error_responses` itself: two entries at status `404`. The second entry wins, a `WARN` is logged naming both error types.                                          |

## Anatomy of an `ErrorResponse`

```proto
{
  status: "404"
  error_type: { [type.googleapis.com/engine.protoc.openapi.example.errorResponses.ValidationError] {} }
  description: "Item not found."
  grpc_code: NOT_FOUND
}
```

* `status` — any OAS-legal key (`"400"`, `"4XX"`, `"default"`, …).
* `error_type` — bracketed `Any` syntax matching `proto_message_ref` elsewhere in the model. Only the resolved type is consulted; the empty `{}` body is ignored.
* `description` — optional. Falls back to the error type's leading proto comment, then to the literal `"Error"`.
* `grpc_code` — optional `google.rpc.Code` enum value. When set:
  * the string enum name is emitted as the `x-grpc-code` vendor extension on the response;
  * the response media type gets an `examples.grpc-error` block whose `value.code` is the integer enum number.

## Emitted shape

For `Get`'s 404:

```yaml
responses:
  "404":
    description: "Item not found."
    x-grpc-code: "NOT_FOUND"
    content:
      application/json:
        examples:
          grpc-error:
            summary: "NOT_FOUND"
            value:
              code: 5
              message: ""
              details: []
        schema:
          allOf:
            - $ref: "#/components/schemas/google.rpc.Status"
            - type: object
              properties:
                details:
                  type: array
                  items:
                    $ref: "#/components/schemas/ValidationError"
```

## Resolution rules

The same last-write-wins behaviour applies in both collision shapes, with a WARN log identifying the operation, the contested status code, and the conflicting payloads:

* **Status already declared by `responses`** — the `error_responses` entry overwrites the explicit `responses` entry at the same status.
* **Status declared twice in `error_responses`** — the second entry overwrites the first.

The auto-inferred `200` success response is never warned about; only conflicts among explicitly-declared statuses are reported.
