# Envoy GrpcJsonTranscoder — OAS Schema Impact Burndown

This document catalogs every `GrpcJsonTranscoder` option and evaluates whether it must change the
OpenAPI spec produced by this compiler. Options are grouped: those that materially affect the schema
come first, in implementation-priority order; options with no schema impact are listed at the end
for completeness.

Reference: [`GrpcJsonTranscoder` proto](https://github.com/envoyproxy/envoy/blob/main/api/envoy/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto)
and [`GrpcJsonTranscoder.kt`](examples/src/envoy/kotlin/GrpcJsonTranscoder.kt) (in-repo model).

---

## Options That Materially Affect the OAS Schema

### 1. `print_options.preserve_proto_field_names`

**Status: not yet implemented**

**What it does.** By default the Envoy transcoder uses the protobuf `json_name` field option (or
lowerCamelCase if none is set) as the JSON key. When `preserve_proto_field_names = true` the
original proto snake_case field name is used instead, for both requests and responses.

**Current compiler behaviour.** `SchemaBuilder` already uses `field.jsonName`, which returns the
`json_name` value or lowerCamelCase. There is no plugin option to override this.

**Required compiler changes.**

Add a new plugin option (e.g., `preserveProtoFieldNames: Boolean = false`) that switches
`SchemaBuilder` and `PathsBuilder` to use `field.name` (the raw proto name) for all property keys
in every schema component and inline schema. The change must be consistent across:

- `components/schemas` property keys
- `paths` request body inline schemas
- `paths` query-parameter names that are derived from field names
- Any `required` array entries

**Before (default, `json_name = "myField"` or auto-camel):**

```json
{
  "components": {
    "schemas": {
      "HelloRequest": {
        "type": "object",
        "properties": {
          "yourName": { "type": "string" },
          "greetingType": { "$ref": "#/components/schemas/Greeting" }
        }
      }
    }
  }
}
```

**After (`preserveProtoFieldNames = true`):**

```json
{
  "components": {
    "schemas": {
      "HelloRequest": {
        "type": "object",
        "properties": {
          "your_name": { "type": "string" },
          "greeting_type": { "$ref": "#/components/schemas/Greeting" }
        }
      }
    }
  }
}
```

**Interaction with `json_name` overrides.** When a field has an explicit `json_name` proto option
(e.g., `string greeting_used = 3 [(json_name) = "greeting"]`), `preserve_proto_field_names` still
overrides it back to the raw proto name (`greeting_used`).

**Test cases.**

| # | Envoy config | Input request | Expected behaviour |
|---|---|---|---|
| T1 | `preserve_proto_field_names = true` | Schema-level check only | All property keys in compiled OAS match raw proto names (snake_case) |
| T2 | `preserve_proto_field_names = true` | `POST /hello` with body `{"your_name":"World"}` | Envoy accepts `your_name` and returns response with `greeting_used` key |
| T3 | `preserve_proto_field_names = true` | `POST /hello` with body `{"yourName":"World"}` | Envoy rejects (camelCase no longer accepted) |
| T4 | default (false) | `POST /hello` with body `{"yourName":"World"}` | Envoy accepts; response uses `greeting` (per explicit `json_name`) |
| T5 | `preserve_proto_field_names = true` | field with explicit `json_name` annotation | Compiled schema uses raw proto name, not `json_name`; Envoy also uses raw name |

A new `PreserveProtoFieldNamesTest` should follow the same pattern as
`AlwaysPrintEnumsAsIntsTest`, with:
- An Envoy-integration context confirming T2 and T3 at runtime
- A compiled-OAS context comparing the output against a reference snapshot
  (`*.PreserveProtoFieldNamesTest.json`)

---

### 2. `print_options.always_print_primitive_fields`

**Status: not yet implemented**

**What it does.** By default, proto3 JSON omits primitive fields whose value equals the type
default (`0`, `""`, `false`, etc.). With `always_print_primitive_fields = true`, every primitive
field appears in every response, even when zero/empty/false.

**Required compiler changes.**

The change is response-schema-only (this is a print/serialization option, not a parse option, so
request schemas are unaffected).

Add a plugin option (e.g., `alwaysPrintPrimitiveFields: Boolean = false`). When true, mark every
primitive field (integers, floats, booleans, strings, bytes — but NOT messages, enums, or repeated
fields) as `required` in response schemas (i.e., in `components/schemas` and any inline response
body schemas).

**Before (default — no `required` array, fields may be absent):**

```json
{
  "HelloResponse": {
    "type": "object",
    "properties": {
      "greeting": { "type": "string" },
      "count":    { "type": "integer", "format": "int32" }
    }
  }
}
```

**After (`always_print_primitive_fields = true` — primitives always present):**

```json
{
  "HelloResponse": {
    "type": "object",
    "properties": {
      "greeting": { "type": "string" },
      "count":    { "type": "integer", "format": "int32" }
    },
    "required": ["greeting", "count"]
  }
}
```

Non-primitive fields (messages, enums, repeated, map) retain their existing optionality because the
Envoy option only governs primitive field emission.

**Test cases.**

| # | Envoy config | Scenario | Expected behaviour |
|---|---|---|---|
| T1 | `always_print_primitive_fields = true` | gRPC returns a response where `count = 0` | JSON response body contains `"count": 0` (not omitted) |
| T2 | default (false) | gRPC returns response where `count = 0` | JSON response body omits `count` key entirely |
| T3 | `always_print_primitive_fields = true` | Compiled OAS | All primitive fields in response schemas appear in the `required` array |
| T4 | default (false) | Compiled OAS | No `required` entries added for primitive fields |
| T5 | `always_print_primitive_fields = true` | Compiled OAS | Message-type fields and enum fields are NOT added to `required` |

A new `AlwaysPrintPrimitiveFieldsTest` should:
- Use a proto with both primitive and message-type fields in the response
- Confirm runtime Envoy behaviour for T1/T2
- Diff compiled OAS against a reference snapshot

---

### 3. `print_options.always_print_enums_as_ints`

**Status: implemented** — compiler option `enumValueFormat = NUMERIC_VALUE`

**What it does.** Enums are serialized as integers in both requests and responses instead of string
names. Integer 0 (the default value) is omitted from responses by default (unless combined with
`always_print_primitive_fields`).

**Existing compiler behaviour.** `PathsBuilder.buildEnumSchema()` emits `{"type": "integer"}`
with integer `enum` values when `enumValueFormat = NUMERIC_VALUE`. Covered by
`AlwaysPrintEnumsAsIntsTest`.

**Gaps / remaining test cases.**

| # | Scenario | Expected behaviour |
|---|---|---|
| T1 | `alwaysPrintEnumsAsInts = true` + `alwaysPrintPrimitiveFields = false` | Enum value `0` absent from response; OAS schema is integer with no `required` |
| T2 | `alwaysPrintEnumsAsInts = true` + `alwaysPrintPrimitiveFields = true` | Enum value `0` present in response; OAS adds enum field to `required` |
| T3 | Nested message containing an enum | Enum schema ref resolves to integer type throughout component hierarchy |

---

### 4. `case_insensitive_enum_parsing`

**Status: implemented** — compiler option `enumValueFormat = LOWER_CASE`

**What it does.** Envoy accepts enum values in any case when parsing requests (`STATUS_ACTIVE`,
`status_active`, `Status_Active` all map to the same value). The canonical serialization in
responses is unaffected (still uppercase string names, or integers if `alwaysPrintEnumsAsInts` is
set).

**Existing compiler behaviour.** `enumValueFormat = LOWER_CASE` emits lowercase strings in the
schema. Covered by `CaseInsensitiveEnumParsingTest`.

**Gaps / remaining test cases.**

| # | Scenario | Expected behaviour |
|---|---|---|
| T1 | `caseInsensitiveEnumParsing = true` | OAS request schema enum values are lowercase; e.g. `["greeting_unspecified", "greeting_hello", "greeting_hi"]` |
| T2 | `caseInsensitiveEnumParsing = true`, mixed-case request | Envoy accepts; response is still uppercase canonical string |
| T3 | `caseInsensitiveEnumParsing = true` + `alwaysPrintEnumsAsInts = true` | Request schema: integers; response schema: integers; case-insensitivity is moot for int parsing |

---

### 5. `auto_mapping`

**Status: implemented** — compiler option `autoMapping = true`

**What it does.** When `auto_mapping = true`, every gRPC method in the transcoded services that
lacks an explicit `google.api.http` annotation is automatically mapped to:

```
POST /<package>.<ServiceName>/<MethodName>
body: *
```

This is equivalent to adding the following annotation to every otherwise-unmapped method:

```protobuf
option (google.api.http) = {
  post: "/package.ServiceName/MethodName"
  body: "*"
};
```

**Required compiler changes.**

Add a plugin option `autoMapping: Boolean = false`. When true, the compiler must include in
`paths` any service method that does not already carry a `google.api.http` annotation, synthesising
a path item:

- HTTP method: `POST`
- Path: `/<fully.qualified.ServiceName>/<MethodName>`
- Request body: the full input message schema (same as `body: "*"`)
- Response: the full output message schema

This requires detecting which methods have HTTP annotations and generating path items for the
remainder. The synthesised paths should use the same tagging and schema-ref conventions as
explicitly-annotated paths.

**Before (no annotation on `Ping`, `auto_mapping = false`):**

```json
{
  "paths": {
    "/hello": { "post": { ... } }
  }
}
```

**After (`auto_mapping = true`, `Ping` method lacks annotation):**

```json
{
  "paths": {
    "/hello": { "post": { ... } },
    "/engine.protoc.openapi.example.envoy.HelloService/Ping": {
      "post": {
        "requestBody": {
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/PingRequest" } } }
        },
        "responses": {
          "200": {
            "content": { "application/json": { "schema": { "$ref": "#/components/schemas/PingResponse" } } }
          }
        }
      }
    }
  }
}
```

**Test cases.**

| # | Envoy config | Proto setup | Expected behaviour |
|---|---|---|---|
| T1 | `autoMapping = true` | Method with no `google.api.http` annotation | Compiled OAS contains synthesised POST path; Envoy routes it |
| T2 | `autoMapping = true` | Method with explicit `google.api.http` annotation | Explicit annotation wins; no duplicate path |
| T3 | `autoMapping = false` (default) | Method with no `google.api.http` annotation | Method absent from compiled OAS |
| T4 | `autoMapping = true` | Service with mix of annotated and unannotated methods | Both appear; annotated method at explicit path, unannotated at auto path |
| T5 | `autoMapping = true`, runtime | `POST /package.Service/Method` with full body JSON | Envoy transcodes and routes to gRPC handler |

---

### 6. `convert_grpc_status`

**Status: not yet implemented**

**What it does.** When `convert_grpc_status = true`, Envoy translates gRPC error trailers into a
JSON response body shaped as `google.rpc.Status`:

```json
{
  "code": 5,
  "message": "not found",
  "details": [{ "@type": "type.googleapis.com/google.rpc.RequestInfo", "requestId": "r-1" }]
}
```

The HTTP status code is mapped from the gRPC status code (`NOT_FOUND` → 404, etc.).

**Required compiler changes.**

Add a plugin option `convertGrpcStatus: Boolean = false`. When true:

1. Add a reusable `google.rpc.Status` schema to `components/schemas`:

```json
"google.rpc.Status": {
  "type": "object",
  "properties": {
    "code":    { "type": "integer", "format": "int32" },
    "message": { "type": "string" },
    "details": {
      "type": "array",
      "items": { "type": "object" }
    }
  }
}
```

2. Add error responses to every operation's `responses` map. At minimum, a default error entry:

```json
"responses": {
  "200": { ... },
  "default": {
    "description": "Error",
    "content": {
      "application/json": {
        "schema": { "$ref": "#/components/schemas/google.rpc.Status" }
      }
    }
  }
}
```

Alternatively (for richer OAS tooling), emit per-gRPC-status-code HTTP equivalents
(`400`, `401`, `403`, `404`, `409`, `429`, `500`, `503`) if desired.

**Test cases.**

| # | Envoy config | Scenario | Expected behaviour |
|---|---|---|---|
| T1 | `convertGrpcStatus = true` | gRPC method returns `NOT_FOUND` status | HTTP 404 with JSON body `{"code":5,"message":"..."}` |
| T2 | `convertGrpcStatus = true` | gRPC method returns `INVALID_ARGUMENT` with detail | HTTP 400; body contains `details` array with typed entry |
| T3 | `convertGrpcStatus = false` (default) | gRPC method returns error | gRPC trailers pass through; no JSON error body |
| T4 | `convertGrpcStatus = true` | Compiled OAS | `google.rpc.Status` component present; all operations reference it in error responses |
| T5 | `convertGrpcStatus = true` | Compiled OAS | Non-error (200) responses are unchanged |

---

### 7. `print_options.stream_newline_delimited`

**Status: not yet implemented**

**What it does.** For server-streaming gRPC methods, responses are normally returned as a
comma-separated JSON array (`[{msg1},{msg2}]`). With `stream_newline_delimited = true`, each
message is emitted as a separate JSON object on its own line with no surrounding array
(`{msg1}\n{msg2}\n`). The content type becomes `application/x-ndjson`.

**Required compiler changes.**

Add a plugin option `streamNewlineDelimited: Boolean = false`. For any operation backed by a
server-streaming method, change the response `content` entry from:

```json
"200": {
  "content": {
    "application/json": {
      "schema": {
        "type": "array",
        "items": { "$ref": "#/components/schemas/HelloResponse" }
      }
    }
  }
}
```

to:

```json
"200": {
  "content": {
    "application/x-ndjson": {
      "schema": { "$ref": "#/components/schemas/HelloResponse" }
    }
  }
}
```

When `stream_sse_style_delimited = true` (see §8), it overrides this setting.

**Test cases.**

| # | Envoy config | Scenario | Expected behaviour |
|---|---|---|---|
| T1 | `streamNewlineDelimited = true` | Compiled OAS, streaming method | Response content-type is `application/x-ndjson`; schema is single message ref (no array wrapper) |
| T2 | default (false) | Compiled OAS, streaming method | Response content-type is `application/json`; schema is `array` of message refs |
| T3 | `streamNewlineDelimited = true` | Compiled OAS, unary method | Unary response schema unchanged (still `application/json`, not ndjson) |
| T4 | `streamNewlineDelimited = true` + `streamSseStyleDelimited = true` | Compiled OAS | SSE takes precedence; `text/event-stream` used (see §8) |
| T5 | `streamNewlineDelimited = true` | Runtime: stream 3 messages | Response body is three newline-separated JSON objects, not an array |

---

### 8. `print_options.stream_sse_style_delimited`

**Status: not yet implemented**

**What it does.** When `stream_sse_style_delimited = true`, each streamed message is emitted using
Server-Sent Events framing:

```
data: {"greeting":"Hello, World!","greetingUsed":1}\n\n
data: {"greeting":"Hi, World!","greetingUsed":2}\n\n
```

The content type is `text/event-stream`. This option overrides `stream_newline_delimited`.

**Required compiler changes.**

Add a plugin option `streamSseStyleDelimited: Boolean = false`. This takes precedence over
`streamNewlineDelimited`. For streaming method responses, emit:

```json
"200": {
  "content": {
    "text/event-stream": {
      "schema": { "$ref": "#/components/schemas/HelloResponse" }
    }
  }
}
```

**Test cases.**

| # | Envoy config | Scenario | Expected behaviour |
|---|---|---|---|
| T1 | `streamSseStyleDelimited = true` | Compiled OAS, streaming method | Response content-type is `text/event-stream`; schema is single message ref |
| T2 | `streamSseStyleDelimited = true` + `streamNewlineDelimited = true` | Compiled OAS | SSE wins; `text/event-stream` used |
| T3 | `streamSseStyleDelimited = true` | Compiled OAS, unary method | Unary response unchanged |
| T4 | `streamSseStyleDelimited = true` | Runtime: stream 3 messages | Each message prefixed `data: `, separated by `\n\n` |
| T5 | `streamSseStyleDelimited = false`, `streamNewlineDelimited = false` | Compiled OAS, streaming method | Default `application/json` array schema |

---

## Options With No Meaningful OAS Schema Impact

The following options change Envoy's runtime routing or validation behaviour but do not affect
the shape of request or response JSON schemas. They need not influence compiler output.

| Option | Why no schema change |
|---|---|
| `print_options.add_whitespace` | Cosmetic JSON formatting; schema is content-agnostic |
| `ignored_query_parameters` | Runtime routing hint; the query params remain documented in OAS paths |
| `ignore_unknown_query_parameters` | Silently drops unknown params at runtime; doesn't change what's documented |
| `capture_unknown_query_parameters` | Runtime capture into extension field; not a schema concern |
| `query_param_unescape_plus` | Changes `+`→space decoding; no schema type or name change |
| `url_unescape_spec` | Controls percent-decoding of path segments; no schema change |
| `match_incoming_request_route` | Routing behaviour after transcoding; invisible to schema |
| `match_unregistered_custom_verb` | URL suffix matching; custom verb routes come from `google.api.http` annotations, not this flag |
| `request_validation_options.reject_unknown_method` | Returns 404 instead of pass-through; error behaviour only |
| `request_validation_options.reject_unknown_query_parameters` | Returns 400 for unknown params; schema already only documents known params |
| `request_validation_options.reject_binding_body_field_collisions` | Validation strictness for binding conflicts; no schema change |
| `max_request_body_size` | Runtime size limit (→ 413); no schema impact |
| `max_response_body_size` | Runtime size limit (→ 500); no schema impact |

---

## Implementation Priority Summary

| Priority | Option | Compiler option name (proposed) | Effort | Completed |
|---|---|---|---|:---:|
| 1 | `preserve_proto_field_names` | `preserveProtoFieldNames` | Medium — field name lookup change throughout SchemaBuilder + PathsBuilder | [x] |
| 2 | `always_print_primitive_fields` | `alwaysPrintPrimitiveFields` | Small — add `required` array logic in SchemaBuilder for primitive fields | [x] |
| 3 | `auto_mapping` | `autoMapping` | Medium — PathsBuilder must detect unannotated methods and synthesise paths | [x] |
| 4 | `convert_grpc_status` | `convertGrpcStatus` | Small — inject `google.rpc.Status` schema + error response on every operation | [ ] |
| 5 | `stream_newline_delimited` | `streamNewlineDelimited` | Small — change content-type + schema shape for streaming responses | [ ] |
| 6 | `stream_sse_style_delimited` | `streamSseStyleDelimited` | Small — same as above, takes precedence over `streamNewlineDelimited` | [ ] |

Items 1–2 affect request/response schemas broadly and are the highest client-visible risk.  
Items 3–4 add new content rather than changing existing shapes.  
Items 5–6 are narrow (streaming methods only) and can be tackled together.

`always_print_enums_as_ints` and `case_insensitive_enum_parsing` are already implemented and
tested; they are listed under §3 and §4 only to document known gaps in their test coverage.
