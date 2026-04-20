# Version option

Exercises the `options.version` feature: a nullable string that is written to `info.version` of
every generated document that does not already have a version supplied by an engine annotation.

The example uses two services in a single proto file to cover all four combinations of
(options version present / absent) × (annotation version present / absent).

## What it exercises

**`options.version` as a global fallback.**  When set, the value is injected into `info.version`
before any annotation layers run.  Services that carry no engine annotation — and would otherwise
produce an OAS document missing the required `info.version` field — receive the options value
automatically.  This is the recommended way to produce fully-valid OAS 3.1 output for services
annotated only with `google.api.http`.

**Annotation version takes precedence.**  `PinnedVersionService` carries an explicit
`engine.protoc.openapi.service` annotation with `info.version = "pinned-1.0.0"`.  Because
service-level annotations are applied as the highest-priority layer (after the options value is
written), the annotation's version always overwrites the fallback.  Setting `options.version`
has no visible effect on this service.

**Absent options version.**  When `options.version` is `null` (the default), no fallback is
injected.  `UnversionedService` emits an `info` object with only `title` and `description`; the
`version` key is entirely absent from the output.

## Priority layering (lowest → highest)

| Priority | Source | Field set |
|---|---|---|
| 1 | Service-derived attributes | `info.title`, `info.description` |
| 2 | `options.version` | `info.version` |
| 3 | File-level `engine.protoc.openapi.file` annotation | any field |
| 4 | Service-level `engine.protoc.openapi.service` annotation | any field |

## Test structure

The test runs the plugin twice on the same `code-generator-request.binpb`:

1. **With `version = "global-2.0.0"`** — both documents have `info.version`; `validateOutput = true`
   is used and the full output is compared against checked-in reference files.
2. **Without a version option** — `UnversionedService` has no `info.version`; `validateOutput = false`
   is used and only the version-specific assertions are checked inline.

## Peculiarities

The reference files (`*.openapi.json` in `resources/`) correspond to the **with-version** run only.
The without-version run produces structurally identical documents except that `UnversionedService`
lacks `info.version`; rather than maintaining a second set of reference files for a one-field
difference, the test asserts that property directly.