package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractGeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.google.protobuf.DescriptorProtos

public class DescriptorProtoWrapper(
    proto: DescriptorProtos.DescriptorProto,

):
    AbstractGeneratedMessageWrapper<DescriptorProtos.DescriptorProto>(proto) {

//    public val name: SyntaxElement<String>? by lazy { if (proto.hasName()) SyntaxElement(proto.name, null) else null }

    // fields
    // extensions
    public val nestedTypes: List<DescriptorProtoWrapper> by lazy {
        TODO()
    }
}
