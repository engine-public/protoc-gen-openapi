# PetStoreTest Fix Plan

Errors from `matches petstore.openapi.json` (70 total), compared against
`swagger-api.petstore.openapi.yaml`. Grouped by root cause below.

---

## Group A — OpenAPI version mismatch (1 error)

**Errors:** `$.openapi` expected "3.0.4", actual "3.1.0"

**Cause:** The proto annotation sets `openapi: "3.1.0"`, but the YAML source
declares `openapi: 3.0.4`. The plugin always emits 3.1.0 as the default; the
annotation overrides it to 3.1.0 explicitly.

**Fix:** Remove the `openapi: "3.1.0"` line from the file-level annotation in
`petstore.proto` and let the plugin default to 3.1.0 naturally, then update the
YAML reference to replace `openapi: 3.0.4` with `openapi: 3.1.0` so the
comparison reflects the plugin's actual output version.

---

## Group B — `explode` field not emitted (2 errors)

**Errors:**
- `$.paths./pet/findByStatus.get.parameters[0].explode` expected true, actual null
- `$.paths./pet/findByTags.get.parameters[0].explode` expected true, actual null

**Cause:** `ParameterSchema.explode` is populated in the proto annotation but the
serializer (`OpenApiSerializer.kt`, `ParameterSchema.toJson()`) does not emit it
to the JSON output.

**Fix:** In `OpenApiSerializer.kt`, locate the `ParameterSchema.toJson()` method
and add serialization of the `explode` field (and verify `style` and
`allowReserved` are also emitted while there).

---

## Group C — DeletePet parameters emitted in wrong order (12 errors)

**Errors:** `$.paths./pet/{petId}.delete.parameters[0]` and `[1]` are swapped —
the compiler emits `[petId (path), api_key (header)]` but the spec expects
`[api_key (header), petId (path)]`.

**Cause:** `PathsBuilder.buildOperation()` splits annotated parameters into path
params and non-path params, then always appends them as
`pathParamNodes + nonPathParamNodes`. This ignores the original ordering declared
in the annotation.

**Fix:** Preserve the full annotation parameter order. Merge path and non-path
params according to their position in `annotation.parametersList` rather than
splitting and re-joining them. The `buildAnnotatedPathParams` path-rewriting logic
(for renaming `{value}` → `{petId}`) can be retained, but the final param list
should follow annotation order.

---

## Group D — Stray `$ref` injected into explicit content schemas (1 error)

**Errors:** `$.paths./pet/{petId}/uploadImage.post.requestBody.content.application/octet-stream.schema.$ref`
expected null, actual `"#/components/schemas/UploadFileRequest"`

**Cause:** `PathsBuilder.injectMissingRef()` injects a `$ref` into any media type
whose `schema` object lacks one. The `application/octet-stream` content has an
explicit `{type: string, format: binary}` schema, which has no `$ref` — so
`injectMissingRef` incorrectly stamps one in.

**Fix:** Change `injectMissingRef` to only inject `$ref` into schemas that are
empty objects (no fields at all). If a schema already contains any keyword (e.g.
`type`, `format`, `description`), leave it untouched.

---

## Group E — Operation extensions (`x-*`) not emitted (9 errors)

**Errors:**
- `$.paths./store/inventory.get.x-swagger-router-controller` expected "OrderController", null
- `$.paths./store/order.post.x-swagger-router-controller` expected "OrderController", null
- `$.paths./store/order/{orderId}.get.x-swagger-router-controller` expected "OrderController", null
- `$.paths./store/order/{orderId}.delete.x-swagger-router-controller` expected "OrderController", null
- `$.paths./user.post.x-swagger-router-controller` expected "UserController", null
- `$.paths./user/createWithList.post.x-swagger-router-controller` expected "UserController", null
- `$.paths./user/{username}.put.x-swagger-router-controller` expected "UserController", null
- (and `x-swagger-router-model` on schemas — see Group F)

**Cause:** `PathsBuilder.buildOperation()` manually constructs the operation JSON
node field-by-field from the `annotation: Operation` object. It never reads
`annotation.extensionsMap`, so `extensions` set in the proto annotation are never
emitted.

