package com.engine.protoc.openapi

import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.compiler.Parameters
import com.engine.protoc.util.extensions.wrap
import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.openapi.Annotations
import java.io.InputStream

public class ProtocGenOpenAPI(
    private val request: CodeGeneratorRequestWrapper,
    private val options: Options,
) {

    /**
     * Options that influence the compiler plugin
     */
    public data class Options(
        val merge: Boolean,
        val caseStrategy: CaseStrategy,
    ) {
        public enum class CaseStrategy {
            UNMODIFIED,
            CAMEL_CASE,
            SNAKE_CASE,
        }

        public class Builder private constructor(parameters: Parameters) {
            public var merge: Boolean = parameters.get<Boolean>("merge") ?: false
            public var caseStrategy: CaseStrategy = parameters.get<CaseStrategy>("caseStrategy") ?: CaseStrategy.UNMODIFIED

            public companion object {
                public fun from(parameters: Parameters): Builder = Builder(parameters)
            }

            public fun build(): Options =
                Options(
                    merge,
                    caseStrategy,
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
            val cgreq = PluginProtos.CodeGeneratorRequest.parseFrom(input, registry).wrap()
            return ProtocGenOpenAPI(
                cgreq,
                Options.Builder.from(cgreq.parameters).apply(block).build(),
            )
        }
    }

    public fun compile(): PluginProtos.CodeGeneratorResponse {
        // if requested, record the request
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
