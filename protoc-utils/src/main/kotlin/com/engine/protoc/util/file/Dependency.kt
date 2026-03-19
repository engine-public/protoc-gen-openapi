package com.engine.protoc.util.file

import com.engine.protoc.util.SyntaxElement

/**
 * Describes a dependency on another file.
 */
public class Dependency(name: String, path: List<Int>, file: FileDescriptorProtoWrapper) : SyntaxElement<String>(name, path, file) {
    /**
     * The [FileDescriptorProtoWrapper] for the imported file, looked up by name from the
     * [com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper.protoFiles] list, or null if
     * the imported file was not included in the request (e.g. it is a well-known proto that protoc
     * strips from the request when not needed for code generation).
     */
    public val dependencyFileDescriptor: FileDescriptorProtoWrapper? by lazy {
        file.cgreq.protoFiles.firstOrNull { it.proto.name == name }
    }
}
