package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes a oneof. */
public class OneofDescriptorProtoWrapper(
    override val proto: DescriptorProtos.OneofDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.OneofDescriptorProto> {

    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.OneofDescriptorProto.NAME_FIELD_NUMBER, file)
    }

    public val options: OneofOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            OneofOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.OneofDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }
}
