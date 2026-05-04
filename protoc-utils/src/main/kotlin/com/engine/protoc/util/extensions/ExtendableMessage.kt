package com.engine.protoc.util.extensions

import com.google.protobuf.GeneratedMessage

/**
 * Typed utility function to retrieve an extension from an extendable message.
 * Extension functions can only be retrieved if they were registered before deserializing the message.
 */
public fun <MessageT, ExtendableMessageT : GeneratedMessage.ExtendableMessage<ExtendableMessageT>, GeneratedExtensionT : GeneratedMessage.GeneratedExtension<ExtendableMessageT, MessageT>> ExtendableMessageT.findExtension(
    extension: GeneratedExtensionT,
): MessageT? =
    if (hasExtension(extension)) {
        getExtension(extension)
    } else {
        null
    }

/**
 * Typed utility function to attempt to retrieve an extension that was not properly registed with the [com.google.protobuf.ExtensionRegistry] before deserialization of the message.
 * It is preferable to configure and pass a [com.google.protobuf.ExtensionRegistry] when you call [com.google.protobuf.Parser.parseFrom] so that the extensions are properly and safely registered.
 * @see findExtension
 * @throws com.google.protobuf.InvalidProtocolBufferException If an unknown field with the same extension number is found, but it cannot be deserialized as [MessageT].
 * @throws ClassCastException if the extension value cannot be coerced to [MessageT].
 */
@Suppress("UNCHECKED_CAST") // MessageT is erased at runtime; cast is verified structurally by the caller
public fun <MessageT, ExtendableMessageT : GeneratedMessage.ExtendableMessage<ExtendableMessageT>, GeneratedExtensionT : GeneratedMessage.GeneratedExtension<ExtendableMessageT, MessageT>> ExtendableMessageT.findUnregisteredExtension(
    extension: GeneratedExtensionT,
): MessageT? =
    if (unknownFields.hasField(extension.number)) {
        // pull the value of the unknown field
        val field = unknownFields.getField(extension.number)

        // get the message type for the field, and its parser
        val parser = extension.messageDefaultInstance.parserForType

        parser.parseFrom(field.lengthDelimitedList.first()) as MessageT
    } else {
        null
    }
