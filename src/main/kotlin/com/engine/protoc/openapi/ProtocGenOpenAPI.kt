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
        /**
         * When `true`, all target proto files are compiled into a single OpenAPI document.
         * File-level annotations are merged left-to-right (later files overwrite earlier ones for
         * conflicting keys), and every service's paths are combined into a shared `paths` object.
         *
         * When `false` (the default), each service produces its own output file named
         * `<package>.<ServiceName>.openapi.json`.  File-level annotations are still applied to
         * every service document they accompany, but services in different files are never merged
         * together.
         */
        val merge: Boolean,

        /**
         * A fallback API version string written to `info.version` of every generated document that
         * does not already have a version supplied by an engine annotation.
         *
         * Priority (lowest to highest):
         *   1. This option — applied before any annotation layer.
         *   2. File-level `engine.protoc.openapi.file` annotation — overwrites the option value if
         *      it specifies `info.version`.
         *   3. Service-level `engine.protoc.openapi.service` annotation — highest priority; always
         *      preserved when present.
         *
         * When `null` (the default) no fallback is injected.  Documents whose annotations do not
         * supply a version will emit `info` without a `version` field, which is not valid OAS 3.1.
         * Setting this option is therefore the recommended way to produce fully-valid documents
         * for services that carry only `google.api.http` annotations and no engine annotations.
         */
        val version: String?,

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

        /**
         * The serialization format of every generated OpenAPI document.
         *
         * [OutputFormat.JSON] produces pretty-printed JSON (the default); files are named
         * `*.openapi.json`. [OutputFormat.YAML] produces YAML; files are named
         * `*.openapi.yaml`.
         *
         * Passed via `--openapi_out=outputFormat=YAML:outdir` (case-insensitive).
         */
        val outputFormat: OutputFormat,

        /**
         * When `true`, the service name is automatically applied as an OAS tag on every operation
         * the service contributes to the `paths` section.  A top-level `tags` entry is also added
         * for each participating service, using the service's leading proto comment as the tag
         * description (when one is present).
         *
         * This provides out-of-the-box grouping in API tools (Swagger UI, Redoc, etc.) without
         * requiring any `engine.protoc.openapi.method` annotation changes.  Auto-generated tags
         * are prepended before any explicitly-set tags on the method annotation and deduplicated.
         *
         * When `false` (the default), tags must be set explicitly via the
         * `engine.protoc.openapi.method` annotation's `tags` field.
         *
         * Passed via `--openapi_out=autoTagServices=true:outdir`.
         */
        val autoTagServices: Boolean,
    ) {
        /**
         * The serialization format for generated OpenAPI documents.
         *
         * Values are matched case-insensitively when supplied via the `--openapi_out`
         * parameter string (`outputFormat=yaml` and `outputFormat=YAML` are both valid).
         */
        public enum class OutputFormat {
            /** Pretty-printed JSON. Output files are named `*.openapi.json`. */
            JSON,

            /** YAML. Output files are named `*.openapi.yaml`. */
            YAML,
        }

        public enum class CaseStrategy {
            UNMODIFIED,
            CAMEL_CASE,
            SNAKE_CASE,
        }

        public class Builder private constructor(parameters: Parameters) {

            /**
             * @see [Options.merge]
             */
            public var merge: Boolean = parameters.get<Boolean>("merge") ?: false

            /**
             * @see [Options.version].
             */
            public var version: String? = parameters.get<String>("version")

            /**
             * @see [Options.validateOutput]
             */
            public var validateOutput: Boolean = parameters.get<Boolean>("validateOutput") ?: false

            /**
             * @see [Options.outputFormat]
             */
            public var outputFormat: OutputFormat =
                parameters.get<OutputFormat>("outputFormat") ?: OutputFormat.JSON

            /**
             * @see [Options.autoTagServices]
             */
            public var autoTagServices: Boolean =
                parameters.get<Boolean>("autoTagServices") ?: false

            public companion object {
                public fun from(parameters: Parameters): Builder = Builder(parameters)
            }

            public fun build(): Options =
                Options(
                    merge = merge,
                    version = version,
                    validateOutput = validateOutput,
                    outputFormat = outputFormat,
                    autoTagServices = autoTagServices,
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
