# Examples

Each example demonstrates how a specific set of compiler options and engine annotations affects the generated OpenAPI output.
Every example pairs a self-contained `.proto` source with a checked-in reference OAS document, so you can read either side and see exactly what input produced what output.

Browse the examples below by the feature you are trying to use.
The proto sources also serve as executable, copy-paste-ready illustrations of how to write the annotations.

## By Example

### [conventions](src/conventions/README.md)

A minimal example based on the [helloworld Greeter service](https://grpc.io/docs/what-is-grpc/introduction/) from the gRPC introduction.
Uses only `google.api.http` annotations — no engine annotations — to show the baseline conventions the plugin derives from plain HTTP binding rules: path parameter inference from URL templates, `response_body` field extraction, and service-derived `info` metadata.

### [petstore](src/petstore/README.md)

A re-expression of the canonical [Swagger Petstore](https://github.com/swagger-api/swagger-petstore) spec in protobuf.
Demonstrates multi-service files, OAuth2 and API key security schemes, response headers, extensions, and tags with external documentation.
Useful as a real-world baseline showing how a well-known API shape maps into engine annotations.

### [complete](src/complete/README.md)

A purpose-built exhaustive exercise of every annotation field the plugin supports.
Not a realistic API — it is a structured inventory of features: the full `SchemaObject` keyword set, all component types, all security scheme types, composition keywords, JSON Schema referencing keywords, encoding objects, and every parameter/response/link variant.
Also demonstrates `outputFormat = YAML` — the plugin generates a `.openapi.yaml` file instead of `.openapi.json`.
If you want to know whether a specific annotation field is wired correctly, look here.

### [merged](src/merged/README.md)

Demonstrates `merge = true` mode: services spread across multiple proto files are combined into a single OpenAPI document.
Also shows how a metadata-only proto file (no service) contributes `info` and `servers` to the merged output, and how `response_body` on `google.api.http` changes the response schema.

### [unmerged](src/unmerged/README.md)

Demonstrates `merge = false` (per-service) mode: each service produces its own OpenAPI document, plus one aggregate document for the package.
Also shows service-level annotation overrides (per-service `info.version`).

### [version](src/version/README.md)

Demonstrates the `version` option: a nullable string written to `info.version` of every document that does not already have a version from an engine annotation.
Covers all four combinations of options-version present/absent and annotation-version present/absent, illustrating the priority layering that lets annotation-pinned versions always win over the global option.

### [namespacing](src/namespacing/README.md)

Demonstrates the four `schemaNamespace*` options that control how proto package information is incorporated into `components/schemas` keys.
Uses two proto packages (`catalog.v1` and `inventory.v2`) that both define an `Item` message — a collision that `NONE` (the default) cannot resolve.
Three reference compilations cover `FULL_PACKAGE`, `SIMPLIFIED_PACKAGE + CAPITALIZED`, and `SIMPLIFIED_PACKAGE + CAPITALIZED + versionExtraction`.

### [filtering](src/filtering/README.md)

Demonstrates the `serviceInclude` and `serviceExclude` regex options that control which services appear in the generated output.
Uses a single proto file with three services (`AlphaService`, `BetaService`, `GammaService`), each owning distinct request/response message types, so that schema suppression is verifiable alongside path suppression.
Covers default pass-all behavior, include/exclude by name substring, regex alternation, anchored full-FQN match, and filtering in `merge = false` (per-service) mode.

### [envoy](src/envoy/README.md)

Demonstrates every compiler option that mirrors an Envoy [`GrpcJsonTranscoder`](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto) option.
Each example maps one Envoy option to the corresponding compiler option and confirms — against a live Envoy container — that the generated OAS describes what Envoy actually emits.
Covers `preserve_proto_field_names`, `always_print_primitive_fields`, `always_print_enums_as_ints`, `case_insensitive_enum_parsing`, `auto_mapping`, `convert_grpc_status`, `stream_newline_delimited`, and `stream_sse_style_delimited`, including selected two-option combinations.

### [responseBodyError](src/responseBodyError/README.md)

Demonstrates the compiler's error behavior when `response_body` or `body` annotations name a field that does not exist on the corresponding message.
Useful for confirming what a compile error looks like when an annotation is wrong.