package com.engine.protoc.util

import com.google.protobuf.GeneratedMessage

/**
 * Base class for wrappers around generated protobuf messages.
 */
public abstract class AbstractGeneratedMessageWrapper<MessageT : GeneratedMessage>(public val proto: MessageT)
