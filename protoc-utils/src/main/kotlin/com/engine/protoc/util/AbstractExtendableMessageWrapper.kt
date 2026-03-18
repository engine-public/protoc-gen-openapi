package com.engine.protoc.util

import com.engine.protoc.util.extensions.findExtension
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.GeneratedMessage

/**
 * Base class for wrappers around generated protobuf messages that are extendable.
 * This provides easy access to extensions and their comments, regardless of whether they were registered on the [com.google.protobuf.ExtensionRegistry] prior to deserialization or not.
 * Please note, while comments on an extension option are retained and available, comments on the fields inside the extension value are lost by protoc and cannot be retrieved without the original source file.
 */
public abstract class AbstractExtendableMessageWrapper<ExtendableMessageT : GeneratedMessage.ExtendableMessage<ExtendableMessageT>>(
    override val proto: ExtendableMessageT,
    private val file: FileDescriptorProtoWrapper,
    public override val path: List<Int>,
) : GeneratedMessageWrapper<ExtendableMessageT>,
    Locatable {
    /**
     * Returns the value of [extension] on this message as a [SyntaxElement] (which carries source
     * location so callers can access comments on the option), or null if the extension is not set.
     * The extension must have been registered on the [com.google.protobuf.ExtensionRegistry] used
     * when the enclosing [com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest] was parsed;
     * use [com.engine.protoc.util.extensions.findUnregisteredExtension] for extensions that were not.
     */
    public fun <MessageT, GeneratedExtensionT : GeneratedMessage.GeneratedExtension<ExtendableMessageT, MessageT>> findExtension(extension: GeneratedExtensionT): SyntaxElement<MessageT>? {
        return proto.findExtension(extension)?.let {
            return SyntaxElement(it, path + extension.number, file)
        }
    }
}
