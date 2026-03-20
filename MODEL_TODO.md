# Model TODO: Proto vs OpenAPI 3.1 Spec Gaps

This document catalogs discrepancies between the protobuf model in `/model` and the
[OpenAPI 3.1 specification](https://spec.openapis.org/oas/v3.1.0), ranked by priority.

---

## Critical

These issues are correctness or completeness blockers — they prevent expressing valid OpenAPI 3.1
documents or will produce structurally invalid output.

---

### 1. `schema.proto` — Schema Object is nearly empty

**File:** `engine/protoc/openapi/schema.proto`

The current `Schema` message has only 4 fields: `discriminator`, `xml`, `external_docs`, and
`example`. The OpenAPI 3.1 Schema Object is a **full superset of JSON Schema Draft 2020-12**,
meaning nearly all of the spec's expressive power lives here. Every JSON Schema keyword is valid
and almost none of them are modeled.

Missing fields include (not exhaustive):

| Category | Missing keywords |
|---|---|
| Type/structure | `type`, `properties`, `required`, `additionalProperties`, `patternProperties`, `propertyNames`, `unevaluatedProperties`, `unevaluatedItems` |
| Composition | `allOf`, `anyOf`, `oneOf`, `not`, `if`, `then`, `else` |
| Arrays | `items`, `prefixItems`, `contains`, `minContains`, `maxContains` |
| Metadata | `title`, `description`, `default`, `examples`, `deprecated`, `readOnly`, `writeOnly` |
| Validation | `enum`, `const`, `format`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minLength`, `maxLength`, `pattern`, `minItems`, `maxItems`, `uniqueItems`, `minProperties`, `maxProperties` |
| References | `$ref`, `$defs`, `$id`, `$schema`, `$anchor`, `$dynamicRef`, `$dynamicAnchor` |
| Content encoding | `contentEncoding`, `contentMediaType`, `contentSchema` |

Without these, users cannot express most schemas other than trivially simple ones.

---

### 2. `schema.proto` — `external_docs` has the wrong type

**File:** `engine/protoc/openapi/schema.proto`, line 45

```protobuf
optional string external_docs = 3;  // WRONG
```

The OpenAPI spec defines `externalDocs` as an `ExternalDocumentation` object (with `url` and
`description` fields), not a plain string URI. This field should be:

```protobuf
.engine.protoc.openapi.model.ExternalDocumentation external_docs = 3;
```

Every other occurrence of `external_docs` in the model (e.g., `openapi.proto`, `tag.proto`,
`operation.proto`) correctly uses the `ExternalDocumentation` message type.

---

### 3. `responses.proto` — HTTP status code range wildcards cannot be represented

**File:** `engine/protoc/openapi/model/responses.proto`, line 27

```protobuf
map<int32, .engine.protoc.openapi.model.ResponseOrReference> codes = 2;
```

The spec says the key "MAY contain the uppercase wildcard character X" — e.g., `2XX`, `3XX`,
`4XX`, `5XX` — to describe a range of response codes. An `int32` key cannot represent these
strings at all, silently dropping all range-based response definitions.

The key must be `string`.

---

### 4. `encoding.proto` — Missing `style`, `explode`, and `allow_reserved` fields

**File:** `engine/protoc/openapi/model/encoding.proto`

The current `Encoding` message only has `content_type` and `headers`. The spec defines three
additional fields that control how `application/x-www-form-urlencoded` and `multipart/form-data`
parameters are serialized:

| Field | Type | Default | Description |
|---|---|---|---|
| `style` | `optional string` | `"form"` | Serialization style (same values as Parameter `style`) |
| `explode` | `optional bool` | `true` for `form` | Array/object generate separate params per item |
| `allow_reserved` | `optional bool` | `false` | Allow RFC3986 reserved chars unencoded |

Note: when `style`, `explode`, or `allow_reserved` are set, `contentType` is ignored per the
spec. This interaction should be noted in the comment.

---

## High Priority

These issues cause spec violations or incorrect serialization behavior but do not prevent
all usage of the model.

---

### 5. `openapi.proto` — `webhooks` map does not allow Reference Objects

**File:** `engine/protoc/openapi/openapi.proto`, line 48

```protobuf
map<string, .engine.protoc.openapi.model.PathItem> webhooks = 6;  // WRONG
```

The spec defines `webhooks` as `Map[string, Path Item Object | Reference Object]`. References
should be valid values here so that webhooks can point to reusable path item definitions in
`components/pathItems`. Should use a `PathItemOrReference` wrapper (analogous to
`CallbackOrReference`, `ResponseOrReference`, etc.).

---

### 6. `components.proto` — `path_items` map does not allow Reference Objects

**File:** `engine/protoc/openapi/model/components.proto`, line 47

```protobuf
map<string, .engine.protoc.openapi.model.PathItem> path_items = 10;  // WRONG
```

Every other map in `Components` uses an `OrReference` wrapper type (`ResponseOrReference`,
`ParameterOrReference`, etc.) — `path_items` should too. The spec defines it as
`Map[string, Path Item Object | Reference Object]`.

---

### 7. `oauth_flow.proto` — `authorization_url` and `token_url` are non-optional

**File:** `engine/protoc/openapi/model/oauth_flow.proto`, lines 11–17

```protobuf
string authorization_url = 1;  // non-optional
string token_url = 2;          // non-optional
```

The `OAuthFlow` message is shared by all four OAuth 2.0 flow types, but the requirements differ:

| Flow | `authorization_url` | `token_url` |
|---|---|---|
| `implicit` | **required** | not required |
| `password` | not required | **required** |
| `clientCredentials` | not required | **required** |
| `authorizationCode` | **required** | **required** |

Because both fields are non-optional, empty strings will be emitted for fields that are not
applicable to a given flow, which is invalid per the spec. Both should be `optional string`.

---

### 8. `parameter_schema.proto` — `example` and `examples` are not mutually exclusive

**File:** `engine/protoc/openapi/model/parameter_schema.proto`, lines 38–42

```protobuf
.google.protobuf.Any example = 5;
map<string, .engine.protoc.openapi.model.ExampleOrReference> examples = 6;
```

The spec states these fields "are mutually exclusive". Comparable objects in this same model
(`MediaType`, `HeaderSchema`) correctly enforce this via `oneof example_type { ... }`.
`ParameterSchema` is an inconsistent exception that allows both to be set simultaneously.

---

### 9. `server.proto` — `description` and `url` are non-optional plain strings

**File:** `engine/protoc/openapi/model/server.proto`, lines 13–17

```protobuf
string url = 1;
string description = 2;
```

`url` is required by the spec (non-optional is correct). However, `description` is optional per
the spec. With a non-optional proto3 `string`, an empty `description` is indistinguishable from an
absent one, and serializers may emit `"description": ""` rather than omitting the field. Should be
`optional string description = 2;`.

---

### 10. `callback.proto` — Callback map values do not allow Reference Objects

**File:** `engine/protoc/openapi/model/callback.proto`, line 36

```protobuf
map<string, .engine.protoc.openapi.model.PathItem> callbacks = 1;
```

Per the spec, Callback Object map values may be a `Path Item Object | Reference Object`. The
current model only accepts `PathItem` directly, preventing references to reusable path items
defined in `components`.

---

## Medium Priority

These are inaccurate or incomplete comments, minor spec misalignments, and inconsistencies that
won't break serialization but will mislead users or violate the spec in edge cases.

---

### 11. `oauth_flow.proto` — Typo in REQUIRED comment

**File:** `engine/protoc/openapi/model/oauth_flow.proto`, line 7

```
// REQUIRED ("implicit", "authoriztionCode").
```

`"authoriztionCode"` is misspelled — should be `"authorizationCode"`.

---

### 12. `link.proto` — Malformed CommonMark URL in comment

**File:** `engine/protoc/openapi/model/link.proto`, line 38

```
// [CommonMark syntax](https://spec.commonmark.org/ MAY be used for rich text representation.
```

The URL is missing its closing `)`. Should be:

```
// [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation.
```

---

### 13. `path_item.proto` and `reference.proto` — Incomplete `proto_ref` comments with TODO

**Files:** `engine/protoc/openapi/model/path_item.proto` line 22–23,
`engine/protoc/openapi/model/reference.proto` line 18–19

Both files have an incomplete comment on `proto_ref` with an empty placeholder:
```
// The RPC must exist, have a '' option, and be within the context of the protoc session.
// TODO fix comment above
```

The empty `''` should name the specific proto extension (e.g., `(google.api.http)`) and the TODO
should be resolved.

---

### 14. `openapi.proto` — Default version comment says `3.1.1` but current stable spec is `3.1.0`

**File:** `engine/protoc/openapi/openapi.proto`, line 24

```
// If not provided, will default to 3.1.1
```

The current release of the OpenAPI Specification is `3.1.0`. The default should be `"3.1.0"`
unless the project is intentionally targeting a pre-release `3.1.1`. If targeting a specific
version, this should be documented in CLAUDE.md or a comment explaining why.

---

### 15. `discriminator.proto` — Missing context: valid only with `oneOf`/`anyOf`/`allOf`

**File:** `engine/protoc/openapi/model/discriminator.proto`

The comment says `discriminator` adds polymorphism support but does not state the spec constraint
that it is only valid when used alongside `oneOf`, `anyOf`, or `allOf`. Inline schemas (not
named `$ref` targets) are never valid discriminator targets. This is important for users to know.

---

### 16. `discriminator.proto` — `property_name` should be `optional string`

**File:** `engine/protoc/openapi/model/discriminator.proto`, line 15

```protobuf
string property_name = 1;  // non-optional
```

If a `Discriminator` message is constructed but `property_name` is not set (e.g., during
incremental construction or overlaying), an empty string will be emitted. This field should be
`optional string property_name = 1;`.

---

### 17. `header_schema.proto` — Skipped field number 3

**File:** `engine/protoc/openapi/model/header_schema.proto`

Field numbers jump from `2` (`explode`) to `4` (`schema`), with no field `3`. This indicates a
field was removed at some point. While not functionally broken, the gap should either be documented
with a `reserved 3;` statement (to prevent accidental re-use) or explained in a comment.

---

### 18. `oauth_flows.proto` — Missing space in doc comment

**File:** `engine/protoc/openapi/model/oauth_flows.proto`, line 7

```protobuf
//Allows configuration of the supported OAuth Flows.
```

Missing space after `//`. Should be `// Allows configuration of the supported OAuth Flows.`

