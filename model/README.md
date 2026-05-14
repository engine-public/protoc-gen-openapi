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
  https://github.com/HotelEngine/protoc-gen-openapi/releases/download/<version>/protoc-gen-openapi-model-protos.zip
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
    openapi: "3.1.0"
    info: {
        title: "Greeter API"
        version: "1.0.0"
    }
};

service Greeter {
    rpc SayHello(HelloRequest) returns (HelloReply) {
        option (engine.protoc.openapi.method) = {
            summary: "Greet a user by name"
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
Every annotation is a proto option attached to one of the standard descriptor option types.

| scope | option | type | purpose |
|---|---|---|---|
| file | [`(engine.protoc.openapi.file)`](src/main/proto/engine/protoc/openapi/annotations.proto#L16) | [`OpenAPI`](src/main/proto/engine/protoc/openapi/openapi.proto) | File-level OAS document: `info`, `servers`, `tags`, `security`, `components`, `externalDocs`. Applied to every document generated from this file. |
| service | [`(engine.protoc.openapi.service)`](src/main/proto/engine/protoc/openapi/annotations.proto#L21) | [`OpenAPI`](src/main/proto/engine/protoc/openapi/openapi.proto) | Overrides or supplements file-level metadata for a single service. Highest-priority layer for `info.version`. |
| service | [`(engine.protoc.openapi.tags)`](src/main/proto/engine/protoc/openapi/annotations.proto#L25) | `repeated string` | Tags applied to every RPC in the service. Tag definitions themselves still come from the `OpenAPI.tags` block at file or service scope. |
| method | [`(engine.protoc.openapi.method)`](src/main/proto/engine/protoc/openapi/annotations.proto#L30) | [`Operation`](src/main/proto/engine/protoc/openapi/model/operation.proto) | OAS Operation object for a single RPC: `summary`, `description`, `tags`, `parameters`, `requestBody`, `responses`, `security`, `callbacks`, etc. |
| message | [`(engine.protoc.openapi.message)`](src/main/proto/engine/protoc/openapi/annotations.proto#L35) | [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) | Schema-level overrides for the generated message schema: `title`, `description`, `required`, composition keywords, etc. |
| field | [`(engine.protoc.openapi.field)`](src/main/proto/engine/protoc/openapi/annotations.proto#L40) | [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) | Schema-level overrides for a single field: validation keywords, `description`, `example`, `nullable`, format hints, etc. |
| enum | [`(engine.protoc.openapi.enum)`](src/main/proto/engine/protoc/openapi/annotations.proto#L45) | [`Schema`](src/main/proto/engine/protoc/openapi/model/schema.proto) | Schema-level overrides for the generated enum schema. |
| enum | [`(engine.protoc.openapi.inline)`](src/main/proto/engine/protoc/openapi/annotations.proto#L47) | `bool` | When `true`, this enum is inlined at every reference instead of emitted as a shared `$ref`. Per-enum override of the `inlineEnums` compiler option. |
| enum value | [`(engine.protoc.openapi.suppress)`](src/main/proto/engine/protoc/openapi/annotations.proto#L53) | `bool` | When `true`, omit this enum value from generated OAS `enum` arrays. Useful for proto3's conventional `*_UNSPECIFIED = 0` value. |
