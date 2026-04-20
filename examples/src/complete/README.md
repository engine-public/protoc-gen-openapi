# Complete

The most comprehensive example in the suite. `complete.proto` is purpose-built to exercise every annotation field and OpenAPI v3.1 construct the plugin supports. It is not meant to model a realistic API — it is a structured inventory of features, organized section by section, used as the ground-truth acceptance test for the plugin.

The test asserts individual values for most fields in the generated output before finally doing a full structural diff against the reference YAML.

`complete.proto` uses `outputFormat = YAML` to demonstrate YAML output. The generated file is named `*.openapi.yaml` instead of `*.openapi.json`.

## What it exercises

### File-level annotation

All root-level OpenAPI fields: `openapi`, `json_schema_dialect`, `info` (including contact, license, and extensions), `servers` (with server variables), `tags` (with `externalDocs` and extensions), `externalDocs`, `security`, `webhooks`, and root `extensions`.

### Components

Every component type appears at least once:

- **Responses** — inline `ResponseObject` definitions with extensions.
- **Parameters** — a path parameter with `deprecated` and extensions; a query parameter; a `content`-variant parameter (using a schema inside a media type instead of a top-level schema).
- **Examples** — both `value` (inline) and `externalValue` variants.
- **Request bodies** — a named request body referenced by an operation.
- **Headers** — headers using `example` (singular), `examples` (plural), and the `content` variant; a deprecated header.
- **Links** — three link styles: `operationId`, `proto_rpc_ref` (resolved to an operationId at compile time), and `operationRef`; includes `requestBody` and `server` overrides.
- **Callbacks** — an inline callback keyed on an expression string, and a `$ref` to a component callback.
- **Path items** — a manually-declared path item with all HTTP method slots (`get`, `put`, `post`, `delete`, `options`, `head`, `patch`, `trace`), servers, and parameters; a second path item using `proto_rpc_ref` to wire an existing RPC.
- **Security schemes** — all six types: HTTP Bearer (JWT), API key, OAuth2 with all four flow types (implicit, password, clientCredentials, authorizationCode), OpenID Connect, and mutual TLS; plus a `$ref` to another scheme.

### Operations

Nine RPCs cover the full variety of operation-level annotation:

- Inline and `$ref` parameters, including a `$ref` that adds `summary` and `description` overrides.
- `requestBody` by reference and by value.
- Multiple response codes including `201` and `default`.
- Inline and referenced response headers.
- Inline and referenced links.
- Security requirements with scopes.
- Callbacks with expression keys.
- `MediaType.encoding` with all five encoding fields (`contentType`, `style`, `explode`, `allowReserved`, `headers`) — exercised on the file-upload operation.
- `MediaType.example` (singular) and `MediaType.examples` (plural, including a `$ref` example).

### Schema objects

Four messages cover the full `SchemaObject` keyword set:

**`Product`** — message-level keywords: `title`, `description`, `deprecated`, `externalDocs`, `xml` (all five XML fields), `discriminator` (with mapping), `required`, `minProperties`, `maxProperties`, `additionalProperties_allowed`, `patternProperties`, `propertyNames`, `extensions`.

**`Product` fields** — field-level keywords:
- `id`: `readOnly`, `format`
- `name`: `writeOnly`, `minLength`, `maxLength`, `pattern`, multi-type (`string | null`)
- `price`: `minimum`, `exclusiveMinimum`, `maximum`, `exclusiveMaximum`, `multipleOf`, `default`, `example`
- `category`: `enum`, `const`
- `tags`: `items`, `prefixItems`, `contains`, `minContains`, `maxContains`, `minItems`, `maxItems`, `uniqueItems`, `unevaluatedItems`
- `stock_quantity`: `deprecated`, `title`, `examples` (repeated)

**`ProductList`** — composition keywords: `allOf`, `anyOf`, `oneOf`, `not`, `if`, `then`, `else`, `unevaluatedProperties`, `additionalProperties` (as boolean schemas via `Schema.boolean`).

**`Order`** — JSON Schema referencing keywords: `$id`, `$schema`, `$anchor`, `$dynamicAnchor`, `$dynamicRef`, `$defs`, `$ref`.

**`Order.status`** — content keywords: `contentEncoding`, `contentMediaType`, `contentSchema`.

## Peculiarities

`complete.proto` is intentionally not a coherent domain model. Fields like `ProductList` using composition keywords, or `Order` using `$dynamicRef`, exist purely to verify those annotation paths are wired correctly.

`proto_rpc_ref` appears in two component contexts (`links` and `pathItems`). At compile time the plugin resolves the referenced RPC to its generated `operationId`, which makes the proto representation more stable under refactoring than a hard-coded string `operationId`.

The `Schema.boolean` wrapper type (a `oneof` inside `SchemaObject`) is used to express bare `true` and `false` schemas for `allOf` items and `unevaluatedProperties`, which have no natural proto analog otherwise.

`StorefrontService` is the only service declared in the file, and it generates a single OpenAPI file named `*.openapi.yaml`. The plugin runs in non-merge mode with `outputFormat = YAML`.