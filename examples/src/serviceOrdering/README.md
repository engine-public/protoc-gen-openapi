# Service Ordering

Demonstrates `(engine.protoc.openapi.index_order)`, the service-level integer that controls the order in which a service's paths (and its auto-generated service tag, when `autoTagServices` is enabled) appear in the emitted OpenAPI document.

## What it exercises

Four services declared in source order, with index_order assigned (or not) per service:

| Service        | Source position | `index_order`     | Effective sort key | Position in output |
| -------------- | --------------- | ----------------- | ------------------ | ------------------ |
| AlphaService   | 0               | _none_            | 0 (encounter)      | 2nd                |
| BetaService    | 1               | -1                | -1                 | 1st                |
| GammaService   | 2               | _none_            | 2 (encounter)      | 3rd                |
| DeltaService   | 3               | 10                | 10                 | 4th                |

In the emitted document the paths appear in the order `/beta/{id}`, `/alpha/{id}`, `/gamma/{id}`, `/delta/{id}`, and the auto-generated `tags` array follows the same order.

## Rules

1. A service with `(engine.protoc.openapi.index_order)` is positioned by that integer.
2. A service without the annotation falls into its encounter ordinal across the full target file set — the first service is 0, the second is 1, and so on.
3. Negative indices place a service ahead of every un-annotated service whose encounter ordinal is `>= 0`.
4. Indices may be sparse — gaps between explicit values are not filled.
5. Ties on sort key (including ties between an explicit `index_order = N` and an un-annotated service at encounter ordinal `N`) are broken by source order.

When `merge = true`, the ordering applies across files in the target set: an explicit `index_order` on a service in file B can position it ahead of un-annotated services in file A.
