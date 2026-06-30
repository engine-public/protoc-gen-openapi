# protoc-gen-openapi

A `protoc` compiler plugin that converts gRPC service definitions (protobuf) into [OpenAPI v3.1](https://spec.openapis.org/oas/v3.1.0) specifications.
Designed to pair with Envoy's [`GrpcJsonTranscoder`](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto): every compiler option that affects wire format has a corresponding Envoy filter option, so the generated OAS document describes what Envoy will actually emit.
The plugin compiles to a native binary via GraalVM so it can be used directly in a `protoc` invocation without a JVM on `PATH`.

## Subprojects

| path | description |
|---|---|
| **root** (`src/`) | The plugin executable. Reads `CodeGeneratorRequest` from stdin and writes `CodeGeneratorResponse` to stdout, per the protoc plugin protocol. |
| [`model/`](model/README.md) | Protobuf definitions for the full OpenAPI v3.1 spec, plus the engine annotation extensions (`annotations.proto`, `openapi.proto`). These are the types the plugin reads from annotated `.proto` files. |
| [`examples/`](examples/README.md) | Acceptance test suite. Each example is also an executable illustration of how to use a particular set of annotations or compiler options. |

## Usage

The plugin runs in two modes that can be mixed within a single proto file:

- **Convention-based** — no engine annotations required.
  The plugin derives operations, path parameters, and request/response shapes from standard [`google.api.http`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto) bindings (or via `autoMapping=true`), and pulls `info.title` from the service name and `info.version` from the `version` option.
  See the [`conventions` example](examples/src/conventions/README.md) for a complete working sample.
- **Annotation-based** — import [`engine/protoc/openapi/annotations.proto`](model/src/main/proto/engine/protoc/openapi/annotations.proto) and attach engine annotations to add or override anything the conventions can't express: explicit `info`, tags, security schemes, per-field schema constraints, etc.
  See the [model README](model/README.md#annotations) for the full annotation list and the [examples](examples/README.md) for richer illustrations.

The plugin executable must be `protoc-gen-openapi` on `PATH` (or pointed at explicitly — see the Gradle examples below).
Options are passed as `--openapi_out=<comma-separated-options>:<outdir>`.

### Gradle

Configure the [`protobuf-gradle-plugin`](https://github.com/google/protobuf-gradle-plugin) to invoke `protoc-gen-openapi` as a code-generation plugin.

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.6"
}

// optional: include the annotations in your project
dependencies {
    protobuf("com.engine:protoc-gen-openapi-model:<version>")
}

protobuf {
    plugins {
        create("openapi") {
            // path to the native binary; or set `artifact = ...` once the published artifact is available
            artifact = "com.engine:protoc-gen-openapi:<version>"
        }
    }
    generateProtoTasks {
        all().all {
            plugins {
                create("openapi") {
                    option("version=1.0.0")
                    option("autoTagServices=true")
                    // other options as desired
                }
            }
        }
    }
}
```

### Bash

```bash
# optional one-time: download and extract the model protos
curl -L -o protoc-gen-openapi-model-protos.zip \
  https://github.com/engine-public/protoc-gen-openapi/releases/download/<version>/protoc-gen-openapi-model-protos.zip
unzip -d build/proto/openapi-model protoc-gen-openapi-model-protos.zip

protoc \
  --proto_path=optional/path/to/extracted/model/protos \
  --proto_path=path/to/your/protos \
  --openapi_out=autoTagServices=true,otherOptionsAsDesired:./build/openapi \
  src/main/proto/example/v1/service.proto
```

## Compiler Options

All options are defined on [`ProtocGenOpenAPI.Options`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt).
Click the option name to jump to its KDoc for full semantics, precedence rules, and Envoy interoperability notes.

| name | type | default | summary |
|---|---|---|---|
| [`alwaysPrintPrimitiveFields`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L266) | boolean | `false` | Add every non-repeated, non-message field to `required`. Pair with Envoy's `always_print_primitive_fields`. |
| [`autoMapping`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L251) | boolean | `false` | Auto-map gRPC methods without a `google.api.http` annotation to `POST /<package>.<ServiceName>/<MethodName>`. Mirrors Envoy's `auto_mapping`. |
| [`autoTagServices`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L116) | boolean | `false` | Tag every operation with its enclosing service name and emit a top-level `tags` entry per service using the service's proto comment as the description. |
| [`convertGrpcStatus`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L309) | boolean | `false` | Add a `"default"` response entry to every operation whose JSON body is an inline `google.rpc.Status` schema. Mirrors Envoy's `convert_grpc_status`. |
| [`enumValueFormat`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L232) | enum | `CANONICAL` | How enum values are written into OAS `enum` arrays: `CANONICAL`, `NUMERIC_VALUE`, or `LOWER_CASE`. Pair with Envoy's `always_print_enums_as_ints` / `case_insensitive_enum_parsing`. |
| [`inlineEnums`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L180) | boolean | `false` | Emit enum values inline at every reference instead of as a shared `$ref` in `components/schemas`. |
| [`inlineRequestSchemas`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L198) | boolean | `true` | Global default for inlining each RPC's request body schema at the use site. Per-method `inline_request` annotation overrides. |
| [`inlineResponseSchemas`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L210) | boolean | `true` | Global default for inlining each RPC's response body schema at the use site. Per-method `inline_response` annotation overrides. |
| [`logFile`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L365) | string | — | File path the SLF4J binding writes records to. When unset, records go to standard error. |
| [`logLevel`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L353) | enum | `ERROR` | SLF4J threshold (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`) applied to every logger the plugin and its dependencies create. |
| [`merge`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L36) | boolean | `false` | Combine every service across every target file into a single OpenAPI document instead of one document per service. |
| [`outputFormat`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L99) | enum | `JSON` | Serialization format of generated documents: `JSON` (default) or `YAML`. |
| [`preserveProtoFieldNames`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L285) | boolean | `false` | Use raw proto field names (e.g. `my_field`) as schema property keys instead of `json_name` or lowerCamelCase. Pair with Envoy's `preserve_proto_field_names`. |
| [`referenceLinkTarget`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L439) | enum | `NONE` | Renderer dialect that CommonMark reference links (`[Type]`, `[Service.Method]`) in `description` fields resolve to: `NONE` (default — resolution off, brackets untouched), `SWAGGER_UI` (operations + tags), or `REDOC` (operations + tags + generated schema sections). When resolution is enabled, unresolved references are stripped of their brackets and rendered as an inline code span (`` `Property` ``), with a warning. |
| [`schemaNamespaceCasing`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L145) | enum | `NONE` | Case transformation applied to package segments of a namespaced schema key. `NONE`, `CAPITALIZED`, or `UPPER_CASE`. |
| [`schemaNamespaceSeparator`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L136) | enum | `NONE` | Separator placed between package segments of a namespaced schema key. `NONE`, `UNDERSCORE`, `DASH`, or `DOT`. |
| [`schemaNamespaceStrategy`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L129) | enum | `NONE` | Controls which package segments are prepended to schema keys in `components/schemas`. `NONE`, `FULL_PACKAGE`, or `SIMPLIFIED_PACKAGE`. |
| [`schemaNamespaceVersionExtraction`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L156) | boolean | `false` | Move package segments that look like proto API version identifiers (e.g. `v1`, `v2beta1`) to the end of the schema key. |
| [`serviceExclude`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L403) | regex | — | Exclude services whose fully-qualified name contains a match, even if they also matched `serviceInclude`. |
| [`serviceInclude`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L390) | regex | `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$` | Only include services whose fully-qualified name contains a match. Schemas referenced only by excluded services are also omitted. |
| [`setSchemaTitleToProtoSimpleName`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L169) | boolean | `false` | Add a `"title"` field to each schema in `components/schemas` set to the unqualified proto type name. |
| [`streamNewlineDelimited`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L324) | boolean | `false` | Document server-streaming responses with content-type `application/x-ndjson`. Mirrors Envoy's `stream_newline_delimited`. |
| [`streamSseStyleDelimited`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L339) | boolean | `false` | Document server-streaming responses with content-type `text/event-stream`. Mirrors Envoy's `stream_sse_style_delimited`. Takes precedence over `streamNewlineDelimited`. |
| [`suppressDefaultEnumValues`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L221) | boolean | `false` | Omit enum values whose proto number is `0` (the proto3 default value convention) from all OAS enum value lists. |
| [`validateOutput`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L68) | boolean | `false` | Validate each generated document against the official OAS 3.1.1 schema. Issues are logged at `WARN`. See [`validationErrorsAreFatal`](#L88) to also fail the compile. |
| [`validationErrorsAreFatal`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L88) | boolean | `false` | When `true`, validation issues are also written to the protoc error and fail the compile. No effect unless `validateOutput` is also `true`. |
| [`version`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L54) | string | — | Fallback `info.version` for documents whose annotations do not specify one. |

Enum-valued options accept their values case-insensitively.

## `google.api.http` body modes

The plugin reads the `body` field of every `google.api.http` annotation and maps it to OpenAPI as
specified in [`google/api/http.proto`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto)
— the same semantics enforced at runtime by Envoy's gRPC-JSON transcoder.  The three modes are
**identical across all five verbs** (GET, POST, PUT, PATCH, DELETE):

| `body` value | HTTP request body | URL query parameters |
|---|---|---|
| **unset / `""`** | none | every request field not bound to a `{var}` in the URL template |
| **`"*"`** | the whole request message | none |
| **`"<field_name>"`** | the value of that single top-level field | every other request field not bound to the URL template |

Auto-derived query parameters use the field's JSON name by default (or the snake_case proto name
when [`preserveProtoFieldNames`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt#L285)
is set), recurse into nested messages with dotted names (`?address.city=…`), and emit `style=form,
explode=true` for repeated scalars.  Repeated message and map fields are skipped with a `WARN` log
since OpenAPI has no faithful query-string representation for them.
Each parameter's schema carries the field's own `(engine.protoc.openapi.field)` annotation
(constraints, `format`, `enum`, …) merged on top of its proto-derived type, so a query parameter
advertises the same constraints the field declares everywhere else.

When a method also declares `(engine.protoc.openapi.parameters)` entries, those manual declarations
win for any field they cover and a `WARN` log records the overlap.
A field is treated as covered when a manual parameter's name matches its proto name, its JSON name,
or — for nested fields — its dotted name (`address.city`); manual entries that reference a reusable
component via `$ref` are matched by the referenced component's declared name.
Two coverage cases cannot be detected and may surface as duplicate parameters: a `$ref` the plugin
cannot resolve locally (a remote/relative `$ref`, or one whose component is itself a `$ref`), and a
nested field bound to the URL with a dotted placeholder such as `{address.city}` (the path-template
parser only recognises single-segment `{var}` placeholders).
Declare the overlapping field with an inline parameter, or move it into the request body, to avoid
the duplicate.
Worked examples live in [examples/src/envoy/](examples/src/envoy/) (live Envoy round-trip tests)
and [examples/src/complete/](examples/src/complete/) (snapshot coverage of every verb/body
combination, plus manual-override precedence).

## Related Projects

- [engine-public/protoc-utils](https://github.com/engine-public/protoc-utils) — shared protoc plugin utilities (descriptor wrappers, comment parsing, parameter handling) and the `recorder` plugin used by this project's example suite.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for build commands, the native-image / reflection metadata workflow, and the PR process.

