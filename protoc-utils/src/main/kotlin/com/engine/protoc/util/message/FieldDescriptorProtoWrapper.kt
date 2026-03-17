package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes a field within a message, or an extension field. */
public class FieldDescriptorProtoWrapper(
    override val proto: DescriptorProtos.FieldDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.FieldDescriptorProto> {

    /** The unqualified name of the field as written in the .proto source. */
    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.NAME_FIELD_NUMBER, file)
    }

    /** The field number as written in the .proto source. */
    public val number: SyntaxElement<Int>? = (if (proto.hasNumber()) proto.number else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.NUMBER_FIELD_NUMBER, file)
    }

    /**
     * The field's cardinality label (optional, required, or repeated).
     * Note: `required` is only allowed in proto2.
     */
    public val label: SyntaxElement<DescriptorProtos.FieldDescriptorProto.Label>? =
        (if (proto.hasLabel()) proto.label else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.LABEL_FIELD_NUMBER, file)
        }

    /**
     * The scalar wire type of the field.
     * If [typeName] is set, this need not be set. If both this and [typeName]
     * are set, this must be one of TYPE_ENUM, TYPE_MESSAGE or TYPE_GROUP.
     */
    public val type: SyntaxElement<DescriptorProtos.FieldDescriptorProto.Type>? =
        (if (proto.hasType()) proto.type else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.TYPE_FIELD_NUMBER, file)
        }

    /**
     * For message and enum types, this is the name of the type.  If the name
     * starts with a '.', it is fully-qualified.  Otherwise, C++-like scoping
     * rules are used to find the type (i.e. first the nested types within this
     * message are searched, then within the parent, on up to the root
     * namespace).
     */
    public val typeName: SyntaxElement<String>? =
        (if (proto.hasTypeName()) proto.typeName else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.TYPE_NAME_FIELD_NUMBER, file)
        }

    /**
     * For extensions, this is the name of the type being extended.  It is
     * resolved in the same manner as [typeName].
     */
    public val extendee: SyntaxElement<String>? =
        (if (proto.hasExtendee()) proto.extendee else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.EXTENDEE_FIELD_NUMBER, file)
        }

    /**
     * For numeric types, contains the original text representation of the value.
     * For booleans, "true" or "false".
     * For strings, contains the default text contents (not escaped in any way).
     * For bytes, contains the C escaped value.  All bytes >= 128 are escaped.
     */
    public val defaultValue: SyntaxElement<String>? =
        (if (proto.hasDefaultValue()) proto.defaultValue else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.DEFAULT_VALUE_FIELD_NUMBER, file)
        }

    /**
     * If set, gives the index of a oneof in the containing type's oneof_decl
     * list.  This field is a member of that oneof.
     */
    public val oneofIndex: SyntaxElement<Int>? =
        (if (proto.hasOneofIndex()) proto.oneofIndex else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.ONEOF_INDEX_FIELD_NUMBER, file)
        }

    /**
     * JSON name of this field. The value is set by protocol compiler. If the
     * user has set a "json_name" option on this field, that option's value
     * will be used. Otherwise, it's deduced from the field's name by converting
     * it to camelCase.
     */
    public val jsonName: SyntaxElement<String>? =
        (if (proto.hasJsonName()) proto.jsonName else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.JSON_NAME_FIELD_NUMBER, file)
        }

    public val options: FieldOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            FieldOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.FieldDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }

    /**
     * If true, this is a proto3 "optional". When a proto3 field is optional, it
     * tracks presence regardless of field type.
     *
     * When proto3_optional is true, this field must belong to a oneof to signal
     * to old proto3 clients that presence is tracked for this field. This oneof
     * is known as a "synthetic" oneof, and this field must be its sole member
     * (each proto3 optional field gets its own synthetic oneof). Synthetic oneofs
     * exist in the descriptor only, and do not generate any API. Synthetic oneofs
     * must be ordered after all "real" oneofs.
     *
     * For message fields, proto3_optional doesn't create any semantic change,
     * since non-repeated message fields always track presence. However it still
     * indicates the semantic detail of whether the user wrote "optional" or not.
     * This can be useful for round-tripping the .proto file. For consistency we
     * give message fields a synthetic oneof also, even though it is not required
     * to track presence. This is especially important because the parser can't
     * tell if a field is a message or an enum, so it must always create a
     * synthetic oneof.
     *
     * Proto2 optional fields do not set this flag, because they already indicate
     * optional with `LABEL_OPTIONAL`.
     */
    public val proto3Optional: SyntaxElement<Boolean>? =
        (if (proto.hasProto3Optional()) proto.proto3Optional else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldDescriptorProto.PROTO3_OPTIONAL_FIELD_NUMBER, file)
        }
}
