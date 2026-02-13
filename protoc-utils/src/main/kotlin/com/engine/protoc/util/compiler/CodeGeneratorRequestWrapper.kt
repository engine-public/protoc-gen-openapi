package com.engine.protoc.util.compiler

import com.engine.protoc.util.AbstractGeneratedMessageWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.compiler.PluginProtos

public class CodeGeneratorRequestWrapper(proto: PluginProtos.CodeGeneratorRequest): AbstractGeneratedMessageWrapper<PluginProtos.CodeGeneratorRequest>(proto) {
    /**
     * The .proto files that were explicitly listed on the command-line.  The
     * code generator should generate code only for these files.  Each file's
     * descriptor will be included in proto_file, below.
     */
    public val filesToGenerate: List<String> by lazy {
        proto.fileToGenerateList.toList()
    }

    /**
     * The generator parameter passed on the command-line.
     */
    public val parameters: Parameters by lazy {
        Parameters(proto.parameter)
    }

    /**
     * FileDescriptorProtos for all files in files_to_generate and everything
     * they import.  The files will appear in topological order, so each file
     * appears before any file that imports it.
     *
     * Note: the files listed in files_to_generate will include runtime-retention
     * options only, but all other files will include source-retention options.
     * The source_file_descriptors field below is available in case you need
     * source-retention options for files_to_generate.
     *
     * protoc guarantees that all proto_files will be written after
     * the fields above, even though this is not technically guaranteed by the
     * protobuf wire format.  This theoretically could allow a plugin to stream
     * in the FileDescriptorProtos and handle them one by one rather than read
     * the entire set into memory at once.  However, as of this writing, this
     * is not similarly optimized on protoc's end -- it will store all fields in
     * memory at once before sending them to the plugin.
     *
     * Type names of fields and extensions in the FileDescriptorProto are always
     * fully qualified.
     */
    public val protoFiles: List<FileDescriptorProtoWrapper> by lazy {
        proto.protoFileList.map { FileDescriptorProtoWrapper(this, it) }
    }

    /**
     * File descriptors with all options, including source-retention options.
     * These descriptors are only provided for the files listed in
     * files_to_generate.
     */
    public val sourceFileDescriptors: List<FileDescriptorProtoWrapper> by lazy {
        proto.sourceFileDescriptorsList.map { FileDescriptorProtoWrapper(this, it) }
    }

    /**
     * The version number of protocol compiler.
     */
    public val version: VersionWrapper? by lazy {
        if (proto.hasCompilerVersion()) VersionWrapper(proto.compilerVersion) else null
    }
}
