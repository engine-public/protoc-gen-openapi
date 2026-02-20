package com.engine.protoc.util.enums

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes an enum type. */
public class EnumDescriptorProtoWrapper(
    override val proto: DescriptorProtos.EnumDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.EnumDescriptorProto> {

    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumDescriptorProto.NAME_FIELD_NUMBER, file)
    }
    public val values: List<EnumValueDescriptorProtoWrapper> by lazy {
        proto.valueList.mapIndexed { index, valueProto ->
            EnumValueDescriptorProtoWrapper(
                valueProto,
                path + DescriptorProtos.EnumDescriptorProto.VALUE_FIELD_NUMBER + index,
                file,
            )
        }
    }
    public val options: EnumOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            EnumOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.EnumDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }

    /**
     * Range of reserved numeric values. Reserved numeric values may not be used
     * by enum values in the same enum declaration. Reserved ranges may not
     * overlap.
     */
    public val reservedRanges: List<SyntaxElement<DescriptorProtos.EnumDescriptorProto.EnumReservedRange>> by lazy {
        proto.reservedRangeList.mapIndexed { index, range ->
            SyntaxElement(
                range,
                path + DescriptorProtos.EnumDescriptorProto.RESERVED_RANGE_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /** Reserved enum value names, which may not be reused. A given name may only be reserved once. */
    public val reservedNames: List<SyntaxElement<String>> by lazy {
        proto.reservedNameList.mapIndexed { index, reservedName ->
            SyntaxElement(
                reservedName,
                path + DescriptorProtos.EnumDescriptorProto.RESERVED_NAME_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
