package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.ProtocGenOpenAPI
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode

/**
 * Computes schema keys for `components/schemas` entries and their corresponding `$ref` strings.
 *
 * For [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.NONE] the key is always the simple
 * unqualified message name (current default behaviour).
 *
 * For [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.FULL_PACKAGE] the key includes all
 * package segments and is stable — [buildPhaseKeyOf] and [keyOf] return the same value.
 *
 * For [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE] the final key
 * cannot be known until all schemas are collected (the common prefix is computed over the full
 * set).  During path building, [buildPhaseKeyOf] returns a temporary FULL_PACKAGE key.  After
 * calling [finalize] with the complete collected set, [rewriteRefs] replaces the temporary keys
 * in the already-built JSON tree and [keyOf] returns the final simplified key.
 */
internal class SchemaKeyResolver(
    private val strategy: ProtocGenOpenAPI.Options.SchemaNamespaceStrategy,
    private val separator: ProtocGenOpenAPI.Options.SchemaNamespaceSeparator,
    private val casing: ProtocGenOpenAPI.Options.SchemaNamespaceCasing,
    private val versionExtraction: Boolean,
    private val setSchemaTitleToProtoSimpleName: Boolean,
    private val messageIndex: MessageIndex,
    private val enumIndex: EnumIndex,
) {
    private val versionRegex = Regex("""^v\d+[a-zA-Z0-9]*$""")

    // Matches a Redoc schema-section anchor `#tag/{key}`; the key uses the schema-key alphabet.
    private val schemaAnchorRegex = Regex("""#tag/([A-Za-z0-9_.\-]+)""")

    // Populated by finalize() only for SIMPLIFIED_PACKAGE.
    private var simplifiedKeyMap: Map<String, String> = emptyMap()

    // Maps a FULL_PACKAGE build-phase key to the final simplified key, for rewriteRefs().
    private var buildPhaseToFinalMap: Map<String, String> = emptyMap()

    /**
     * Returns the schema key to embed in `$ref` strings while the JSON paths tree is being built.
     *
     * For [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE] this is a
     * temporary FULL_PACKAGE key; call [rewriteRefs] after [finalize] to replace it.
     */
    fun buildPhaseKeyOf(typeName: String): String =
        when (strategy) {
            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.NONE ->
                localPathSegments(typeName).joinToString(separator.asString())

            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.FULL_PACKAGE,
            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE,
            ->
                applyTransform(typeName.removePrefix(".").split("."))
        }

    /**
     * Computes the final simplified key map from [collectedTypes].
     * No-op for strategies other than [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE].
     * Must be called before [keyOf] or [rewriteRefs] produce correct results for that strategy.
     *
     * Named `finalizeKeys` (not `finalize`) to avoid shadowing [Object.finalize].
     */
    fun finalizeKeys(collectedTypes: Set<String>) {
        if (strategy != ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE) return
        val prefix = commonPackagePrefix(collectedTypes)
        simplifiedKeyMap = collectedTypes.associateWith { computeSimplifiedKey(it, prefix) }
        buildPhaseToFinalMap = collectedTypes.associateBy(
            keySelector = { applyTransform(it.removePrefix(".").split(".")) },
            valueTransform = { simplifiedKeyMap[it]!! },
        )
    }

    /**
     * Returns the `title` to embed in the schema object for [typeName], or `null` if
     * [setSchemaTitleToProtoSimpleName] is `false`.  When non-null the caller sets this as the
     * initial `title`; any annotation that also sets `title` will overwrite it via deep-merge.
     *
     * Works for both message and enum type names.
     */
    fun titleFor(typeName: String): String? {
        if (!setSchemaTitleToProtoSimpleName) return null
        return typeName.substringAfterLast('.')
    }

    /** Returns the final schema key for use in `components/schemas` (after [finalize]). */
    fun keyOf(typeName: String): String =
        when (strategy) {
            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.NONE ->
                localPathSegments(typeName).joinToString(separator.asString())

            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.FULL_PACKAGE ->
                applyTransform(typeName.removePrefix(".").split("."))

            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE ->
                simplifiedKeyMap[typeName] ?: computeSimplifiedKey(typeName, "")
        }

    /**
     * Walks [root] and rewrites every `$ref` value that contains a FULL_PACKAGE build-phase key
     * to the corresponding final simplified key.
     * No-op for strategies other than [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE].
     */
    fun rewriteRefs(root: JsonNode) {
        if (strategy != ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.SIMPLIFIED_PACKAGE ||
            buildPhaseToFinalMap.isEmpty()
        ) {
            return
        }
        rewriteTree(root)
    }

    private fun rewriteTree(node: JsonNode) {
        when (node) {
            is ObjectNode -> {
                val ref = node.get("\$ref")?.asString()
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    val oldKey = ref.removePrefix("#/components/schemas/")
                    val newKey = buildPhaseToFinalMap[oldKey]
                    if (newKey != null) node.put("\$ref", "#/components/schemas/$newKey")
                }
                // Reference-link anchors emitted into `description` text under REDOC mode point at
                // a schema's generated `<SchemaDefinition>` section via `#tag/{buildPhaseKey}`;
                // rewrite those to the final simplified key so they match the emitted section tag.
                for ((key, child) in node.properties().toList()) {
                    if (child is StringNode) {
                        rewriteSchemaAnchors(child.asString())?.let { node.put(key, it) }
                    } else {
                        rewriteTree(child)
                    }
                }
            }

            is ArrayNode -> for (child in node) rewriteTree(child)
        }
    }

    /**
     * Rewrites every `#tag/{buildPhaseKey}` anchor in [text] to `#tag/{finalKey}` for keys that
     * the `SIMPLIFIED_PACKAGE` strategy renamed.  Returns `null` when nothing changed (the common
     * case) so callers can skip the write.  Service-name tags are not build-phase keys, so they
     * are left untouched.
     */
    private fun rewriteSchemaAnchors(text: String): String? {
        if (!text.contains("#tag/")) return null
        val rewritten = schemaAnchorRegex.replace(text) { match ->
            buildPhaseToFinalMap[match.groupValues[1]]?.let { "#tag/$it" } ?: match.value
        }
        return rewritten.takeIf { it != text }
    }

    /**
     * Returns the segments of [typeName] that are local to the file (i.e., everything after
     * stripping the proto package prefix).  For top-level types this is `["Status"]`; for nested
     * types like `.pkg.Order.Status` it is `["Order", "Status"]`.
     *
     * Used by the [ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.NONE] strategy so that nested
     * types produce unique keys even without package namespacing.
     */
    private fun localPathSegments(typeName: String): List<String> {
        val pkg = packageOf(typeName)
        val fullName = typeName.removePrefix(".")
        return if (pkg.isEmpty()) fullName.split(".") else fullName.removePrefix("$pkg.").split(".")
    }

    /**
     * Returns the proto package for [typeName], checking messages first then enums.
     */
    private fun packageOf(typeName: String): String =
        messageIndex.packageOf(typeName).takeIf { it.isNotEmpty() }
            ?: enumIndex.packageOf(typeName)

    private fun computeSimplifiedKey(
        typeName: String,
        commonPrefix: String,
    ): String {
        val protoPackage = packageOf(typeName)
        // Extract all type-path segments (enclosing type names + simple name) by removing the
        // file-level proto package from the full type name.  For a top-level type like
        // ".pkg.Item" this yields ["Item"]; for a nested type like ".pkg.Outer.Inner" this
        // yields ["Outer", "Inner"], preserving the enclosing type name.
        val typePathSegments = localPathSegments(typeName)
        val remaining = when {
            commonPrefix.isEmpty() -> protoPackage
            protoPackage == commonPrefix -> ""
            protoPackage.startsWith("$commonPrefix.") -> protoPackage.removePrefix("$commonPrefix.")
            else -> protoPackage
        }
        val segments = (if (remaining.isEmpty()) emptyList() else remaining.split(".")) +
            typePathSegments
        return applyTransform(segments)
    }

    private fun applyTransform(allSegments: List<String>): String {
        val (versions, nonVersions) = if (versionExtraction) {
            allSegments.partition { versionRegex.matches(it) }
        } else {
            emptyList<String>() to allSegments
        }

        val displaySegments = when (casing) {
            ProtocGenOpenAPI.Options.SchemaNamespaceCasing.NONE -> nonVersions

            ProtocGenOpenAPI.Options.SchemaNamespaceCasing.CAPITALIZED ->
                nonVersions.map { it.replaceFirstChar { c -> c.uppercase() } }

            ProtocGenOpenAPI.Options.SchemaNamespaceCasing.UPPER_CASE ->
                nonVersions.map { it.uppercase() }
        }

        val sep = separator.asString()
        val base = displaySegments.joinToString(sep)
        return if (versions.isEmpty()) base else "$base$sep${versions.joinToString(sep)}"
    }

    private fun commonPackagePrefix(types: Set<String>): String = commonDotPrefix(types.map { packageOf(it) })
}

/** Returns the longest dot-segment prefix shared by all non-empty strings in [packages]. */
internal fun commonDotPrefix(packages: Iterable<String>): String {
    val nonEmpty = packages.filter { it.isNotEmpty() }
    if (nonEmpty.isEmpty()) return ""
    val parts = nonEmpty.map { it.split(".") }
    val result = mutableListOf<String>()
    for (i in parts[0].indices) {
        if (parts.all { it.size > i && it[i] == parts[0][i] }) result += parts[0][i] else break
    }
    return result.joinToString(".")
}

private fun ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.asString(): String =
    when (this) {
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.NONE -> ""
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.UNDERSCORE -> "_"
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DASH -> "-"
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DOT -> "."
    }
