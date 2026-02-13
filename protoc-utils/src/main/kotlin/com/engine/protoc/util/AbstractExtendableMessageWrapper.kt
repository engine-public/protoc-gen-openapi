package com.engine.protoc.util

import com.engine.protoc.util.extensions.findExtension
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.file.SourceCodeInfoWrapper
import com.google.protobuf.GeneratedMessage

/**
 * Base class for wrappers around generated protobuf messages that are extendable.
 * This provides easy access to extensions and their comments, regardless of whether they were registered on the [com.google.protobuf.ExtensionRegistry] prior to deserialization or not.
 * Please note, while comments on an extension option are retained and available, comments on the fields inside the extension value are lost by protoc and cannot be retrieved without the original source file.
 */
public abstract class AbstractExtendableMessageWrapper<ExtendableMessageT: GeneratedMessage.ExtendableMessage<ExtendableMessageT>>(
    proto: ExtendableMessageT,
    private val file: FileDescriptorProtoWrapper,
    public override val path: List<Int>
): AbstractGeneratedMessageWrapper<ExtendableMessageT>(proto), Locatable {
    public fun <MessageT, GeneratedExtensionT : GeneratedMessage.GeneratedExtension<ExtendableMessageT, MessageT>> findExtension(extension: GeneratedExtensionT): SyntaxElement<MessageT>? {
        return proto.findExtension(extension)?.let {
            return SyntaxElement(it, path + extension.number, file)
        }
    }
}
