package com.engine.protoc.util

/**
 * Implemented by any syntax element that can be located within a proto source file.
 *
 * [path] is a sequence of field numbers and list indices that uniquely identifies this element
 * within the file's [com.google.protobuf.DescriptorProtos.SourceCodeInfo] — the same encoding
 * used by protoc itself when writing location entries.  For example, the path `[4, 0, 2, 1]`
 * means: field 4 (message_type), index 0 (first message), field 2 (field), index 1 (second field).
 */
public interface Locatable {
    public val path: List<Int>
}
