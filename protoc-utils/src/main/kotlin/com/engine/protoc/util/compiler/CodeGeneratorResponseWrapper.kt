package com.engine.protoc.util.compiler

import com.google.protobuf.compiler.PluginProtos

public class CodeGeneratorResponseWrapper {

    private val errors = mutableListOf<String>()
    private val files = mutableListOf<PluginProtos.CodeGeneratorResponse.File>()

    public val hasErrors: Boolean get() = errors.isNotEmpty()

    public fun addError(message: String): CodeGeneratorResponseWrapper =
        apply {
            errors.add(message)
        }

    public fun addFile(
        name: String,
        content: String,
    ): CodeGeneratorResponseWrapper =
        apply {
            files.add(
                PluginProtos.CodeGeneratorResponse.File.newBuilder()
                    .setName(name)
                    .setContent(content)
                    .build(),
            )
        }

    public fun build(): PluginProtos.CodeGeneratorResponse {
        val builder = PluginProtos.CodeGeneratorResponse.newBuilder()
            .setSupportedFeatures(
                PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.number.toLong(),
            )
        if (errors.isNotEmpty()) {
            builder.error = errors.joinToString("\n")
        }
        for (file in files) {
            builder.addFile(file)
        }
        return builder.build()
    }
}
