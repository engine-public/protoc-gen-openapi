# Merged

Demonstrates the plugin's **merge mode**, where services defined across multiple proto files are combined into a single OpenAPI document. The test asserts that the output file name, structure, and content match the expected spec.

## What it exercises

**Multi-file merge.** Three proto files participate:

- `base.proto` — carries only a file-level annotation with `info` (title, summary, version) and `servers`. It declares no service. Its metadata becomes the root metadata of the merged output.
- `widget.proto` — declares `WidgetService` with `ListWidgets` and `GetWidget`.
- `two.proto` — declares `SecondaryService` with `AnotherThingToDoWithWidgets`.

When `merge = true`, the plugin combines all services from all files into one document. The resulting file is named after the package rather than a specific service.

**`response_body` on `google.api.http`.** `ListWidgets` uses `response_body: "widgets"` in its HTTP annotation. This tells the plugin to use the schema of the `widgets` field from `ListWidgetResponse` as the 200 response body schema, rather than the whole message. This is how the plugin handles proto RPCs that return wrapper messages where only a nested repeated field is the meaningful payload.

**Metadata-only proto file.** `base.proto` has no service. It exists only to inject file-level OpenAPI metadata into the merged output. This pattern is useful when the API metadata (title, description, servers, security schemes) is maintained separately from the service definitions.

## Peculiarities

In merge mode the output file is named using the proto package (`engine.protoc.openapi.example.merged.openapi.json`) rather than a service name. If multiple files in the merge set have different file-level annotations, the plugin uses the metadata from the file whose annotation is encountered first.

`SecondaryService.AnotherThingToDoWithWidgets` takes `google.protobuf.Empty` as its input and returns a `Widget`. This exercises the path where a request message has no fields — the operation gets no request body.