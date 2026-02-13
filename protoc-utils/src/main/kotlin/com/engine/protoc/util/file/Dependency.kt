package com.engine.protoc.util.file

import com.engine.protoc.util.SyntaxElement

/**
 * Describes a dependency on another file.
 */
public class Dependency(name: String, path: List<Int>, file: FileDescriptorProtoWrapper) : SyntaxElement<String>(name, path, file) {
    public val dependencyFileDescriptor: FileDescriptorProtoWrapper? by lazy {
        file.cgreq.protoFiles.firstOrNull { it.proto.name == name }
    }
}
