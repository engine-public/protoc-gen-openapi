# namespacing example

Exercises the five `schemaNamespace*` plugin options and `setSchemaTitleToMessageName` that
control how proto package information is incorporated into `components/schemas` keys and
`$ref` strings.

## The problem

By default (`schemaNamespaceStrategy = NONE`) schema keys are the unqualified message name. When
two proto packages both define a message called `Item`, the second definition silently overwrites
the first and the generated spec is broken.

This example uses two packages that both define `GetItemRequest` and `Item`:

| Package | Service | Messages |
|---------|---------|----------|
| `engine.protoc.openapi.example.namespacing.catalog.v1` | `CatalogService` | `GetItemRequest`, `Item` |
| `engine.protoc.openapi.example.namespacing.inventory.v2` | `InventoryService` | `GetItemRequest`, `Item` |

## Options

### `schemaNamespaceStrategy`

| Value | Description |
|-------|-------------|
| `NONE` *(default)* | Unqualified message name — collisions silently overwrite each other |
| `FULL_PACKAGE` | Every package segment is prepended to the message name |
| `SIMPLIFIED_PACKAGE` | The longest common package prefix shared by all schemas in the document is stripped before prefixing |

### `schemaNamespaceSeparator`

Joins package segments and the message name.

| Value | Character |
|-------|-----------|
| `NONE` *(default)* | No separator — segments concatenated directly |
| `UNDERSCORE` | `_` |
| `DASH` | `-` |
| `DOT` | `.` |

### `schemaNamespaceCasing`

Capitalises each package segment. Version segments (see below) extracted via
`schemaNamespaceVersionExtraction` are always kept lowercase.

| Value | Effect |
|-------|--------|
| `NONE` *(default)* | Segments left as written in the proto source |
| `CAPITALIZED` | First character of each package segment uppercased |
| `UPPER_CASE` | Every character of each package segment uppercased |

### `schemaNamespaceVersionExtraction`

Boolean, default `false`. When `true`, package segments that match the proto versioning convention
(`v1`, `v2beta1`, etc.) are removed from their original position and appended at the end of the
schema key, after the message name, without any capitalisation.

### `setSchemaTitleToMessageName`

Boolean, default `false`. When `true`, adds a `"title"` field to every schema object in
`components/schemas` set to the unqualified proto message name. Useful when schema keys are
namespaced and consumers need a clean, stable label.

If a schema has an `engine.protoc.openapi.message` annotation that explicitly sets `title`, the
annotation value takes precedence over the auto-generated name. In this example,
`catalog.proto`'s `Item` message has `title: "CatalogItem"` in its annotation, so its schema
always shows `"title": "CatalogItem"` regardless of the namespace key format.

## Tested Combinations

| # | schemaNamespaceStrategy | schemaNamespaceSeparator | schemaNamespaceCasing | schemaNamespaceVersionExtraction | setSchemaTitleToMessageName | Example key (catalog.v1.Item) | Title |
|---|-------------------------|--------------------------|-----------------------|----------------------------------|-----------------------------|-------------------------------|-------|
| 1 | NONE | NONE | NONE | true | true | `Item` (collision, inventory wins) | `Item` (auto; no annotation on surviving schema) |
| 2 | NONE | DASH | NONE | true | false | `Item` | — (option is false) |
| 3 | FULL_PACKAGE | NONE | CAPITALIZED | true | true | `EngineProtocOpenapiExampleNamespacingCatalogItemv1` | `CatalogItem` (annotation overrides auto) |
| 4 | FULL_PACKAGE | DOT | NONE | true | true | `engine.protoc.openapi.example.namespacing.catalog.Item.v1` | `CatalogItem` (annotation overrides auto) |
| 5 | FULL_PACKAGE | UNDERSCORE | UPPER_CASE | false | true | `ENGINE_PROTOC_OPENAPI_EXAMPLE_NAMESPACING_CATALOG_V1_ITEM` | `CatalogItem` (annotation overrides auto) |
| 6 | SIMPLIFIED_PACKAGE | UNDERSCORE | CAPITALIZED | true | true | `Catalog_Item_v1` | `CatalogItem` (annotation overrides auto `Item`) |
