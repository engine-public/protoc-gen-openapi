package com.engine.protoc.util.service

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Describes a single RPC method within a [ServiceDescriptorProtoWrapper].
 * Each method has a name, fully-qualified input and output message types, optional streaming
 * flags, and optional method-level options (including custom extensions).
 */
public class MethodDescriptorProtoWrapper(
    override val proto: DescriptorProtos.MethodDescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.MethodDescriptorProto> {

    /** The unqualified name of this method as written in the .proto source. */
    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.MethodDescriptorProto.NAME_FIELD_NUMBER, file)
    }

    /**
     * Input and output type names.  These are resolved in the same way as
     * FieldDescriptorProto.type_name, but must refer to a message type.
     */
    public val inputType: SyntaxElement<String>? = (if (proto.hasInputType()) proto.inputType else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.MethodDescriptorProto.INPUT_TYPE_FIELD_NUMBER, file)
    }

    /** @see inputType */
    public val outputType: SyntaxElement<String>? = (if (proto.hasOutputType()) proto.outputType else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.MethodDescriptorProto.OUTPUT_TYPE_FIELD_NUMBER, file)
    }

    /** Options set on this method, or null if none were specified. */
    public val options: MethodOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            MethodOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.MethodDescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }

    /** Identifies if client streams multiple client messages */
    public val clientStreaming: SyntaxElement<Boolean>? =
        (if (proto.hasClientStreaming()) proto.clientStreaming else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MethodDescriptorProto.CLIENT_STREAMING_FIELD_NUMBER, file)
        }

    /** Identifies if server streams multiple server messages */
    public val serverStreaming: SyntaxElement<Boolean>? =
        (if (proto.hasServerStreaming()) proto.serverStreaming else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MethodDescriptorProto.SERVER_STREAMING_FIELD_NUMBER, file)
        }
}