**Fix:** At the end of `buildOperation()`, iterate `annotation.extensionsMap` and
call `node.set(key, value.toJson(ctx))` for each entry (same pattern used in
`OpenApiSerializer.kt`'s `putExtensionsInto` helper). Import or inline that helper
into `PathsBuilder`.

---

## Group F — Schema-level extensions (`x-swagger-router-model`) not supported (5 errors)

**Errors:**
- `$.components.schemas.Order.x-swagger-router-model` expected "io.swagger.petstore.model.Order", null
- Same for Category, User, Tag, Pet

**Cause:** `SchemaObject` in `schema.proto` has no `extensions` field, so there is
no way to declare `x-*` extensions on a schema. The YAML assigns
`x-swagger-router-model` at the schema level; the compiler has no mechanism to
emit it.

**Fix (two parts):**
1. Add `map<string, google.protobuf.Value> extensions = <next field number>` to
   `SchemaObject` in `model/src/main/proto/engine/protoc/openapi/schema.proto`.
2. Serialize the new field in `SchemaSerializer.kt` `SchemaObject.toJson()` using
   the same `putExtensionsInto` pattern used elsewhere.
3. Add `engine.protoc.openapi.message` annotations to each relevant message in
   `petstore.proto` with `extensions: { key: "x-swagger-router-model" value: { string_value: "io.swagger.petstore.model.XXX" } }`.

---

## Group G — Field `example` annotations missing from proto (16 errors)

**Errors:** `example` missing on properties of Order (id, petId, quantity, status),
Category (id, name), User (id, username, firstName, lastName, email, password,
phone, userStatus), Pet (id, name).

**Cause:** The proto fields have no `engine.protoc.openapi.field` annotations with
`example` values, so the compiler emits no `example` in the schema properties.

**Fix:** Add `engine.protoc.openapi.field` annotations to each affected field in
`petstore.proto` using `SchemaObject.examples` (field 11, `repeated Any`) with the
appropriate wrapper type:
- integers → `[type.googleapis.com/google.protobuf.Int64Value] { value: 10 }`
- strings → `[type.googleapis.com/google.protobuf.StringValue] { value: "doggie" }`

Note: use `SchemaObject.example` (field 4, deprecated OAS `example`) rather than
the JSON Schema `examples` array if single-value examples are needed to match the
YAML's `example` key (not `examples`). Check how `SchemaSerializer.kt` serializes
field 4 vs field 11 to pick the right one.

---

## Group H — `ship_date` has wrong description; `format` missing (2 errors)

**Errors:**
- `$.components.schemas.Order.properties.shipDate.format` expected "date-time", actual null
- `$.components.schemas.Order.properties.shipDate.description` expected null, actual "date-time"

**Cause:** The `ship_date` field has a leading proto comment `// date-time` which
the compiler picks up as a field description. The YAML intends `date-time` to be
the `format`, not the description.

**Fix:** Remove the `// date-time` comment from the `ship_date` field in
`petstore.proto` and add an `engine.protoc.openapi.field` annotation with
`format: "date-time"` explicitly.

---

## Group I — XML annotations missing from schemas and properties (8 errors)

**Errors:**
- `$.components.schemas.Order.xml` expected `{"name":"order"}`
- `$.components.schemas.Category.xml` expected `{"name":"category"}`
- `$.components.schemas.User.xml` expected `{"name":"user"}`
- `$.components.schemas.Tag.xml` expected `{"name":"tag"}`
- `$.components.schemas.Pet.xml` expected `{"name":"pet"}`
- `$.components.schemas.Pet.properties.photoUrls.xml` expected `{"wrapped":true}`
- `$.components.schemas.Pet.properties.photoUrls.items.xml` expected `{"name":"photoUrl"}`
- `$.components.schemas.Pet.properties.tags.xml` expected `{"wrapped":true}`
- `$.components.schemas.ApiResponse.xml` expected `{"name":"##default"}`

**Cause:** The YAML declares `xml` objects on each schema and on some properties.
The proto messages have no `engine.protoc.openapi.message` or
`engine.protoc.openapi.field` annotations with `xml: { ... }` set.

**Fix:** Add `engine.protoc.openapi.message` annotations with `object: { xml: { name: "order" } }`
(etc.) to each message type in `petstore.proto`. Add `engine.protoc.openapi.field`
annotations to `photo_urls` and `tags` fields of `Pet` with `xml: { wrapped: true }`,
and to the items of `photo_urls` with `xml: { name: "photoUrl" }` via the field
annotation's `items.xml`.

---

## Group J — Internal request-wrapper schemas leaking into components (10 errors)

**Errors:** The following schemas appear in the output but are not in the YAML:
`FindPetsByStatusRequest`, `FindPetsByTagsRequest`, `PetIdRequest`,
`UpdatePetWithFormRequest`, `DeletePetRequest`, `UploadFileRequest`,
`OrderIdRequest`, `UserListRequest`, `LoginUserRequest`, `UsernameRequest`

**Cause:** The `MessageCollector` in the plugin gathers every message type
referenced as an RPC input or output and emits it as a component schema. These
request-wrapper messages are an implementation detail of the proto design and
should not appear in the OpenAPI output.

**Fix (choose one approach):**
- **Option A (proto change):** Replace each request-wrapper RPC input with
  `google.protobuf.Empty`, moving all parameter info into explicit `parameters`
  annotations. This is cleanest but requires significant proto restructuring.
- **Option B (compiler change):** Add a mechanism to suppress schema generation
  for specific message types — e.g., a boolean flag on the `engine.protoc.openapi.message`
  annotation (`exclude_from_schemas: true`) that tells `MessageCollector` to skip it.
- **Option C (compiler change):** Do not collect the RPC input type when the
  operation has a fully-annotated `request_body` with no `body: "*"` in the HTTP
  rule, treating explicit-annotation operations as not needing an auto-collected
  input schema.

---

## Group K — `components.requestBodies` missing (1 error)

**Errors:** `$.components.requestBodies` expected `{Pet: {...}, UserArray: {...}}`, actual null

**Cause:** The `components.request_bodies` map in the file-level OpenAPI annotation
is not populated. This section is defined in the source YAML under
`components.requestBodies`.

**Fix:** Add `request_bodies` entries to the `components` block of the file-level
annotation in `petstore.proto`:
```
request_bodies: {
  key: "Pet"
  value: { request_body: { description: "..." content: { ... } } }
}
request_bodies: {
  key: "UserArray"
  value: { request_body: { description: "..." content: { ... } } }
}
```

---

## Group L — Empty `parameters: []` not emitted (1 error)

**Errors:** `$.paths./user/logout.get.parameters` expected `[]`, actual null

**Cause:** The YAML explicitly declares `parameters: []` for `logoutUser`. The
plugin omits the `parameters` key entirely when there are no parameters, but the
diff algorithm treats `[]` and `null` as different values.

**Fix (choose one):**
- **Option A (test change):** Update `collectJsonDiffs` to treat an empty array
  and a missing/null node as equal, since they are semantically equivalent in OpenAPI.
- **Option B (compiler change):** When the annotation has a non-null
  `parametersList` (even if empty), emit `parameters: []` rather than omitting the key.
- **Option C (proto change):** Remove `parameters: []` from the source YAML before
  comparing, since it is redundant.

---

## Group M — Response headers missing from `loginUser` (1 error)

**Errors:** `$.paths./user/login.get.responses.200.headers` expected
`{X-Rate-Limit: {...}, X-Expires-After: {...}}`, actual null

**Cause:** The `loginUser` response annotation in `petstore.proto` defines the 200
response content but does not include the `headers` map. The YAML has two response
headers: `X-Rate-Limit` (integer/int32) and `X-Expires-After` (string/date-time).

**Fix:** Add a `headers` map to the 200 `ResponseObject` in the `loginUser`
annotation in `petstore.proto`. Use `HeaderOrReference` entries referencing the
`Header` model (which has `schema` and `description` fields).

---

## Pet.required missing (1 error, part of Group F schema issues)

**Errors:** `$.components.schemas.Pet.required` expected `["name","photoUrls"]`, actual null

**Cause:** The YAML marks `name` and `photoUrls` as required on the Pet schema.
The proto has no `engine.protoc.openapi.message` annotation with `required` set.

**Fix:** Add `engine.protoc.openapi.message` annotation to the `Pet` message with
`object: { required: ["name", "photoUrls"] }`. Note the casing must match the
JSON field names (`photoUrls` not `photo_urls`).

---

## Summary

| Done | Group | Order | Description                                            | Errors | Type                 |
|------|-------|-------|--------------------------------------------------------|--------|----------------------|
| [X]  | A     | 5     | `openapi` version (3.0.4 vs 3.1.0)                     | 1      | YAML mismatch        |
| [ ]  | B     | 3     | `explode` not serialized from `ParameterSchema`        | 2      | Compiler bug         |
| [ ]  | C     | 6     | DeletePet params emitted in wrong order                | 12     | Compiler bug         |
| [ ]  | D     | 2     | Stray `$ref` injected into explicit content schemas    | 1      | Compiler bug         |
| [ ]  | E     | 1     | Operation-level `x-*` extensions not emitted           | 9      | Compiler gap         |
| [ ]  | F     | 4     | Schema-level `extensions` not in model or serializer   | 5      | Model + compiler gap |
| [ ]  | G     | 10    | Field `example` annotations missing from proto         | 16     | Proto annotation gap |
| [ ]  | H     | 9     | `ship_date` comment treated as description, not format | 2      | Proto annotation gap |
| [ ]  | I     | 11    | XML annotations missing from messages/fields           | 8      | Proto annotation gap |
| [ ]  | J     | 8     | Internal request-wrapper messages leak into components | 10     | Design issue         |
| [ ]  | K     | 13    | `components.requestBodies` not populated               | 1      | Proto annotation gap |
| [ ]  | L     | 7     | Empty `parameters: []` emitted as null                 | 1      | Compiler/test gap    |
| [ ]  | M     | 12    | `loginUser` 200 response headers missing               | 1      | Proto annotation gap |
