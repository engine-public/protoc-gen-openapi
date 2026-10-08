# referenceLinkResolution

Demonstrates the `resolveReferenceLinksMode` and `referenceLink` options.

Every string the plugin emits is either CommonMark (`description`) or plain text (`summary`, `title`), whether it comes from a proto comment or an `engine.protoc.openapi` annotation.
Reference links (`[Gizmo]`, `[Gizmo.name]`, `[CatalogService.GetGizmo]`, `[display text][label]`) are resolved in both:

- In CommonMark fields a resolved reference becomes a link to its operation, tag, or schema (per `referenceLinkTarget`), or an inline code span when the element has no anchor in the document — an RPC without an HTTP binding, an imported type that isn't emitted, or any schema under Swagger UI.
- Labels configured with `referenceLink=<label>=<URL>` link to that URL, so `[Status.details][google.rpc.Status]` can point at external documentation.
- Plain-text fields keep only the visible text: `Lists **every** [Gizmo] in the `catalog`.` becomes `Lists every Gizmo in the catalog.`

`catalog.proto` contains one unresolved reference (`[NoSuchThing]`) and one ambiguous one (`[Gadget]`, which also names a message in `shared.proto`).
The suite compiles the same request four ways:

- **`FAIL_ON_INVALID`** — the run fails and `CodeGeneratorResponse.error` lists both bad references.
- **`WARN` with `REDOC`** and **`WARN` with `SWAGGER_UI`** — the run succeeds, the bad references are logged, and the unresolved one renders as code (`redoc.openapi.json`, `swagger.openapi.json`).
- **`NONE`** — comments are emitted as written, brackets included (`none.openapi.json`).
