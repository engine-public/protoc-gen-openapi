package com.engine.protoc.util.file

import com.engine.protoc.util.AbstractGeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Wrapper for a FileDescriptorProto, providing convenient access to its properties and associated syntax elements.
 */
public class FileDescriptorProtoWrapper(internal val cgreq: CodeGeneratorRequestWrapper, proto: DescriptorProtos.FileDescriptorProto, ): AbstractGeneratedMessageWrapper<DescriptorProtos.FileDescriptorProto>(proto) {
    /**
     * The relative path to the file from one of the source roots.
     */
    public val name: String? get() = if (proto.hasName()) proto.name else null

    /**
     * Generated info about the source file, including comments and location identification of the File's syntax elements.
     */
    public val sourceCodeInfo: SourceCodeInfoWrapper? by lazy {
        if (proto.hasSourceCodeInfo()) SourceCodeInfoWrapper(
            proto.sourceCodeInfo
        ) else null
    }

    /**
     * The package applied to the contents of the file.
     */
    public val `package`: SyntaxElement<String>? by lazy {
        if (proto.hasPackage()) {
            SyntaxElement(
                proto.`package`,
                listOf(DescriptorProtos.FileDescriptorProto.PACKAGE_FIELD_NUMBER),
                this
            )
        } else null
    }

    /**
     * The files imported by this file.
     */
    public val dependencies: List<Dependency> by lazy {
        proto.dependencyList.mapIndexed { index, dependency ->
            Dependency(dependency, listOf(DescriptorProtos.FileDescriptorProto.DEPENDENCY_FIELD_NUMBER, index), this)
        }
    }
    public val dependenciesByName: Map<String, Dependency> by lazy {
        dependencies.associateBy { it.value }
    }

    /**
     * The files publicly imported by this file.  A subset of [dependencies].
     */
    public val publicDependencies: List<Dependency> by lazy {
        dependencies.slice(proto.publicDependencyList)
    }

    /**
     * The files weakly imported by this file.  A subset of [dependencies].
     */
    @Deprecated("For Google-internal migration only. Do not use.")
    public val weakDependencies: List<Dependency> by lazy {
        dependencies.slice(proto.weakDependencyList)
    }

    // TODO option_dependency -- field id 15

    // TODO message_type
    // TODO enum_type
    // TODO service
    // TODO extension

    public val options: FileOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            FileOptionsWrapper(
                proto.options,
                this,
                listOf(DescriptorProtos.FileDescriptorProto.OPTIONS_FIELD_NUMBER))
        } else null
    }

    /**
     * The syntax type of the file.
     * @see DescriptorProtos.FileDescriptorProto.getSyntax
     */
    public val syntax: SyntaxElement<String>? by lazy {
        if (proto.hasSyntax()) {
            SyntaxElement(
                proto.syntax, listOf(
                    DescriptorProtos.FileDescriptorProto.SYNTAX_FIELD_NUMBER
                ), this
            )
        } else null
    }
    // TODO edition
}
