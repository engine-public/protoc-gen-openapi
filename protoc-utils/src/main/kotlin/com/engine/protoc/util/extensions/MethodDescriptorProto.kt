package com.engine.protoc.util.extensions

import com.google.protobuf.DescriptorProtos

/**
 * Retrieves the path of a method descriptor within a file and service.
 * This path can be used to find the location of the method in a [com.engine.protoc.util.file.SourceCodeInfoWrapper], if available.
 */
public fun DescriptorProtos.MethodDescriptorProto.getPath(file: DescriptorProtos.FileDescriptorProto, service: DescriptorProtos.ServiceDescriptorProto): List<Int> = sequence {
    yieldAll(service.getPath(file))
    val methodIndex = service.methodList.indexOf(this@getPath)
    check(methodIndex != -1) { "method=`${this@getPath.name}` not found in file=`${file.name}`/service=`${this@getPath.name}`.  This is an error in the protoc plugin." }
    yield(methodIndex)
}.toList()
