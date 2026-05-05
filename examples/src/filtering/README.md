# filtering example

Exercises the `serviceInclude` and `serviceExclude` plugin options, which control which
services from a proto file appear in the generated OpenAPI output.  Only schemas in
`components/schemas` that are reachable from an included service are emitted; types
referenced exclusively by excluded services are suppressed.

## The options

Both options accept a **Java-compatible regular expression** tested via
`Regex.containsMatchIn` against the **fully-qualified service name** — the dot-joined
concatenation of the proto package and the service identifier, e.g.
`engine.protoc.openapi.example.filtering.AlphaService`.  Because `containsMatchIn` is
used, patterns match anywhere in the name; add `^` / `$` anchors when you need exact
boundary matching.

### `serviceInclude`

| | |
|---|---|
| Type | regex string |
| Default | `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$` |

A service is **included** when its fully-qualified name matches this pattern.  The default
matches every syntactically valid fully-qualified proto identifier, so all services pass
unless you override this option.

### `serviceExclude`

| | |
|---|---|
| Type | regex string |
| Default | *(absent — no services are excluded)* |

A service is **excluded** when its fully-qualified name matches this pattern, even if it
also matched `serviceInclude`.  When this option is absent, no services are excluded by the
exclude pass.

### Evaluation order

1. Test `serviceInclude` — services that do not match are dropped immediately.
2. Test `serviceExclude` — among the services that passed step 1, those that match are
   dropped.

## Proto file

The example defines three services in the same package and proto file, each with a
dedicated pair of request/response message types:

| Service | Path | Request type | Response type |
|---------|------|--------------|---------------|
| `AlphaService` | `POST /alpha` | `AlphaRequest` | `AlphaResponse` |
| `BetaService` | `POST /beta` | `BetaRequest` | `BetaResponse` |
| `GammaService` | `POST /gamma` | `GammaRequest` | `GammaResponse` |

POST with `body: "*"` is used so that each service's request type appears in the body and
is therefore collected as a `components/schemas` entry alongside the response type.
Filtering a service out removes both its request and response schemas from the output,
making it easy to verify that schema suppression works correctly alongside path
suppression.

## Tested combinations

| Run | `serviceInclude` | `serviceExclude` | Services in output |
|-----|------------------|------------------|--------------------|
| 1 | *(default)* | *(absent)* | Alpha, Beta, Gamma |
| 2 | `AlphaService` | *(absent)* | Alpha |
| 3 | *(default)* | `BetaService` | Alpha, Gamma |
| 4 | `Alpha\|Gamma` | *(absent)* | Alpha, Gamma |
| 5 | `engine\.protoc\.openapi\.example\.filtering\.GammaService` | *(absent)* | Gamma |
| 6 | *(default)* | `AlphaService\|BetaService` | Gamma |
| 7 | `AlphaService` | *(absent)* | Alpha (unmerged mode — only one output file generated) |

Run 4 demonstrates regex alternation (`|`) for selecting multiple services in a single
pattern.  Run 5 shows an anchored full-FQN match using escaped dots.  Run 7 verifies that
filtering works identically in `merge = false` (per-service) mode — the filtered-out
service files are simply not generated.