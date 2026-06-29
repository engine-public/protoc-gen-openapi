# Envoy GrpcJsonTranscoder

An integration test suite that validates the compiler's support for Envoy's
[`GrpcJsonTranscoder`](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto)
filter options. Each test class corresponds to one Envoy option (or a combination of options) and
exercises two layers:

1. **Runtime behavior** — a live Envoy container (via [Testcontainers](https://testcontainers.com))
   is configured with the option under test and sent real HTTP requests. These tests confirm that
   Envoy behaves as documented and that the option's runtime semantics match the assumptions behind
   the compiler output.
2. **OAS snapshot** — the compiler is run with the corresponding plugin option and the output is
   diffed against a checked-in reference JSON file. These tests confirm that the generated schema
   accurately reflects what Envoy will accept and produce at runtime.

## Proto

`hello.proto` declares a single service, `HelloService`, with three methods:

- **`SayHello`** — unary, bound to `POST /hello`. Used by most tests.
- **`StreamHellos`** — server-streaming, bound to `POST /hellos`. Used by the streaming tests.
- **`PingHello`** — unary, no `google.api.http` annotation. Used by the `auto_mapping` test.

`HelloResponse.greeting_used` carries an explicit `json_name = "greeting"` annotation. This field
appears throughout the tests because it demonstrates the interaction between `json_name` overrides,
`preserve_proto_field_names`, and `always_print_primitive_fields`.

## Test infrastructure

`EnvoyTestBase` sets up a gRPC server (`HelloServiceImpl`) and an Envoy container before each test
class runs, and tears them down after. Subclasses pass a `GrpcJsonTranscoder` configuration value
to the base class; the base class injects it into Envoy's filter chain from a YAML template.
`GrpcJsonTranscoder.kt` mirrors the Envoy proto in Kotlin so that the config can be serialized
directly to YAML.

Reference JSON files are stored in `resources/` and named `<output-file>.<TestClass>.json`.

## What it exercises

### `preserve_proto_field_names` → `preserveProtoFieldNames`

**`PreserveProtoFieldNamesTest`** — By default the transcoder uses the protobuf `json_name` value
(or lowerCamelCase) as the JSON key. With `preserve_proto_field_names = true`, Envoy uses the raw
proto snake\_case name for both requests and responses, overriding any explicit `json_name`
annotation. The test confirms that Envoy accepts snake\_case request bodies and returns
`reply_message` and `greeting_used` in responses (not `replyMessage` and `greeting`). The
corresponding compiler option (`preserveProtoFieldNames = true`) switches all property keys in
`components/schemas` and request/response bodies to use raw proto names, and updates `required`
arrays to match.

### `always_print_primitive_fields` → `alwaysPrintPrimitiveFields`

**`AlwaysPrintPrimitiveFieldsTest`** — Proto3 JSON omits fields whose value equals the type
default (`0`, `""`, `false`). With `always_print_primitive_fields = true`, every primitive field
appears in every response even when zero or empty. The test confirms that `greeting_used = 0`
(`GREETING_UNSPECIFIED`) appears in the response body and that `replyMessage` (an empty string) is
also present. The compiler option adds all primitive fields to the `required` array in response
schemas. Enum and message fields are not added to `required` because the Envoy option only governs
primitive emission.

### `always_print_enums_as_ints` → `enumValueFormat = NUMERIC_VALUE`

**`AlwaysPrintEnumsAsIntsTest`** — Enums are normally serialized as uppercase string names in
proto3 JSON. With `always_print_enums_as_ints = true`, Envoy serializes enum values as integers in
responses, and accepts both integers and canonical uppercase strings in requests (but not
non-canonical casing). Integer `0` (the proto3 default) is omitted from responses unless
`always_print_primitive_fields` is also set. The compiler maps this to `enumValueFormat =
NUMERIC_VALUE`, which produces `{"type": "integer", "enum": [0, 1, 2]}` instead of a string enum.

**`AlwaysPrintEnumsAsIntsPrimitiveFieldsTest`** — Exercises the combination of
`alwaysPrintEnumsAsInts = true` and `alwaysPrintPrimitiveFields = true`. With both enabled, Envoy
returns integer `0` in the response for a `GREETING_UNSPECIFIED` input. The compiler emits
`enumValueFormat = NUMERIC_VALUE` alongside `alwaysPrintPrimitiveFields = true`, which adds enum
fields to the `required` array in addition to producing an integer enum schema.

### `case_insensitive_enum_parsing` → `enumValueFormat = LOWER_CASE`

**`CaseInsensitiveEnumParsingTest`** — With `case_insensitive_enum_parsing = true`, Envoy accepts
enum values in any casing in request bodies (`GREETING_HELLO`, `greeting_hello`, and
`Greeting_Hello` all map to the same value). Responses always use the canonical uppercase string
form. The compiler maps this to `enumValueFormat = LOWER_CASE`, emitting lowercase string values in
the enum schema (`["greeting_unspecified", "greeting_hello", "greeting_hi"]`) to match what
case-insensitive clients typically send.

**`CaseInsensitiveEnumParsingAndAlwaysPrintAsIntsTest`** — Exercises the combination of
`caseInsensitiveEnumParsing = true` and `alwaysPrintEnumsAsInts = true`. Envoy accepts both
integers and any-case strings as input but always returns integers. The compiler option for this
combination is `enumValueFormat = NUMERIC_VALUE`; case-insensitivity is irrelevant for integer
inputs, so no separate compiler option is needed.

### `auto_mapping` → `autoMapping`

**`AutoMappingTest`** — Without `google.api.http` annotations, a gRPC method is normally invisible
to the transcoder. With `auto_mapping = true`, Envoy synthesizes a `POST
/<package>.<Service>/<Method>` route for every unannotated method. The test confirms that
`/engine.protoc.openapi.example.envoy.HelloService/PingHello` routes correctly at runtime. The
compiler option (`autoMapping = true`) adds the synthesized path to the OAS output using the same
schema refs as explicitly annotated methods.

### `convert_grpc_status` → `convertGrpcStatus`

**`ConvertGrpcStatusTest`** — With `convert_grpc_status = true`, Envoy translates gRPC error
trailers into a JSON body shaped as `google.rpc.Status` (`{"code": 5, "message": "..."}`) and maps
gRPC status codes to HTTP status codes (`NOT_FOUND` → 404). The test confirms that a gRPC
`NOT_FOUND` error produces an HTTP 404 with the expected JSON body. The compiler injects a
`default` error response entry on every operation whose JSON body is an inline `google.rpc.Status`
schema — the envelope is never added to `components/schemas`.

### `stream_newline_delimited` → `streamNewlineDelimited`

**`StreamNewlineDelimitedTest`** — Server-streaming responses are normally returned as a
comma-separated JSON array. With `stream_newline_delimited = true`, each message is emitted on its
own line with no surrounding array (`{msg1}\n{msg2}\n`). The test confirms that `/hellos` returns
three newline-separated JSON objects and that the unary `/hello` endpoint is unaffected. The
compiler changes the response content type for streaming methods from `application/json` (with an
array schema) to `application/x-ndjson` (with a single-message schema). Unary methods are
unchanged.

> **Note:** `streamSseStyleDelimited` takes precedence. If both options are `true`, this option has
> no effect — the compiler emits `text/event-stream`, not `application/x-ndjson`. Setting both is
> a misconfiguration; enable at most one.

### `stream_sse_style_delimited` → `streamSseStyleDelimited`

**`StreamSseStyleDelimitedTest`** — With `stream_sse_style_delimited = true`, each streamed message
is framed as a Server-Sent Events chunk (`data: <json>\n\n`). This option takes precedence over
`stream_newline_delimited` when both are set; the compiler emits `text/event-stream` (not
`application/x-ndjson`) as the content type for streaming method responses and uses a
single-message schema rather than an array. The test confirms that `/hellos` returns `data: `
prefixed lines and that enabling both SSE and ndjson options still produces `text/event-stream`
content.

## Peculiarities

The Envoy container runs version **v1.35.0**. The `stream_sse_style_delimited` option was added to
Envoy after v1.33 and requires v1.35.0 or later.

`HelloResponse.greeting_used` has an explicit `json_name = "greeting"` annotation. This means the
default OAS output uses `"greeting"` as the property key, but `preserveProtoFieldNames = true`
overrides it back to `"greeting_used"`. Both the runtime test (Envoy response key) and the compiler
snapshot verify this override consistently.

Proto3 default-value omission — the fact that a field set to `0`, `""`, or `false` is absent from
JSON responses by default — is exercised repeatedly across the enum and primitive-fields tests. The
same field (`greeting_used = GREETING_UNSPECIFIED = 0`) is used as the canonical "absent by
default" case throughout.
