# responseBodyError

Verifies that `response_body` and `body` annotations whose field names do not exist on the corresponding message produce explicit compile errors rather than silently emitting a broken spec.

This example is an **error-case test**. Unlike the other examples it has no reference output files and does not validate generated OpenAPI content — it asserts that compilation fails loudly and that the error message identifies the bad field name.

## What it exercises

**`response_body` with a nonexistent field.** `WidgetService.GetWidget` annotates its HTTP binding with `response_body: "this_field_does_not_exist"`. The plugin must detect that `this_field_does_not_exist` is not a field on `GetWidgetResponse` and surface a compile error naming the bad field.

**`body` with a nonexistent field.** `GadgetService.CreateGadget` annotates its HTTP binding with `body: "this_body_field_does_not_exist"`. The plugin must detect that `this_body_field_does_not_exist` is not a field on `CreateGadgetRequest` and surface a compile error naming the bad field.

**No output on error.** When either condition is present, the plugin must not emit any output files. Generating a partial spec alongside an error would give consumers a silently incomplete document.

## Peculiarities

Before the bug fix that motivated this example, both cases could produce dangling `$ref`s or empty schemas. `PathsBuilder` would silently skip schema collection when the named field was not found, but the schema-emission phase would still emit a `$ref` to the uncollected type — resulting in an invalid spec with no error reported.

Because the test asserts on error conditions rather than output content, `validateOutput` is set to `false` (there is nothing to validate) and there are no reference files in `src/responseBodyError/resources/`.

The two services are in the same proto file intentionally: a single compilation pass must catch and report both error kinds simultaneously.