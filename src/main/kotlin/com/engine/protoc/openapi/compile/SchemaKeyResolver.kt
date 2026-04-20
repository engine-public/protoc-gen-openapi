package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

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
    private val setSchemaTitleToMessageName: Boolean,
    private val messageIndex: MessageIndex,
) {
    private val versionRegex = Regex("""^v\d+[a-zA-Z0-9]*$""")

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
                messageIndex.simpleNameOf(typeName)

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
     * [setSchemaTitleToMessageName] is `false`.  When non-null the caller sets this as the
     * initial `title`; any `engine.protoc.openapi.message` annotation that also sets `title`
     * will overwrite it via deep-merge.
     */
    fun titleFor(typeName: String): String? {
        if (!setSchemaTitleToMessageName) return null
        return messageIndex.simpleNameOf(typeName)
    }

    /** Returns the final schema key for use in `components/schemas` (after [finalize]). */
    fun keyOf(typeName: String): String =
        when (strategy) {
            ProtocGenOpenAPI.Options.SchemaNamespaceStrategy.NONE ->
                messageIndex.simpleNameOf(typeName)

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
                val ref = node.get("\$ref")?.asText()
                if (ref != null && ref.startsWith("#/components/schemas/")) {
                    val oldKey = ref.removePrefix("#/components/schemas/")
                    val newKey = buildPhaseToFinalMap[oldKey]
                    if (newKey != null) node.put("\$ref", "#/components/schemas/$newKey")
                }
                for ((_, child) in node.fields()) rewriteTree(child)
            }

            is ArrayNode -> for (child in node) rewriteTree(child)
        }
    }

    private fun computeSimplifiedKey(
        typeName: String,
        commonPrefix: String,
    ): String {
        val protoPackage = messageIndex.packageOf(typeName)
        // Extract all message-path segments (enclosing type names + simple name) by removing the
        // file-level proto package from the full type name.  For a top-level type like
        // ".pkg.Item" this yields ["Item"]; for a nested type like ".pkg.Outer.Inner" this
        // yields ["Outer", "Inner"], preserving the enclosing type name that simpleNameOf drops.
        val fullName = typeName.removePrefix(".")
        val messagePathSegments = if (protoPackage.isEmpty()) {
            fullName.split(".")
        } else {
            fullName.removePrefix("$protoPackage.").split(".")
        }
        val remaining = when {
            commonPrefix.isEmpty() -> protoPackage
            protoPackage == commonPrefix -> ""
            protoPackage.startsWith("$commonPrefix.") -> protoPackage.removePrefix("$commonPrefix.")
            else -> protoPackage
        }
        val segments = (if (remaining.isEmpty()) emptyList() else remaining.split(".")) +
            messagePathSegments
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

    private fun commonPackagePrefix(types: Set<String>): String {
        val packages = types
            .map { messageIndex.packageOf(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        if (packages.isEmpty()) return ""
        val parts = packages.map { it.split(".") }
        val result = mutableListOf<String>()
        for (i in parts[0].indices) {
            if (parts.all { it.size > i && it[i] == parts[0][i] }) result += parts[0][i] else break
        }
        return result.joinToString(".")
    }
}

private fun ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.asString(): String =
    when (this) {
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.NONE -> ""
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.UNDERSCORE -> "_"
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DASH -> "-"
        ProtocGenOpenAPI.Options.SchemaNamespaceSeparator.DOT -> "."
    }
