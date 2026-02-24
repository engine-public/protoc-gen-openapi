package com.engine.protoc.util.extensions

import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public fun DescriptorProtos.FileDescriptorProto.wrap(cgreq: CodeGeneratorRequestWrapper): FileDescriptorProtoWrapper = FileDescriptorProtoWrapper(cgreq, this)
