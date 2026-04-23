package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.toJson
import com.engine.protoc.openapi.model.Schema
import com.engine.protoc.util.message.DescriptorProtoWrapper
import com.engine.protoc.util.message.FieldDescriptorProtoWrapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.DescriptorProtos

/**
 * Builds the `components/schemas` section from all message types identified by [collector].
 *
 * For each collected type the schema is derived by:
 * 1. Generating a default schema from the proto message descriptor (fields → JSON Schema properties)
 * 2. Deep-merging any `engine.protoc.openapi.message` annotation on top
 *
 * Individual fields follow the same pattern: auto-generated type schema merged with any
 * `engine.protoc.openapi.field` annotation.
 */
internal class SchemaBuilder(
    private val ctx: JsonContext,
    private val pathsBuilder: PathsBuilder,
) {

    /** Returns an ObjectNode containing all component schemas (messages and enums) keyed by schema name. */
    fun build(collector: MessageCollector): ObjectNode {
        val schemas = ctx.obj()
        for (typeName in collector.collected) {
            val wrapper = ctx.messageIndex.find(typeName) ?: continue
            if (wrapper.proto.options.mapEntry) continue
            val schemaKey = ctx.schemaKeyResolver.keyOf(typeName)
            schemas.set<JsonNode>(schemaKey, buildMessageSchema(wrapper, typeName))
        }
        for (typeName in collector.collectedEnums) {
            val wrapper = ctx.enumIndex.find(typeName) ?: continue
            val schemaKey = ctx.schemaKeyResolver.keyOf(typeName)
            schemas.set<JsonNode>(schemaKey, pathsBuilder.buildInlineEnumSchema(typeName, wrapper))
        }
        return schemas
    }

    private fun buildMessageSchema(
        wrapper: DescriptorProtoWrapper,
        typeName: String,
    ): ObjectNode {
        val base = ctx.obj()
        base.put("type", "object")
        ctx.schemaKeyResolver.titleFor(typeName)?.let { base.put("title", it) }

        // ---- Leading comment → description ------------------------------
        val comment = wrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        if (comment != null) base.put("description", comment)

        // ---- Properties from fields -------------------------------------
        val required = mutableListOf<String>()
        val propsNode = ctx.obj()
        for (fieldWrapper in wrapper.fields) {
            val field = fieldWrapper.proto
            val jsonName = field.jsonName.ifEmpty { field.name }
            val fieldSchema = buildFieldSchema(fieldWrapper)
            propsNode.set<JsonNode>(jsonName, fieldSchema)

            // proto3 required: fields inside a oneof, or proto2 LABEL_REQUIRED
            if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED) {
                required += jsonName
            }
        }
        if (propsNode.size() > 0) base.set<JsonNode>("properties", propsNode)
        if (required.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (r in required) arr.add(r)
            base.set<JsonNode>("required", arr)
        }

        // ---- engine.protoc.openapi.message annotation override ----------
        val annotation = wrapper.options
            ?.findExtension(Annotations.message)?.value

        return if (annotation != null && annotation.schemaValueCase ==
            Schema.SchemaValueCase.OBJECT
        ) {
            with(ctx) { base.deepMerge(annotation.`object`.toJson(ctx)) }
        } else {
            base
        }
    }

    private fun buildFieldSchema(fieldWrapper: FieldDescriptorProtoWrapper): ObjectNode {
        val field = fieldWrapper.proto
        val base = pathsBuilder.fieldTypeSchema(field)

        // ---- Leading comment → description -----------------------------
        val comment = fieldWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        if (comment != null && !base.has("description")) {
            base.put("description", comment)
        }

        // ---- engine.protoc.openapi.field annotation override -----------
        val annotation = fieldWrapper.options
            ?.findExtension(Annotations.field)?.value

        return if (annotation != null && annotation.schemaValueCase ==
            Schema.SchemaValueCase.OBJECT
        ) {
            with(ctx) { base.deepMerge(annotation.`object`.toJson(ctx)) }
        } else {
            base
        }
    }
}
