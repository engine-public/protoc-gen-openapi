# referenceLinks

Demonstrates the `referenceLinkTarget` option, which rewrites CommonMark reference links in proto comments into same-document anchors.

A reference link is a bracketed token in a leading comment — `[Widget]`, `[catalog.v1.Widget]`, or `[WidgetService.GetWidget]` — that names another element in the compile scope.
When it resolves, the plugin replaces it with a Markdown link to the referenced operation, tag, or schema; when it does not, the bracketed text is left as-is and a warning is logged.

Anchor fragment formats are renderer-specific and are not portable, so the target is chosen explicitly.
This suite compiles the same proto twice:

- **`SWAGGER_UI`** (the default) — operation references resolve to `#/{tag}/{operationId}` and service references to `#/{tag}`.
  Swagger UI has no anchor for a component schema, so `[Widget]`-style schema references are stripped of their brackets and rendered as an inline code span (`` `Widget` ``).
- **`REDOC`** — operation references resolve to `#operation/{operationId}`, service references to `#tag/{tagName}`, and schema references to `#tag/{schemaKey}`.
  Because Redoc has no stable standalone-schema anchor, enabling this target also emits a `<SchemaDefinition>` section tag per component schema (grouped under an `x-tagGroups` "Schemas" group) so every schema reference has a controlled anchor to point at.

Both runs use `autoTagServices = true` so that every operation carries a tag — Swagger UI's operation anchors are nested under a tag, so an untagged operation cannot be linked.
