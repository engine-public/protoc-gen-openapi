package com.engine.protoc.util.extensions

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public fun DescriptorProtos.FileDescriptorProto.wrap(): FileDescriptorProtoWrapper = FileDescriptorProtoWrapper(this)
