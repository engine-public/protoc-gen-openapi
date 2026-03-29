import com.fasterxml.jackson.databind.JsonNode

/** Represents a single difference between two JSON trees at the given [path]. */
public data class JsonDiff(val path: String, val expected: JsonNode?, val actual: JsonNode?)

/**
 * Recursively diffs [expected] against [actual], returning one [JsonDiff] per divergent node.
 *
 * Paths in [ignoredPath] (expressed as JSONPath strings, e.g. `"$.openapi"`) are excluded from
 * the results. A missing field in [actual] whose [expected] value is an empty array is treated as
 * equivalent (OpenAPI semantics: omitting `parameters` is the same as `parameters: []`).
 */
public fun collectJsonDiffs(
    expected: JsonNode,
    actual: JsonNode,
    vararg ignoredPath: String,
): List<JsonDiff> {
    val ignoredPaths = ignoredPath.toSet()
    return collectJsonDiffs("$", expected, actual).filterNot { ignoredPaths.contains(it.path) }
}

/**
 * Recursively walks [expected] and [actual] starting at [path], collecting any divergences.
 * This overload is intended for recursive calls; prefer the top-level overload for test assertions.
 */
public fun collectJsonDiffs(
    path: String,
    expected: JsonNode,
    actual: JsonNode,
): List<JsonDiff> {
    val diffs = mutableListOf<JsonDiff>()
    when {
        expected.isObject && actual.isObject -> {
            val allFields = (expected.fieldNames().asSequence() + actual.fieldNames().asSequence()).toSet()
            for (field in allFields) {
                val exp = expected.get(field)
                val act = actual.get(field)
                when {
                    exp == null -> diffs.add(JsonDiff("$path.$field", null, act))

                    // A missing field is semantically equivalent to an empty array in OpenAPI
                    // (e.g. `parameters: []` and omitting `parameters` are identical).
                    act == null && exp.isArray && exp.size() == 0 -> {}

                    act == null -> diffs.add(JsonDiff("$path.$field", exp, null))

                    else -> diffs.addAll(collectJsonDiffs("$path.$field", exp, act))
                }
            }
        }

        expected.isArray && actual.isArray -> {
            if (expected.size() != actual.size()) {
                diffs.add(JsonDiff(path, expected, actual))
            } else {
                for (i in 0 until expected.size()) {
                    diffs.addAll(collectJsonDiffs("$path[$i]", expected[i], actual[i]))
                }
            }
        }

        expected != actual -> diffs.add(JsonDiff(path, expected, actual))
    }
    return diffs
}
