# Unmerged

Demonstrates the plugin's **non-merge (per-service) mode**, where each service produces its own OpenAPI document. The test asserts that the correct set of files is generated and that each one matches its reference.

## What it exercises

**Per-service output files.** Three proto files participate:

- `base.proto` — file-level annotation only (info, servers). No service declared.
- `doodad.proto` — declares `DooDadService` with `ListDooDads` and `GetDooDad`.
- `two.proto` — declares `SecondaryService` with `AnotherThingToDoWithDooDads`.

When `merge = false`, the plugin emits one file per service plus one aggregate file for the package. This example produces three output files:

| Output file | Content |
|---|---|
| `…DooDadService.openapi.json` | `DooDadService` only |
| `…SecondaryService.openapi.json` | `SecondaryService` only |
| `….unmerged.openapi.json` | All services combined |

**Service-level annotation overrides.** Both services use `(.engine.protoc.openapi.service)` to override the `info.version` from the file-level annotation. `DooDadService` sets version `22.33.44`; `SecondaryService` sets `7.2.1`. The per-service output files carry these overridden versions, while the aggregate file carries the base version from `base.proto`.

**`response_body` on `google.api.http`.** `ListDooDads` uses `response_body: "doodads"`, the same pattern as the merged example — the 200 response body schema is taken from the `doodads` field of `ListDooDadResponse` rather than the whole wrapper message.

**Parameterized test validation.** The test uses kotest `withData` to validate each of the three output files independently against both the OpenAPI 3.1 schema and its reference file, rather than hard-coding assertions for each file.

## Peculiarities

The aggregate file (`….unmerged.openapi.json`) is generated even in non-merge mode. It combines all services as if merge mode were on, giving consumers a single spec that covers the full API surface. The per-service files are intended for consumers that want to depend on only one service's contract.

`SecondaryService.AnotherThingToDoWithDooDads` takes `google.protobuf.Empty` as input, exercising the empty-request-body path.

The name `DooDad` is deliberately cased with an internal capital letter to verify that the plugin handles mixed-case service and message names correctly in file naming and schema references.