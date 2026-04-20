# Examples

The `examples` subproject is the acceptance test suite for `protoc-gen-openapi`. Each example is a self-contained Gradle test suite that compiles real `.proto` files through the plugin and asserts that the generated OpenAPI output matches a checked-in reference file.

Examples serve two purposes:

1. **Regression protection** — if a change to the plugin alters generated output, the corresponding test fails and the diff shows exactly what changed.
2. **Feature documentation** — each proto file is an authoritative, executable illustration of how to use a particular set of annotations or of how the plugin handles invalid input.

## How examples work

Each test suite:

1. Runs `protoc` with the `recorder` plugin (a native binary built from `protoc-utils-recorder`) to capture the raw `CodeGeneratorRequest` as a `.binpb` file.
2. Loads that `.binpb` at test time and feeds it to `ProtocGenOpenAPI.compile()`.
3. For happy-path examples, compares the output against reference JSON/YAML files stored in `src/<suite>/resources/`. For error-case examples, asserts that compilation fails with an informative error instead.

Tests use [kotest](https://kotest.io) `FunSpec` style with `assertSoftly` enabled so all assertions are evaluated even on failure.

## Test suites

### [petstore](src/petstore/README.md)

A re-expression of the canonical [Swagger Petstore](https://github.com/swagger-api/swagger-petstore) spec in protobuf. Covers multi-service files, OAuth2 and API key security schemes, response headers, extensions, and tags with external documentation. Useful as a real-world baseline: if it breaks, the plugin has regressed on a well-known API shape.

### [complete](src/complete/README.md)

A purpose-built exhaustive exercise of every annotation field the plugin supports. Not a realistic API — it is a structured inventory of features. Covers the full `SchemaObject` keyword set, all component types, all security scheme types, composition keywords, JSON Schema referencing keywords, encoding objects, and every parameter/response/link variant. Also demonstrates `outputFormat = YAML` — the plugin generates a `.openapi.yaml` file instead of `.openapi.json`. If you want to know whether a specific annotation field is wired correctly, look here.

### [merged](src/merged/README.md)

Demonstrates `merge = true` mode: services spread across multiple proto files are combined into a single OpenAPI document. Also shows how a metadata-only proto file (no service) contributes `info` and `servers` to the merged output, and how `response_body` on `google.api.http` changes the response schema.

### [unmerged](src/unmerged/README.md)

Demonstrates `merge = false` (per-service) mode: each service produces its own OpenAPI document, plus one aggregate document for the package. Also shows service-level annotation overrides (per-service `info.version`) and parameterized test validation across multiple output files.

### [responseBodyError](src/responseBodyError/README.md)

An error-case test: verifies that `response_body` and `body` annotations whose field names do not exist on the corresponding message produce explicit compile errors rather than silently emitting a broken spec. Has no reference output files — it asserts that compilation fails and that the error message names the bad field.

### [conventions](src/conventions/README.md)

A minimal example based on the [helloworld Greeter service](https://grpc.io/docs/what-is-grpc/introduction/) from the gRPC introduction. Uses only `google.api.http` annotations — no engine annotations — to show the baseline conventions the plugin derives from plain HTTP binding rules: path parameter inference from URL templates, `response_body` field extraction, and service-derived `info` metadata.

### [version](src/version/README.md)

Exercises the `options.version` fallback: a nullable string written to `info.version` of every document that does not already have a version from an engine annotation. Covers all four combinations of options version present/absent × annotation version present/absent, and documents the priority layering that lets annotation-pinned versions always win over the global option.

### [namespacing](src/namespacing/README.md)

Exercises the four `schemaNamespace*` options that control how proto package information is incorporated into `components/schemas` keys. Uses two proto packages (`catalog.v1` and `inventory.v2`) that both define an `Item` message — a collision that `NONE` (the default) cannot resolve. Three reference compilations cover `FULL_PACKAGE`, `SIMPLIFIED_PACKAGE + CAPITALIZED`, and `SIMPLIFIED_PACKAGE + CAPITALIZED + versionExtraction`.

## Adding a new example

1. Create `src/<name>/proto/` with your `.proto` files.
2. Add a test suite block in `build.gradle.kts` following the pattern of the existing suites.
3. Run `./gradlew :protoc-gen-openapi-examples:generate<Name>Proto` to produce the `code-generator-request.binpb`, then run the test once to generate initial output.
4. Copy the generated JSON/YAML into `src/<name>/resources/` as the reference file.
5. Write a `src/<name>/README.md` describing what the example exercises.