---

### 19. `mtls_security_scheme.proto` — No doc comment on empty message

**File:** `engine/protoc/openapi/model/mtls_security_scheme.proto`

`MutualTLSSecurityScheme` is an empty message with no comment. It should explain that mutual TLS
requires no additional configuration fields beyond the scheme `type`, and that it was added in
OpenAPI 3.1.0 (as a 3.1-specific feature not present in 3.0).

---

### 20. `security_requirement_values.proto` — Doc comment is copy-pasted from `SecurityRequirement`

**File:** `engine/protoc/openapi/model/security_requirement_values.proto`

The message-level comment is a verbatim copy of the `SecurityRequirement` comment, not a
description of what `SecurityRequirementValues` itself represents. It should be replaced with a
concise description: this message is a wrapper to hold the `repeated string` list of scope names
(or role names) for a single entry in a Security Requirement map, needed because protobuf map
values cannot be `repeated` scalars directly.

---

### 21. `license.proto` — Malformed comment on `name` field

**File:** `engine/protoc/openapi/model/license.proto`, line 10

```
//   // proto information: While `name` is required in the OpenAPI spec, it is optional here to allow overlaying details from other sources.
```

The `//   //` double-comment prefix and leading whitespace is malformed. Should be:

```
// protoc information: While `name` is required in the OpenAPI spec, it is optional here to allow overlaying details from other sources.
```

