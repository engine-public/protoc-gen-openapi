# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Does

`protoc-gen-openapi` is a `protoc` compiler plugin that converts gRPC service definitions (protobuf) into OpenAPI v3.1 specifications. It compiles to a native binary via GraalVM so it can be used directly in a `protoc` invocation without special JVM setup.

## Build and Development Commands

```bash
./gradlew build                              # Full build
./gradlew nativeCompile                      # Compile to native binary (requires GraalVM 21)
./gradlew test                               # Run tests
./gradlew ktlintCheck                        # Lint
./gradlew ktlintFormat                       # Auto-format
./gradlew :protoc-gen-openapi-example:generateProto  # Run plugin on example project
./gradlew :protoc-gen-openapi:metadataCopy   # Merge GraalVM reflection metadata after agent run
```

Version is set via the `ENGINE_BUILD_VERSION` environment variable; defaults to `0.0.0-pre.0`.

## Architecture

### Subprojects

Subprojects are auto-discovered: any subdirectory with a `build.gradle.kts` is included. The project name convention is `:protoc-gen-openapi-<relative-path>`.

- **root** (`src/main/kotlin/`) — The plugin executable: reads `CodeGeneratorRequest` from stdin, writes `CodeGeneratorResponse` to stdout.
- **`model/`** (`:protoc-gen-openapi-model`) — Protobuf definitions for the full OpenAPI v3.1 spec, plus custom proto extensions (`annotations.proto`, `openapi.proto`, etc.). These are the types the plugin reads from annotated `.proto` files.
- **`example/`** — Demonstrates plugin usage; also used to capture real `CodeGeneratorRequest` input for debugging.

### Core Plugin Flow

```
Proto files with OpenAPI extensions
  → protoc creates CodeGeneratorRequest (binary, via stdin)
  → ProtocGenOpenAPI.from(stdin) parses it with an ExtensionRegistry
      (registers Annotations + google.api extensions)
  → compile() filters file descriptors that have services,
      reads the file_level_openapi extension from each file's options,
      emits a .txt file per proto file as CodeGeneratorResponse
  → written to stdout → protoc writes files to disk
```

Key classes:
- `Main.kt` — entry point (`mainClass = "com.engine.protoc.openapi.MainKt"`)
- `ProtocGenOpenAPI.kt` — all plugin logic; `from()` + `compile()`
- `Options` (inner class) — plugin options parsed via Kotlin reflection from the `parameter` string sent by protoc

### Plugin Options (passed via `--openapi_out=option=value:outdir`)

| Option | Type | Description |
|---|---|---|
| `recordCodeGeneratorRequest` | path | Write the parsed `CodeGeneratorRequest` proto to this file |
| `recordCodeGeneratorResponse` | path | Write the `CodeGeneratorResponse` proto to this file |

### Debugging Environment Variables

These bypass the normal stdin mechanism and are essential when debugging native-image issues:

| Variable | Description |
|---|---|
| `PROTOC_GEN_OPENAPI_RECORD_CGREQ` | Write raw stdin bytes to this path before parsing |
| `PROTOC_GEN_OPENAPI_REPLAY_CGREQ` | Read bytes from this path instead of stdin |

Standard debug workflow: run `generateProto` with `PROTOC_GEN_OPENAPI_RECORD_CGREQ=/var/tmp/protoc-gen-openapi.cgreq`, then replay via `MainKt` (JVM, debuggable) using `PROTOC_GEN_OPENAPI_REPLAY_CGREQ`.

## GraalVM Native Image and Reflection Metadata

Protobuf uses runtime reflection, which must be explicitly declared for native-image. Reflection metadata lives in `src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi/reflect-config.json`.

When adding new proto types or Kotlin reflection usage, update the metadata:

1. Run the plugin with the GraalVM agent: `./gradlew :protoc-gen-openapi:run` (with a real request as input)
2. Copy/merge metadata: `./gradlew :protoc-gen-openapi:metadataCopy`
3. Verify: `./gradlew :protoc-gen-openapi-example:generateProto`

The build uses `-H:ThrowMissingRegistrationErrors=` so missing reflection registrations fail loudly rather than silently at runtime.

## Code Style

- ktlint 1.7.1 with IntelliJ IDEA style
- All public declarations require explicit visibility modifiers (`explicitApi()` is enabled)
- Max line length: 88 chars; multiline function signatures enforced
- Generated protobuf code in `build/` is excluded from linting
