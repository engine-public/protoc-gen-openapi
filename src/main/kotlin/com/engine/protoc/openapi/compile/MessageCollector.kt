package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Schema
import com.engine.protoc.openapi.SchemaObject
import com.google.protobuf.DescriptorProtos

/**
 * Collects every proto message type that must appear in `components/schemas`, starting from
 * explicit seeds (RPC input/output types, proto_message_ref URLs) and transitively following all
 * message-typed fields.
 *
 * Well-known types from `google.protobuf.*` are excluded — they are handled inline wherever they
 * appear (as scalars or wrapper types).
 */
internal class MessageCollector(private val index: MessageIndex) {

    private val _collected: LinkedHashSet<String> = LinkedHashSet()

    /** The fully-qualified type names that should appear in `components/schemas`. */
    val collected: Set<String> get() = _collected

    /** Seed the collector with a fully-qualified RPC input/output type name. */
    fun collect(typeName: String) {
        if (typeName.isWellKnown()) return
        if (!_collected.add(typeName)) return
        val wrapper = index.find(typeName) ?: return
        // If this is a map-entry synthetic message, skip it.
        if (wrapper.proto.options.mapEntry) return
        for (field in wrapper.proto.fieldList) {
            if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                collect(field.typeName)
            }
        }
    }

    /**
     * Walk a [Schema] proto from an annotation and seed any `proto_message_ref` type URLs encountered,
     * plus any nested schema references.
     */
    fun collectFromSchema(schema: Schema?) {
        schema ?: return
        if (schema.schemaValueCase == Schema.SchemaValueCase.OBJECT) {
            collectFromSchemaObject(schema.`object`)
        }
    }

    private fun collectFromSchemaObject(obj: SchemaObject) {
        if (obj.refTypeCase == SchemaObject.RefTypeCase.PROTO_MESSAGE_REF) {
            // type URL is like "type.googleapis.com/swagger.Pet" → ".swagger.Pet"
            val typeName = ".${obj.protoMessageRef.typeUrl.substringAfterLast('/')}"
            collect(typeName)
        }
        obj.propertiesMap.values.forEach { collectFromSchema(it) }
        if (obj.hasItems()) collectFromSchema(obj.items)
        obj.allOfList.forEach { collectFromSchema(it) }
        obj.anyOfList.forEach { collectFromSchema(it) }
        obj.oneOfList.forEach { collectFromSchema(it) }
        if (obj.hasNot()) collectFromSchema(obj.not)
        if (obj.hasIfSchema()) collectFromSchema(obj.ifSchema)
        if (obj.hasThenSchema()) collectFromSchema(obj.thenSchema)
        if (obj.hasElseSchema()) collectFromSchema(obj.elseSchema)
    }

    private fun String.isWellKnown(): Boolean = startsWith(".google.protobuf.")
}
