package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.compile.json.JsonContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * Builds the `components/schemas` section from all message types identified by [collector].
 *
 * The per-message schema construction lives on [PathsBuilder] (see `buildMessageSchema` and
 * `buildFieldSchema`) so that the same logic can also produce inline-expanded schemas at request
 * and response boundaries marked with `inline_request_schema` / `inline_response_schema`.  This
 * class is the outer loop that decides which messages and enums get a component entry.
 */
internal class SchemaBuilder(
    private val ctx: JsonContext,
    private val pathsBuilder: PathsBuilder,
) {

    /**
     * Returns an ObjectNode containing all component schemas (messages and enums) keyed by
     * schema name.  Entries are emitted in alphabetical order of the final schema key, so the
     * output is stable across runs regardless of the order in which types were first reached
     * during collection.
     */
    fun build(collector: MessageCollector): ObjectNode {
        // Collect into a sorted map so the final emission order is alphabetical by schema key.
        // [ctx.schemaKeyResolver.keyOf] already returns the final key (SIMPLIFIED_PACKAGE
        // resolution happens in [SchemaKeyResolver.finalizeKeys], which Compiler calls before
        // invoking us), so sorting here matches the names that will appear in the output.
        val sorted = sortedMapOf<String, JsonNode>()
        for (typeName in collector.collected) {
            val wrapper = ctx.messageIndex.find(typeName) ?: continue
            if (wrapper.proto.options.mapEntry) continue
            val schemaKey = ctx.schemaKeyResolver.keyOf(typeName)
            sorted[schemaKey] = pathsBuilder.buildMessageSchema(wrapper, typeName)
        }
        for (typeName in collector.collectedEnums) {
            val wrapper = ctx.enumIndex.find(typeName) ?: continue
            val schemaKey = ctx.schemaKeyResolver.keyOf(typeName)
            sorted[schemaKey] = pathsBuilder.buildEnumSchema(typeName, wrapper, includeTitle = true)
        }
        if (ctx.convertGrpcStatus || pathsBuilder.requiresGrpcStatus) {
            sorted["google.rpc.Status"] = buildGrpcStatusSchema()
        }

        val schemas = ctx.obj()
        for ((key, value) in sorted) {
            schemas.set(key, value)
        }
        return schemas
    }

    private fun buildGrpcStatusSchema(): ObjectNode {
        val schema = ctx.obj()
        schema.put("type", "object")
        val props = ctx.obj()
        props.set(
            "code",
            ctx.obj().also {
                it.put("type", "integer")
                it.put("format", "int32")
            },
        )
        props.set("message", ctx.obj().also { it.put("type", "string") })
        val details = ctx.obj()
        details.put("type", "array")
        details.set("items", ctx.obj().also { it.put("type", "object") })
        props.set("details", details)
        schema.set("properties", props)
        return schema
    }
}
