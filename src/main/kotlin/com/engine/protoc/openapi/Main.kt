package com.engine.protoc.openapi

import com.google.protobuf.compiler.PluginProtos
import java.io.ByteArrayOutputStream
import java.io.File

public fun main() {
    val replayCodeGeneratorRequest = System.getenv("PROTOC_GEN_OPENAPI_REPLAY_CGREQ")?.let { File(it) }
    val recordCodeGeneratorRequest = System.getenv("PROTOC_GEN_OPENAPI_RECORD_CGREQ")?.let { File(it) }
    val recordCodeGeneratorResponse = System.getenv("PROTOC_GEN_OPENAPI_RECORD_CGRESP")?.let { File(it) }

    // read the input from the replay file (if requested) or stdin
    val input = replayCodeGeneratorRequest?.readBytes() ?: System.`in`.readBytes()

    // record the request if requested
    recordCodeGeneratorRequest?.run {
        writeBytes(input)
    }

    // build the request from the input bytes
    val request = PluginProtos.CodeGeneratorRequest.parseFrom(input)

    // build the shell response
    val response = PluginProtos.CodeGeneratorResponse.newBuilder()

    // let protoc see that this plugin supports optional scalars
    response.supportedFeatures =
        response.supportedFeatures or
        PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.number
            .toLong()

    // TODO naive implementation for initial PR, simply create one output file for every input file
    request
        .sourceFileDescriptorsList
        .forEach { fileDescriptor ->
            response.addFile(
                PluginProtos.CodeGeneratorResponse.File.newBuilder().apply {
                    name = fileDescriptor.name.replace(".proto", ".txt")
                    content = fileDescriptor.`package` + "." + fileDescriptor.name
                },
            )
        }

    // capture the output bytes
    val output = ByteArrayOutputStream()
    response.build().writeTo(output)

    // record the response if requested
    recordCodeGeneratorResponse?.run {
        writeBytes(output.toByteArray())
    }

    // return the response to protoc via stdout
    System.out.apply {
        write(output.toByteArray())
        flush()
    }
}