---

## Low Priority

Minor documentation omissions and structural notes that do not affect correctness.

---

### 22. `paths.proto` and `security_requirement.proto` — Structural wrapping vs. OpenAPI spec

**Files:** `engine/protoc/openapi/model/paths.proto`,
`engine/protoc/openapi/model/security_requirement.proto`

The OpenAPI spec defines both `Paths` and `Security Requirement` as plain maps (not objects with
a named field wrapping the map). The proto model wraps them:

- `Paths.paths` — the map lives in a `paths` field
- `SecurityRequirement.names` — the map lives in a `names` field

This is a reasonable protobuf design choice (maps must be in a message), but should be documented
clearly in each file's comment so implementors know how to unwrap them during serialization.
Neither file currently explains this structural difference from the spec.

---

### 23. `header_content.proto` and `parameter_content.proto` — Missing class-level comments

**Files:** `engine/protoc/openapi/model/header_content.proto`,
`engine/protoc/openapi/model/parameter_content.proto`

Both `HeaderContent` and `ParameterContent` are thin wrappers with no message-level comments.
They exist solely to allow a map type inside a `oneof` (which proto3 doesn't support directly).
A brief comment explaining this structural reason and noting that per the spec the map MUST contain
exactly one entry would help users understand why the indirection exists.

---

### 24. `openid_connect_security_scheme.proto` — Comment could be more precise

**File:** `engine/protoc/openapi/model/openid_connect_security_scheme.proto`

The field comment says "Well-known URL to discover the [[OpenID-Connect-Discovery]] provider
metadata" but does not mention the spec's requirement that the URL "MUST use TLS" (same as OAuth
flow URLs). Should add that constraint to the comment.

---

### 25. `schema.proto` — `example` deprecation note could reference migration path

**File:** `engine/protoc/openapi/schema.proto`, lines 47–52

The comment correctly notes `example` is deprecated in OAS 3.1 in favor of JSON Schema's
`examples` keyword. However, since `examples` (the JSON Schema keyword) is one of the missing
Schema Object fields (see issue #1), the deprecation note is currently pointing users toward a
non-existent field. Once the Schema Object is fleshed out with JSON Schema keywords, verify
that `examples` (plural) is included and update this cross-reference.
