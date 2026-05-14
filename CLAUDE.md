# CLAUDE.md

Guidance for Claude Code when working in this repository.

For project overview, usage, compiler options, and subproject layout, read [README.md](README.md).
For build commands, native-image / reflection metadata workflow, code style, and the example-suite pipeline, read [CONTRIBUTING.md](CONTRIBUTING.md).
For engine annotations and the model artifact, read [model/README.md](model/README.md).

## Code Navigation

- [`src/main/kotlin/com/engine/protoc/openapi/Main.kt`](src/main/kotlin/com/engine/protoc/openapi/Main.kt) — process entry point; wires stdin → `ProtocGenOpenAPI.from(...).compile()` → stdout.
- [`ProtocGenOpenAPI.kt`](src/main/kotlin/com/engine/protoc/openapi/ProtocGenOpenAPI.kt) — public façade.
  Houses the `Options` data class (every plugin option lives here as a property with KDoc) and the `from(...)` factory that registers the `Annotations` + `google.api` extension registries before parsing the `CodeGeneratorRequest`.
- [`compile/Compiler.kt`](src/main/kotlin/com/engine/protoc/openapi/compile/Compiler.kt) — internal compiler.
  `compile()` dispatches to `compileMerged()` or `compileUnmerged()` based on `options.merge`.
- [`compile/`](src/main/kotlin/com/engine/protoc/openapi/compile) — supporting builders and indices: `SchemaBuilder`, `PathsBuilder`, `MessageIndex`, `EnumIndex`, `RpcIndex`, `SchemaKeyResolver`, plus the `json/` serializers.

## Working Reminders

- After any change to a Kotlin source file, run `./gradlew ktlintFormat` before declaring the task done.
  CI runs `ktlintCheck` and will fail otherwise.
- When you add a plugin option, update the alphabetized table in [README.md](README.md) — the option name links to its KDoc line, so keep the `#L<number>` anchor accurate.
- When you add a new proto type or new Kotlin reflection usage, regenerate native-image metadata per the steps in [CONTRIBUTING.md](CONTRIBUTING.md#graalvm-native-image-and-reflection-metadata).
- Markdown files use one sentence per line — see [CONTRIBUTING.md](CONTRIBUTING.md#markdown-style).
- Adding new compiler options requires a new working example test suite, see [README.md](examples/README.md)
