package com.engine.protoc.openapi.compile.json

import com.engine.protoc.openapi.compile.MessageIndex
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/** Shared state passed through all serialization functions. */
internal class JsonContext(
    val mapper: ObjectMapper,
    val messageIndex: MessageIndex,
) {
    fun obj(): ObjectNode = mapper.createObjectNode()

    /**
     * Deep-merges [other] into this node.  Object nodes are recursively merged; all other values
     * are overwritten by the value in [other].
     */
    fun ObjectNode.deepMerge(other: ObjectNode): ObjectNode {
        for ((key, value) in other.fields()) {
            val existing = get(key)
            if (existing is ObjectNode && value is ObjectNode) {
                existing.deepMerge(value)
            } else {
                set<ObjectNode>(key, value)
            }
        }
        return this
    }

    /**
     * Resolves a `proto_message_ref` type URL to an OpenAPI `$ref` string pointing into
     * `#/components/schemas`.
     *
     * The type URL format is `type.googleapis.com/<package>.<MessageName>`.
     */
    fun resolveProtoRef(typeUrl: String): String {
        val simpleName = messageIndex.simpleNameOf(".${typeUrl.substringAfterLast('/')}")
        return "#/components/schemas/$simpleName"
    }

    /** Returns an inline JSON Schema for a well-known protobuf scalar wrapper type, or null. */
    fun wellKnownScalarSchema(typeName: String): ObjectNode? =
        when (typeName) {
            ".google.protobuf.StringValue" -> scalar("string")
            ".google.protobuf.Int32Value" -> scalar("integer", "int32")
            ".google.protobuf.Int64Value" -> scalar("integer", "int64")
            ".google.protobuf.UInt32Value" -> scalar("integer", "int32")
            ".google.protobuf.UInt64Value" -> scalar("integer", "int64")
            ".google.protobuf.FloatValue" -> scalar("number", "float")
            ".google.protobuf.DoubleValue" -> scalar("number", "double")
            ".google.protobuf.BoolValue" -> scalar("boolean")
            ".google.protobuf.BytesValue" -> scalar("string", "byte")
            ".google.protobuf.Duration" -> scalar("string", "duration")
            ".google.protobuf.Timestamp" -> scalar("string", "date-time")
            else -> null
        }

    private fun scalar(
        type: String,
        format: String? = null,
    ): ObjectNode {
        val node = obj()
        node.put("type", type)
        if (format != null) node.put("format", format)
        return node
    }
}
