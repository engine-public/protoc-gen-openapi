package com.engine.protoc.util.extensions

import com.google.protobuf.DescriptorProtos

/**
 * Retrieves the path of a service descriptor within a file and service.
 * This path can be used to find the location of the service in a [com.engine.protoc.util.file.SourceCodeInfoWrapper], if available.
 */
public fun DescriptorProtos.ServiceDescriptorProto.getPath(file: DescriptorProtos.FileDescriptorProto): List<Int> = sequence {
    yield(DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER)
    val serviceIndex = file.serviceList.indexOf(this@getPath)
    check(serviceIndex != -1) { "service=`${this@getPath.name}` not found in file=`${file.name}`.  This is an error in the protoc plugin." }
    yield(serviceIndex)
}.toList()
