package com.engine.protoc.util.file

import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper
import com.engine.protoc.util.message.FieldDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Wrapper for a [com.google.protobuf.DescriptorProtos.FileDescriptorProto], providing typed,
 * lazily-evaluated access to all proto elements defined in a single .proto file along with their
 * source locations and comments.
 *
 * The [cgreq] back-reference is needed to resolve cross-file lookups such as [Dependency.dependencyFileDescriptor]
 * and to supply the [SourceCodeInfoWrapper] used for comment extraction.
 */
public class FileDescriptorProtoWrapper(internal val cgreq: CodeGeneratorRequestWrapper, override val proto: DescriptorProtos.FileDescriptorProto) : GeneratedMessageWrapper<DescriptorProtos.FileDescriptorProto> {
    /**
     * The relative path to the file from one of the source roots.
     */
    public val name: String? get() = if (proto.hasName()) proto.name else null

    /**
     * Generated info about the source file, including comments and location identification of the File's syntax elements.
     */
    public val sourceCodeInfo: SourceCodeInfoWrapper? by lazy {
        if (proto.hasSourceCodeInfo()) {
            SourceCodeInfoWrapper(
                proto.sourceCodeInfo,
            )
        } else {
            null
        }
    }

    /**
     * The package applied to the contents of the file.
     */
    public val `package`: SyntaxElement<String>? by lazy {
        if (proto.hasPackage()) {
            SyntaxElement(
                proto.`package`,
                listOf(DescriptorProtos.FileDescriptorProto.PACKAGE_FIELD_NUMBER),
                this,
            )
        } else {
            null
        }
    }

    /**
     * The files imported by this file.
     */
    public val dependencies: List<Dependency> by lazy {
        proto.dependencyList.mapIndexed { index, dependency ->
            Dependency(dependency, listOf(DescriptorProtos.FileDescriptorProto.DEPENDENCY_FIELD_NUMBER, index), this)
        }
    }
    /** The same dependencies as [dependencies] indexed by import path for O(1) lookup. */
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

    /**
     * Names of files imported by this file purely for the purpose of providing
     * option extensions. These are excluded from the dependency list above.
     */
    public val optionDependencies: List<Dependency> by lazy {
        proto.optionDependencyList.mapIndexed { index, dependency -> Dependency(dependency, listOf(DescriptorProtos.FileDescriptorProto.OPTION_DEPENDENCY_FIELD_NUMBER, index), this) }
    }

    public val messageTypes: List<DescriptorProtoWrapper> by lazy {
        proto.messageTypeList.mapIndexed { index, msgProto ->
            DescriptorProtoWrapper(
                msgProto,
                listOf(DescriptorProtos.FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER, index),
                this,
            )
        }
    }
    public val enumTypes: List<EnumDescriptorProtoWrapper> by lazy {
        proto.enumTypeList.mapIndexed { index, enumProto ->
            EnumDescriptorProtoWrapper(
                enumProto,
                listOf(DescriptorProtos.FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER, index),
                this,
            )
        }
    }
    public val services: List<ServiceDescriptorProtoWrapper> by lazy {
        proto.serviceList.mapIndexed { index, serviceProto ->
            ServiceDescriptorProtoWrapper(
                serviceProto,
                listOf(DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER, index),
                this,
            )
        }
    }

    /**
     * All top-level extension fields defined in this file via `extend OtherMessage { ... }`
     * blocks at the file level.  Each entry describes one extension field.
     */
    public val extensions: List<FieldDescriptorProtoWrapper> by lazy {
        proto.extensionList.mapIndexed { index, extensionProto ->
            FieldDescriptorProtoWrapper(
                extensionProto,
                listOf(DescriptorProtos.FileDescriptorProto.EXTENSION_FIELD_NUMBER, index),
                this,
            )
        }
    }

    public val options: FileOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            FileOptionsWrapper(
                proto.options,
                this,
                listOf(DescriptorProtos.FileDescriptorProto.OPTIONS_FIELD_NUMBER),
            )
        } else {
            null
        }
    }

    /**
     * The syntax type of the file.
     * @see DescriptorProtos.FileDescriptorProto.getSyntax
     */
    public val syntax: SyntaxElement<String>? by lazy {
        if (proto.hasSyntax()) {
            SyntaxElement(
                proto.syntax,
                listOf(
                    DescriptorProtos.FileDescriptorProto.SYNTAX_FIELD_NUMBER,
                ),
                this,
            )
        } else {
            null
        }
    }
    /**
     * The edition of the proto file.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val edition: SyntaxElement<DescriptorProtos.Edition>? by lazy {
        if (proto.hasEdition()) {
            SyntaxElement(
                proto.edition,
                listOf(DescriptorProtos.FileDescriptorProto.EDITION_FIELD_NUMBER),
                this,
            )
        } else {
            null
        }
    }
}
