# Service Ordering

Demonstrates `(engine.protoc.openapi.index_order)`, the service-level integer that controls the order in which a service's paths (and its auto-generated service tag, when `autoTagServices` is enabled) appear in the emitted OpenAPI document.

## What it exercises

Five services declared in source order, with index_order assigned (or not) per service:

| Service         | Source position | `index_order`     | Effective sort key | Position in output |
| --------------- | --------------- | ----------------- | ------------------ | ------------------ |
| AlphaService    | 0               | _none_            | 0 (encounter)      | 2nd                |
| BetaService     | 1               | -1                | -1                 | 1st                |
| GammaService    | 2               | _none_            | 2 (encounter)      | 4th                |
| DeltaService    | 3               | 10                | 10                 | 5th                |
| EpsilonService  | 4               | 0                 | 0 (collides)       | 3rd                |

`EpsilonService`'s explicit `index_order = 0` collides with `AlphaService`'s implicit encounter ordinal of 0.
The compiler emits a `WARN` naming both services and the contested index, then falls back to stable sort by source position — `AlphaService` keeps the earlier slot because it appears first in the file.

The emitted paths appear in the order `/beta/{id}`, `/alpha/{id}`, `/epsilon/{id}`, `/gamma/{id}`, `/delta/{id}`, and the auto-generated `tags` array follows the same order.

## Rules

1. A service with `(engine.protoc.openapi.index_order)` is positioned by that integer.
2. A service without the annotation falls into its encounter ordinal across the full target file set — the first service is 0, the second is 1, and so on.
3. Negative indices place a service ahead of every un-annotated service whose encounter ordinal is `>= 0`.
4. Indices may be sparse — gaps between explicit values are not filled.
5. Ties on sort key (including ties between an explicit `index_order = N` and an un-annotated service at encounter ordinal `N`) are broken by source order — and a `WARN` is logged naming the conflicting services and the contested index, so an unintended collision is visible without changing the output.

When `merge = true`, the ordering applies across files in the target set: an explicit `index_order` on a service in file B can position it ahead of un-annotated services in file A.
