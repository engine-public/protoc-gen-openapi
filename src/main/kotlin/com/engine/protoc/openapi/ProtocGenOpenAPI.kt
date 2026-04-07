package com.engine.protoc.openapi

import com.engine.protoc.openapi.compile.Compiler
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.compiler.Parameters
import com.engine.protoc.util.extensions.wrap
import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
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
//        val caseStrategy: CaseStrategy,
        /**
         * Validates the output against the official OAS 3.1.1 schema.
         * Please note, some features described in the OAS 3.1 documentation produces
         * OAS files that do not pass validation of the spec.
         *
         * Known "invalid" features include:
         * * variables in URI references
         *   OpenAPI allows you to specify a variable for inclusion of a URI.
         *   For example, `https://{region}.api.example.com`.
         *   However, the OAS schema document requires the URI fields to e a valid
         *   RFC 3986 URI-reference, which may not contain curly-braces in the host.
         */
        val validateOutput: Boolean,
//        val outputFormat: OutputFormat,
    ) {
        public enum class CaseStrategy {
            UNMODIFIED,
            CAMEL_CASE,
            SNAKE_CASE,
        }

//        public enum class OutputFormat {
//            JSON,
//            YAML,
//        }

        public class Builder private constructor(parameters: Parameters) {
            public var merge: Boolean = parameters.get<Boolean>("merge") ?: false

//            public var caseStrategy: CaseStrategy = parameters.get<CaseStrategy>("caseStrategy") ?: CaseStrategy.UNMODIFIED
            public var validateOutput: Boolean = true
//            public var outputFormat: OutputFormat = parameters.get<OutputFormat>("outputFormat") ?: OutputFormat.JSON

            public companion object {
                public fun from(parameters: Parameters): Builder = Builder(parameters)
            }

            public fun build(): Options =
                Options(
                    merge = merge,
//                    caseStrategy,
                    validateOutput = validateOutput,
//                    outputFormat,
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

    public fun compile(): PluginProtos.CodeGeneratorResponse = Compiler(request, options).compile()
}
