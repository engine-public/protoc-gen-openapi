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

A purpose-built exhaustive exercise of every annotation field the plugin supports. Not a realistic API — it is a structured inventory of features. Covers the full `SchemaObject` keyword set, all component types, all security scheme types, composition keywords, JSON Schema referencing keywords, encoding objects, and every parameter/response/link variant. If you want to know whether a specific annotation field is wired correctly, look here.

### [merged](src/merged/README.md)

Demonstrates `merge = true` mode: services spread across multiple proto files are combined into a single OpenAPI document. Also shows how a metadata-only proto file (no service) contributes `info` and `servers` to the merged output, and how `response_body` on `google.api.http` changes the response schema.

### [unmerged](src/unmerged/README.md)

Demonstrates `merge = false` (per-service) mode: each service produces its own OpenAPI document, plus one aggregate document for the package. Also shows service-level annotation overrides (per-service `info.version`) and parameterized test validation across multiple output files.

### [responseBodyError](src/responseBodyError/README.md)

An error-case test: verifies that `response_body` and `body` annotations whose field names do not exist on the corresponding message produce explicit compile errors rather than silently emitting a broken spec. Has no reference output files — it asserts that compilation fails and that the error message names the bad field.

## Adding a new example

1. Create `src/<name>/proto/` with your `.proto` files.
2. Add a test suite block in `build.gradle.kts` following the pattern of the existing suites.
3. Run `./gradlew :protoc-gen-openapi-examples:generate<Name>Proto` to produce the `code-generator-request.binpb`, then run the test once to generate initial output.
4. Copy the generated JSON/YAML into `src/<name>/resources/` as the reference file.
5. Write a `src/<name>/README.md` describing what the example exercises.