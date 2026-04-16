# Conventions

A minimal example based on the [helloworld Greeter service](https://grpc.io/docs/what-is-grpc/introduction/) from the gRPC introduction. The proto uses **only** `google.api.http` annotations — no `engine.protoc.openapi.*` annotations — to illustrate the baseline conventions that the plugin derives from plain HTTP binding rules.

## What it exercises

**Path parameters inferred from URL templates.** `SayHello` is bound to `GET /hello/{name}`. The `{name}` segment is matched to the `name` field on `HelloRequest` and emitted as a required path parameter with its proto field type (`string`).

**`response_body` field extraction.** The binding carries `response_body: "message"`, which tells the plugin to use the `message` field of `HelloReply` as the HTTP response body rather than the whole wrapper message. The emitted response schema is therefore `{ "type": "string" }` — the type of the named field — rather than a `$ref` to a component schema. `HelloReply` itself is not added to `components/schemas` because it is never `$ref`'d.

**Service-derived `info`.** With no file-level or service-level engine annotation, `info.title` is set to the service name (`Greeter`) and `info.description` is taken from the leading comment on the service declaration. No `info.version` is emitted.

## Peculiarities

Because `info.version` is absent the output does not satisfy the OAS 3.1 schema (which requires that field). The test therefore runs with `validateOutput = false` and asserts structural correctness against the reference file instead. Adding `info.version` requires at minimum a file-level `engine.protoc.openapi.file` annotation; see the [petstore](../petstore/README.md) or [complete](../complete/README.md) examples for that pattern.