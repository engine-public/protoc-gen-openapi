package com.engine.protoc.util.recorder

import com.google.protobuf.ByteString
import com.google.protobuf.compiler.PluginProtos

public fun main() {
    PluginProtos
        .CodeGeneratorResponse
        .newBuilder()
        .addFile(
            PluginProtos.CodeGeneratorResponse.File.newBuilder().apply {
                name = "code-generator-request.binpb"
                contentBytes = ByteString.copyFrom(System.`in`.readBytes())
            },
        )
        .build()
        .writeTo(System.out)
}
