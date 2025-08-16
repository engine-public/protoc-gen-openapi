package com.engine.protoc.util.compiler

// TODO : placeholder for now
public class CodeGeneratorRequestWrapper {
    /**
     * // An encoded CodeGeneratorRequest is written to the plugin's stdin.
     * message CodeGeneratorRequest {
     *   // The .proto files that were explicitly listed on the command-line.  The
     *   // code generator should generate code only for these files.  Each file's
     *   // descriptor will be included in proto_file, below.
     *   repeated string file_to_generate = 1;
     *
     *   // The generator parameter passed on the command-line.
     *   optional string parameter = 2;
     *
     *   // FileDescriptorProtos for all files in files_to_generate and everything
     *   // they import.  The files will appear in topological order, so each file
     *   // appears before any file that imports it.
     *   //
     *   // Note: the files listed in files_to_generate will include runtime-retention
     *   // options only, but all other files will include source-retention options.
     *   // The source_file_descriptors field below is available in case you need
     *   // source-retention options for files_to_generate.
     *   //
     *   // protoc guarantees that all proto_files will be written after
     *   // the fields above, even though this is not technically guaranteed by the
     *   // protobuf wire format.  This theoretically could allow a plugin to stream
     *   // in the FileDescriptorProtos and handle them one by one rather than read
     *   // the entire set into memory at once.  However, as of this writing, this
     *   // is not similarly optimized on protoc's end -- it will store all fields in
     *   // memory at once before sending them to the plugin.
     *   //
     *   // Type names of fields and extensions in the FileDescriptorProto are always
     *   // fully qualified.
     *   repeated FileDescriptorProto proto_file = 15;
     *
     *   // File descriptors with all options, including source-retention options.
     *   // These descriptors are only provided for the files listed in
     *   // files_to_generate.
     *   repeated FileDescriptorProto source_file_descriptors = 17;
     *
     *   // The version number of protocol compiler.
     *   optional Version compiler_version = 3;
     * }
     */
}
