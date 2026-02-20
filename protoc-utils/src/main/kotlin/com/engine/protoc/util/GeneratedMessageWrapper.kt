package com.engine.protoc.util

import com.google.protobuf.GeneratedMessage

/**
 * Base class for wrappers around generated protobuf messages.
 */
public interface GeneratedMessageWrapper<MessageT : GeneratedMessage> {
    public val proto: MessageT
}
