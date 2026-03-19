package com.engine.protoc.util.enums

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes a single value within an enum type. */
public class EnumValueDescriptorProtoWrapper(
    override val proto: DescriptorProtos.EnumValueDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.EnumValueDescriptorProto> {

    /** The name of this enum value as written in the .proto source (e.g. `MY_ENUM_VALUE`). */
    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumValueDescriptorProto.NAME_FIELD_NUMBER, file)
    }

    /** The integer tag assigned to this enum value. */
    public val number: SyntaxElement<Int>? = (if (proto.hasNumber()) proto.number else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumValueDescriptorProto.NUMBER_FIELD_NUMBER, file)
    }

    /** Options set on this enum value, or null if none were specified. */
    public val options: EnumValueOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            EnumValueOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.EnumValueDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }
}
