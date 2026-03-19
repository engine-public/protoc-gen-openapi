package com.engine.protoc.util.service

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes a service. */
public class ServiceDescriptorProtoWrapper(
    override val proto: DescriptorProtos.ServiceDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.ServiceDescriptorProto> {

    /** The unqualified name of this service as written in the .proto source. */
    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.ServiceDescriptorProto.NAME_FIELD_NUMBER, file)
    }

    /** The RPC methods declared in this service, in source order. */
    public val methods: List<MethodDescriptorProtoWrapper> by lazy {
        proto.methodList.mapIndexed { index, methodProto ->
            MethodDescriptorProtoWrapper(
                methodProto,
                path + DescriptorProtos.ServiceDescriptorProto.METHOD_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /** Options set on this service, or null if none were specified. */
    public val options: ServiceOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            ServiceOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.ServiceDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }
}
