# Contributing

## Prerequisites

- GraalVM 21 (the Gradle toolchain spec pins `JvmVendorSpec.GRAAL_VM`; install via SDKMAN or the [GraalVM downloads page](https://www.graalvm.org/downloads/))
- A POSIX shell environment

The version of the produced artifacts is read from the `ENGINE_BUILD_VERSION` environment variable and falls back to `0.0.0-pre.0` when unset.

## Build Commands

```bash
./gradlew build                                       # Full JVM build + tests + lint
./gradlew nativeCompile                               # Produce the native plugin binary for the os and architecture you are using
./gradlew test                                        # Unit tests for the root plugin
./gradlew :protoc-gen-openapi-examples:check          # Run the example suite
./gradlew ktlintCheck                                 # Lint
./gradlew ktlintFormat                                # Auto-format
```

To run the plugin on the example proto files end-to-end:

```bash
./gradlew :protoc-gen-openapi-examples:generateProto
```

## Subprojects

Subprojects are auto-discovered by [`settings.gradle.kts`](settings.gradle.kts): any subdirectory with a `build.gradle.kts` is included as `:protoc-gen-openapi-<relative-path>`.
Add a `.gradle_ignore` marker file to a directory to exclude it.

## GraalVM Native Image and Reflection Metadata

Protobuf uses runtime reflection, which must be explicitly declared for `native-image`.
Reflection metadata lives in [`src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi/`](src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi/).

The build sets `-H:ThrowMissingRegistrationErrors=` so missing reflection registrations fail loudly rather than silently at runtime.

When you add new proto types or new Kotlin reflection usage, regenerate the metadata:

1. Run the plugin on the JVM with the GraalVM agent attached: `./gradlew :protoc-gen-openapi:run` (feed it a real `CodeGeneratorRequest`)
2. Copy/merge the captured metadata into the resources directory: `./gradlew :protoc-gen-openapi:metadataCopy`
3. Verify by re-running the example suite: `./gradlew :protoc-gen-openapi-examples:generateProto`

## Code Style

- ktlint 1.8.0 with IntelliJ IDEA style
- `explicitApi()` is enabled — every public declaration requires an explicit visibility modifier
- Max line length: 88 chars; multiline function signatures enforced
- Generated protobuf code under `build/` is excluded from linting

## Markdown Style

Use one sentence per line in all `*.md` files.
Markdown collapses consecutive lines into a single paragraph at render time, so this convention keeps diffs sentence-level and avoids reflow noise without affecting the rendered output.

## Example Suite Mechanics

Each example under [`examples/src/<name>/`](examples/README.md) is a self-contained Gradle test suite that compiles real `.proto` files through the plugin and asserts that the generated OpenAPI output matches a checked-in reference file.

Each suite runs:

1. `protoc` with the `recorder` plugin (a native binary published as `com.engine:protoc-utils-recorder` from [engine-public/protoc-utils](https://github.com/HotelEngine/protoc-utils)) to capture the raw `CodeGeneratorRequest` as a `.binpb` file.
2. A test that loads the `.binpb` and feeds it to `ProtocGenOpenAPI.compile()`.
3. For happy-path examples, comparison against reference JSON/YAML files stored in `src/<suite>/resources/`.
   For error-case examples, an assertion that compilation fails with an informative error.

Tests use [kotest](https://kotest.io) `FunSpec` style with `assertSoftly` enabled so all assertions are evaluated even on failure.

## Adding a New Example

1. Create `src/<name>/proto/` with your `.proto` files.
2. Add a test suite block in [`examples/build.gradle.kts`](examples/build.gradle.kts) following the pattern of the existing suites.
3. Run `./gradlew :protoc-gen-openapi-examples:generate<Name>Proto` to produce the `code-generator-request.binpb`, then run the test once to generate initial output.
4. Copy the generated JSON/YAML into `src/<name>/resources/` as the reference file.
5. Write a `src/<name>/README.md` describing what the example demonstrates.
6. Add a section for it in [`examples/README.md`](examples/README.md).

## PR Process

1. Make your change.
   Cover behavior with unit tests in `src/test/` and, for anything that affects generated output, an example test suite under `examples/src/`.
2. Run `./gradlew build :protoc-gen-openapi-examples:check` locally.
   Both must pass.
3. If you added new proto types or new reflection usage and `nativeCompile` (or the resulting binary) fails, regenerate the reflection metadata as described above and include the updated `reflect-config.json` in your PR.
4. Open a pull request describing the change and linking any relevant Envoy or OAS spec references.
