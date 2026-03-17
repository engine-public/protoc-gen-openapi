package com.engine.protoc.openapi

import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.openapi.Annotations
import java.io.File
import java.io.InputStream
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

public class ProtocGenOpenAPI(
    private val request: PluginProtos.CodeGeneratorRequest,
    private val options: Options,
) {

    /**
     * Options that influence the compiler plugin
     */
    public data class Options constructor(
        val recordCodeGeneratorRequest: File?,
        val recordCodeGeneratorResponse: File?,
    ) {

        public class Builder {
            public var recordCodeGeneratorRequest: File? = null
            public var recordCodeGeneratorResponse: File? = null

            public companion object {
                public fun from(requestParameter: String): Builder {
                    val builder = Builder()
                    requestParameter.split(",").forEach { parameter ->
                        val (optionName, optionValue) = parameter.split("=", limit = 2)
                        val property = Builder::class.memberProperties.find { it.name == optionName }

                        checkNotNull(property) {
                            "`protoc-gen-openapi` does not recognizer the `$optionName` option."
                        }

                        check(property is KMutableProperty<*>) {
                            "`${property.name}` is not settable in `ProtocGenOpenAPI.Options`"
                        }

                        val convertedOptionValue = when (property.returnType.classifier) {
                            Boolean::class -> optionValue.toBoolean()

                            File::class -> File(optionValue)

                            String::class -> optionValue

                            else -> throw IllegalStateException(
                                """
                                `${property.returnType.classifier}` is not a supported `ProtocGenOpenAPI.Options` type.
                                This is an error internal to `protoc-gen-openapi`.
                                The provided option was `$parameter`.
                                """.trimIndent(),
                            )
                        }
                        property.setter.call(builder, convertedOptionValue)
                    }

                    return builder
                }
            }

            public fun build(): Options =
                Options(
                    recordCodeGeneratorRequest = recordCodeGeneratorRequest,
                    recordCodeGeneratorResponse = recordCodeGeneratorResponse,
                )
        }
    }

    public companion object {
        public fun ExtensionRegistry.prepare(): ExtensionRegistry =
            apply {
                Annotations.registerAllExtensions(this)
                AnnotationsProto.registerAllExtensions(this)
            }

        public fun from(
            input: InputStream,
            registry: ExtensionRegistry = ExtensionRegistry.newInstance().prepare(),
            block: Options.Builder.() -> Unit = {},
        ): ProtocGenOpenAPI {
            // capture the bytes in case we need to record them and parsing fails
            val stdin: ByteArray = input.readBytes()

            // record the bytes if requested
            System.getenv("PROTOC_GEN_OPENAPI_RECORD_CGREQ")?.let { File(it) }?.writeBytes(stdin)

            // override the input if requested
            // this is only provided via env variable, because it makes no sense
            // to put this in the standard compiler path
            val input = System.getenv("PROTOC_GEN_OPENAPI_REPLAY_CGREQ")?.let { File(it) }?.readBytes() ?: stdin

            try {
                return ProtocGenOpenAPI(
                    PluginProtos.CodeGeneratorRequest.parseFrom(input, registry),
                    Options.Builder().apply(block).build(),
                )
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "The provided code generator request could not be parsed.  To record the request for debugging, re-run the compiler using the environment variable `PROTOC_GEN_OPENAPI_RECORD_CGREQ` with a writable file path.",
                    t,
                )
            }
        }
    }

    public fun compile(): PluginProtos.CodeGeneratorResponse {
        // if requested, record the request
        options.recordCodeGeneratorRequest?.outputStream()?.use { output -> request.writeTo(output) }
        return PluginProtos.CodeGeneratorResponse.newBuilder().apply {
            supportedFeatures = PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.number.toLong()
            // TODO
            //  look at every file that needs to be compiled
            //  1. filter out any that have no services
            //  2. transform that file into an openapi spec, if possible
            //  3. spit out the openapi yaml for any files that survived
        }.build()
    }
}
