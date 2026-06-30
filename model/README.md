# protoc-gen-openapi-model

Protobuf definitions for the [OpenAPI v3.1](https://spec.openapis.org/oas/v3.1.0) spec, plus the proto extension options the [`protoc-gen-openapi`](../README.md) compiler reads from annotated `.proto` files.

Import these protos into your own service definitions to attach OpenAPI metadata — `info`, `tags`, security schemes, response shapes, schema overrides — directly to the proto entities they describe (file, service, method, message, field, enum).
The compiler then folds that metadata into the generated OAS document.

## Contents

| file | purpose |
|---|---|
| [`annotations.proto`](src/main/proto/engine/protoc/openapi/annotations.proto) | The `extend` declarations that attach OpenAPI metadata to `FileOptions`, `ServiceOptions`, `MethodOptions`, `MessageOptions`, `FieldOptions`, `EnumOptions`, and `EnumValueOptions`. Import this file to annotate your protos. |
| [`openapi.proto`](src/main/proto/engine/protoc/openapi/openapi.proto) | The top-level `OpenAPI` message used at file and service scope. |
| [`model/*.proto`](src/main/proto/engine/protoc/openapi/model) | One proto per OAS object (`Operation`, `Schema`, `Parameter`, `RequestBody`, `Response`, security schemes, etc.). |

## Usage

The annotations are usable from any toolchain that can put the `.proto` files on `protoc`'s include path.
Two common entry points are shown below.

### Gradle Integration

Add the artifact and configure the [`protobuf-gradle-plugin`](https://github.com/google/protobuf-gradle-plugin) to extract the `.proto` files from the jar so `protoc` can resolve the imports.

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.6"
}

dependencies {
    // tells the protobuf-gradle-plugin to extract the .proto sources from the
    // jar into protoc's include path so `import "engine/protoc/openapi/..."` resolves
    protobuf("com.engine:protoc-gen-openapi-model:<version>")
}
```

With that configuration in place, run your normal `generateProto` task — `protoc` will resolve `engine/protoc/openapi/annotations.proto` (and its transitive imports) from the extracted jar.

### Bash `protoc` Invocation

If you are invoking `protoc` directly, place the model `.proto` sources somewhere on disk and pass that directory via `--proto_path` alongside your own sources:

```bash
# one-time: download and extract the model protos from the GitHub release
curl -L -o protoc-gen-openapi-model-protos.zip \
  https://github.com/engine-public/protoc-gen-openapi/releases/download/<version>/protoc-gen-openapi-model-protos.zip
unzip -d build/proto/openapi-model protoc-gen-openapi-model-protos.zip

protoc \
  --proto_path=build/proto/openapi-model \
  --proto_path=src/main/proto \
  --openapi_out=./build/openapi \
  src/main/proto/example/v1/service.proto
```

The first `--proto_path` lets `protoc` resolve `import "engine/protoc/openapi/annotations.proto"`; the second resolves your own service definitions.

### Example Protobuf Usage

The smallest useful annotation set: a file-level `info` block and a method-level `summary` for a single RPC.
Combine these with a standard `google.api.http` binding and the compiler will produce a complete OAS document.

```proto
syntax = "proto3";

package example.greeter.v1;

import "engine/protoc/openapi/annotations.proto";
import "google/api/annotations.proto";

option (engine.protoc.openapi.file) = {
    openapi: {
        openapi: "3.1.0"
        info: {
            title: "Greeter API"
            version: "1.0.0"
        }
    }
};

service Greeter {
    rpc SayHello(HelloRequest) returns (HelloReply) {
        option (engine.protoc.openapi.method) = {
            operation: {
                summary: "Greet a user by name"
            }
        };
        option (google.api.http) = {
            get: "/hello/{name}"
            response_body: "message"
        };
    }
}

message HelloRequest {
    string name = 1;
}

message HelloReply {
    string message = 1;
}
```

For richer examples covering every annotation field — message schemas, field overrides, enum suppression, security schemes, tags, response headers — see the [examples](../examples/README.md), particularly the [`complete`](../examples/src/complete/README.md) and [`petstore`](../examples/src/petstore/README.md) suites.

## Annotations

Defined in [`annotations.proto`](src/main/proto/engine/protoc/openapi/annotations.proto).
Each scope has a single extension that points at a wrapper message; the wrapper's fields hold the OAS payload plus any scope-specific flags.
This shape keeps the field names in their own per-wrapper namespace (so e.g. both `(field).inline` and `(enum).inline` can use the same short name).

| scope | extension | wrapper | nested fields |
|---|---|---|---|
| file | [`(engine.protoc.openapi.file)`](src/main/proto/engine/protoc/openapi/annotations.proto#L143) | [`File`](src/main/proto/engine/protoc/openapi/annotations.proto#L17) | `openapi:` [`OpenAPI`](src/main/proto/engine/protoc/openapi/openapi.proto) — file-level OAS document: `info`, `servers`, `tags`, `security`, `components`, `externalDocs`. Applied to every document generated from this file. |
| service | [`(engine.protoc.openapi.service)`](src/main/proto/engine/protoc/openapi/annotations.proto#L147) | [`Service`](src/main/proto/engine/protoc/openapi/annotations.proto#L23) | `openapi:` [`OpenAPI`](src/main/proto/engine/protoc/openapi/openapi.proto) — overrides or supplements file-level metadata for a single service. Highest-priority layer for `info.version`.<br>`tags: repeated string` — applied to every RPC in the service; tag definitions themselves still come from the `OpenAPI.tags` block at file or service scope.<br>`index_order: int32` — sort key controlling the order this service's paths (and its auto-tag when `autoTagServices` is enabled) appear in the document. Un-annotated services fall into their encounter ordinal; negative values place a service ahead of the un-annotated baseline; ties break by source order. See [examples/src/serviceOrdering](../examples/src/serviceOrdering/README.md). |
| method | [`(engine.protoc.openapi.method)`](src/main/proto/engine/protoc/openapi/annotations.proto#L151) | [`Method`](src/main/proto/engine/protoc/openapi/annotations.proto#L42) | `operation:` [`Operation`](src/main/proto/engine/protoc/openapi/model/operation.proto) — OAS Operation object for a single RPC: `summary`, `description`, `tags`, `parameters`, `requestBody`, `responses`, `security`, `callbacks`, etc.<br>`inline_request: bool` — when `true`, the RPC's request body schema is inlined at the use site instead of emitted as a `$ref` into `components/schemas`. Transitive — see [examples/src/inlineSchemas](../examples/src/inlineSchemas/README.md).<br>`inline_response: bool` — same as `inline_request`, but for the RPC's response body.<br>`error_responses: repeated ErrorResponse` — shortcut for typed error scenarios that expand into entries on `operation.responses`; see [examples/src/errorResponses](../examples/src/errorResponses/README.md). |
| message | [`(engine.protoc.openapi.message)`](src/main/proto/engine/protoc/openapi/annotations.proto#L155) | [`Message`](src/main/proto/engine/protoc/openapi/annotations.proto#L106) | `schema:` [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) — schema-level overrides for the generated message schema: `title`, `description`, `required`, composition keywords, etc. |
| field | [`(engine.protoc.openapi.field)`](src/main/proto/engine/protoc/openapi/annotations.proto#L159) | [`Field`](src/main/proto/engine/protoc/openapi/annotations.proto#L112) | `schema:` [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) — schema-level overrides for a single field: validation keywords, `description`, `example`, `nullable`, format hints, etc.<br>`inline: bool` — when `true` on a message-typed field, the referenced message's schema is inlined at the field site instead of emitted as a `$ref`. Same transitivity rule as the method-level inline flags — see [examples/src/inlineFieldSchema](../examples/src/inlineFieldSchema/README.md). |
| enum | [`(engine.protoc.openapi.enum)`](src/main/proto/engine/protoc/openapi/annotations.proto#L163) | [`Enum`](src/main/proto/engine/protoc/openapi/annotations.proto#L126) | `schema:` [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) — schema-level overrides for the generated enum schema.<br>`inline: bool` — when `true`, this enum is inlined at every reference instead of emitted as a shared `$ref`. Per-enum override of the `inlineEnums` compiler option. |
| enum value | [`(engine.protoc.openapi.enum_value)`](src/main/proto/engine/protoc/openapi/annotations.proto#L167) | [`EnumValue`](src/main/proto/engine/protoc/openapi/annotations.proto#L137) | `suppress: bool` — when `true`, omit this enum value from generated OAS `enum` arrays. Useful for proto3's conventional `*_UNSPECIFIED = 0` value. |
