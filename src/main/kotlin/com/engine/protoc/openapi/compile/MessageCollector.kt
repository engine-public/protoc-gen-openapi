package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.model.Schema
import com.engine.protoc.openapi.model.SchemaObject
import com.google.protobuf.DescriptorProtos

/**
 * Collects every proto message and enum type that must appear in `components/schemas`, starting
 * from explicit seeds (RPC input/output types, proto_message_ref URLs) and transitively following
 * all message-typed fields.
 *
 * Well-known types from `google.protobuf.*` are excluded — they are handled inline wherever they
 * appear (as scalars or wrapper types).
 *
 * Enum collection honours the [inlineEnums] option and the per-enum `engine.protoc.openapi.inline`
 * annotation: enums that are inlined at all usage sites are never added to [collectedEnums].
 */
internal class MessageCollector(
    private val index: MessageIndex,
    private val enumIndex: EnumIndex,
    private val inlineEnums: Boolean,
) {

    private val _collected: LinkedHashSet<String> = LinkedHashSet()
    private val _collectedEnums: LinkedHashSet<String> = LinkedHashSet()

    /** The fully-qualified message type names that should appear in `components/schemas`. */
    val collected: Set<String> get() = _collected

    /** The fully-qualified enum type names that should appear in `components/schemas`. */
    val collectedEnums: Set<String> get() = _collectedEnums

    /** All collected schema types (messages + enums) combined, for key finalization. */
    val allCollected: Set<String> get() = _collected + _collectedEnums

    /** Seed the collector with a fully-qualified RPC input/output type name. */
    fun collect(typeName: String) {
        if (typeName.isWellKnown()) return
        if (!_collected.add(typeName)) return
        val wrapper = index.find(typeName) ?: return
        if (wrapper.proto.options.mapEntry) return
        for (field in wrapper.proto.fieldList) {
            when (field.type) {
                DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> collect(field.typeName)
                DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> collectEnum(field.typeName)
                else -> {}
            }
        }
    }

    /**
     * Conditionally adds [typeName] to [collectedEnums].  Skipped when the enum is configured for
     * inlining (global [inlineEnums] option or per-enum `engine.protoc.openapi.inline` annotation).
     */
    fun collectEnum(typeName: String) {
        val wrapper = enumIndex.find(typeName) ?: return
        val annotationInline = wrapper.options?.findExtension(Annotations.inline)?.value
        val shouldInline = annotationInline ?: inlineEnums
        if (!shouldInline) {
            _collectedEnums.add(typeName)
        }
    }

    /**
     * Walk a [Schema] proto from an annotation and seed any `proto_message_ref` type URLs
     * encountered, plus any nested schema references.
     */
    fun collectFromSchema(schema: Schema?) {
        schema ?: return
        if (schema.schemaValueCase == Schema.SchemaValueCase.OBJECT) {
            collectFromSchemaObject(schema.`object`)
        }
    }

    private fun collectFromSchemaObject(obj: SchemaObject) {
        if (obj.refTypeCase == SchemaObject.RefTypeCase.PROTO_MESSAGE_REF) {
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
