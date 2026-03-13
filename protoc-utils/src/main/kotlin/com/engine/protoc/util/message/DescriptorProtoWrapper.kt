package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public class DescriptorProtoWrapper(
    override val proto: DescriptorProtos.DescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.DescriptorProto> {

    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.DescriptorProto.NAME_FIELD_NUMBER, file)
    }

    // fields
    // extensions
    // nestedTypes
    public val enumTypes: List<EnumDescriptorProtoWrapper> by lazy {
        proto.enumTypeList.mapIndexed { index, enumProto ->
            EnumDescriptorProtoWrapper(
                enumProto,
                path + DescriptorProtos.DescriptorProto.ENUM_TYPE_FIELD_NUMBER + index,
                file,
            )
        }
    }
    // extensionRanges
    // reservedRanges
    // reservedNames
    // visibility
}
