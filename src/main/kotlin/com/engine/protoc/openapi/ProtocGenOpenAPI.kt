package com.engine.protoc.openapi

import com.engine.protoc.openapi.compile.Compiler
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.compiler.Parameters
import com.engine.protoc.util.extensions.wrap
import com.google.api.AnnotationsProto
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.Configurator
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.slf4j.event.Level
import java.io.InputStream
import org.apache.logging.log4j.Level as Log4jLevel

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
         * Controls how validation issues found by [validateOutput] are reported.
         *
         * When `false` (the default), every validation issue is emitted at the SLF4J `WARN`
         * level and the compile completes successfully — the generated document is still
         * written to the [CodeGeneratorResponse][com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse]
         * and no error string is set.
         *
         * When `true`, each issue is still logged at `WARN` AND added to the
         * `CodeGeneratorResponse.error` field, causing the plugin to exit non-zero and protoc
         * to abort the generation step.
         *
         * Has no effect unless [validateOutput] is also `true`; when [validateOutput] is
         * `false` no validation runs at all.  Setting this option to `true` while
         * [validateOutput] is `false` is reported as a `WARN` at compile-start.
         *
         * Passed via `--openapi_out=validationErrorsAreFatal=true:outdir`.
         */
        val validationErrorsAreFatal: Boolean,

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

        /**
         * Controls which package segments are prepended to each schema key in
         * `components/schemas` and the corresponding `$ref` strings.
         *
         * When two or more proto messages share the same simple name but live in different
         * packages, the default [SchemaNamespaceStrategy.NONE] produces a key collision.
         * Setting a non-`NONE` strategy makes every key globally unique within the generated
         * document.
         *
         * Passed via `--openapi_out=schemaNamespaceStrategy=FULL_PACKAGE:outdir`.
         */
        val schemaNamespaceStrategy: SchemaNamespaceStrategy,

        /**
         * Separator character inserted between each segment of a namespaced schema key.
         *
         * Passed via `--openapi_out=schemaNamespaceSeparator=UNDERSCORE:outdir`.
         */
        val schemaNamespaceSeparator: SchemaNamespaceSeparator,

        /**
         * Case transformation applied to each package segment of a namespaced schema key.
         * Version segments moved to the end by [schemaNamespaceVersionExtraction] are always
         * kept lowercase regardless of this setting.
         *
         * Passed via `--openapi_out=schemaNamespaceCasing=CAPITALIZED:outdir`.
         */
        val schemaNamespaceCasing: SchemaNamespaceCasing,

        /**
         * When `true`, package segments that look like proto API version identifiers
         * (matching `^v\d+[a-zA-Z0-9]*$`, e.g. `v1`, `v2beta1`) are removed from their
         * original position in the key and appended at the end, separated by
         * [schemaNamespaceSeparator].  Version segments are never capitalised regardless of
         * [schemaNamespaceCasing].
         *
         * Passed via `--openapi_out=schemaNamespaceVersionExtraction=true:outdir`.
         */
        val schemaNamespaceVersionExtraction: Boolean,

        /**
         * When `true`, adds a `"title"` field to every schema in `components/schemas` set to
         * the unqualified proto type name (message or enum simple name).  Useful when schema keys
         * are namespaced (e.g. the key is `Catalog_Item_v1`) and consumers need a clean, stable
         * label.
         *
         * If a schema has an `engine.protoc.openapi.message` or `engine.protoc.openapi.enum`
         * annotation that explicitly sets `title`, the annotation value takes precedence.
         *
         * Passed via `--openapi_out=setSchemaTitleToProtoSimpleName=true:outdir`.
         */
        val setSchemaTitleToProtoSimpleName: Boolean,

        /**
         * When `true`, every enum field emits its values inline (`"enum": [...]`) instead of
         * generating a separate `$ref` to a `components/schemas` entry.
         *
         * The per-enum `engine.protoc.openapi.inline` annotation overrides this option for a
         * specific enum, regardless of the global setting.
         *
         * Passed via `--openapi_out=inlineEnums=true:outdir`.
         */
        val inlineEnums: Boolean,

        /**
         * Global default for inlining an RPC's request body schema at the use site rather than
         * emitting a `$ref` into `components/schemas`.  Equivalent in effect to setting
         * `inline_request: true` on every method's `engine.protoc.openapi.method` annotation.
         *
         * The per-method `inline_request` annotation overrides this option for a specific RPC,
         * regardless of the global setting — an explicit `inline_request: false` keeps the `$ref`
         * even when this option is `true`, and an explicit `inline_request: true` inlines even
         * when this option is `false`.
         *
         * Transitivity is unchanged: messages reached only through an inlined boundary are
         * themselves inlined; messages also reachable through a non-inlined path remain in
         * components and are `$ref`'d from the inline expansion.
         *
         * Passed via `--openapi_out=inlineRequestSchemas=false:outdir`.
         */
        val inlineRequestSchemas: Boolean,

        /**
         * Global default for inlining an RPC's response body schema at the use site rather than
         * emitting a `$ref` into `components/schemas`.  Equivalent in effect to setting
         * `inline_response: true` on every method's `engine.protoc.openapi.method` annotation.
         *
         * The per-method `inline_response` annotation overrides this option for a specific RPC,
         * regardless of the global setting.
         *
         * Passed via `--openapi_out=inlineResponseSchemas=false:outdir`.
         */
        val inlineResponseSchemas: Boolean,

        /**
         * When `true`, enum values whose proto number is `0` (the proto3 default value
         * convention) are omitted from all OAS enum value lists.
         *
         * The per-value `engine.protoc.openapi.suppress` annotation overrides this option for a
         * specific enum value, regardless of the global setting.
         *
         * Passed via `--openapi_out=suppressDefaultEnumValues=true:outdir`.
         */
        val suppressDefaultEnumValues: Boolean,

        /**
         * Controls how proto enum value names are written into OAS `enum` arrays.
         *
         * The default is [EnumValueFormat.CANONICAL], which writes each value exactly as its
         * proto name.  [EnumValueFormat.NUMERIC_VALUE] emits the integer number instead, which
         * satisfies Envoy's `always_print_enums_as_ints = true` configuration.
         *
         * Passed via `--openapi_out=enumValueFormat=CANONICAL:outdir`.
         */
        val enumValueFormat: EnumValueFormat,

        /**
         * When `true`, gRPC methods that lack a `google.api.http` annotation are automatically
         * mapped to a REST endpoint using the convention:
         *
         * ```
         * POST /<package>.<ServiceName>/<MethodName>
         * body: "*"
         * ```
         *
         * Explicit `google.api.http` annotations on other methods in the same service always take
         * precedence.  This mirrors Envoy's `auto_mapping` option, which applies the same
         * convention at runtime so that unannotated methods remain reachable via HTTP/JSON.
         *
         * See: [GrpcJsonTranscoder.auto_mapping](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder)
         *
         * Passed via `--openapi_out=autoMapping=true:outdir`.
         */
        val autoMapping: Boolean,

        /**
         * When `true`, every non-repeated, non-message field is added to the `required` array of
         * its containing schema.  Proto3 JSON omits scalar and enum fields whose value equals
         * the type default (0, `""`, `false`, first enum value); enabling this option tells the
         * compiler that Envoy will always include those fields, so they can be modelled as
         * `required` in the OAS schema.
         *
         * Use this option together with Envoy's `always_print_primitive_fields = true` PrintOption.
         *
         * See: [PrintOptions.always_print_primitive_fields](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
         *
         * Passed via `--openapi_out=alwaysPrintPrimitiveFields=true:outdir`.
         */
        val alwaysPrintPrimitiveFields: Boolean,

        /**
         * When `true`, all schema property keys use the raw proto field name (e.g. `my_field`)
         * instead of the `json_name` option value or its lowerCamelCase default (e.g. `myField`).
         * This applies to every property key across all `components/schemas` entries.
         *
         * Use this option together with Envoy's `preserve_proto_field_names = true` PrintOption
         * so the OAS schema matches the JSON field names that Envoy actually emits in responses.
         *
         * Note: proto3 JSON parsers accept **both** the camelCase name and the original proto
         * name in request bodies regardless of this setting.  Setting this option makes the
         * documented (OAS) name match the server-side wire name when `preserve_proto_field_names`
         * is enabled.
         *
         * See: [PrintOptions.preserve_proto_field_names](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
         *
         * Passed via `--openapi_out=preserveProtoFieldNames=true:outdir`.
         */
        val preserveProtoFieldNames: Boolean,

        /**
         * When `true`, a reusable `google.rpc.Status` schema is added to `components/schemas`
         * and every operation's `responses` map gains a `"default"` entry referencing it.
         *
         * This matches Envoy's `convert_grpc_status` option, which translates gRPC error trailers
         * into an HTTP error response whose JSON body is shaped as `google.rpc.Status`:
         *
         * ```json
         * { "code": 5, "message": "not found", "details": [...] }
         * ```
         *
         * Enable this when the Envoy filter is configured with `convert_grpc_status: true` so
         * that API consumers can see the error contract in the generated OpenAPI spec.
         *
         * See: [GrpcJsonTranscoder.convert_grpc_status](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder)
         *
         * Passed via `--openapi_out=convertGrpcStatus=true:outdir`.
         */
        val convertGrpcStatus: Boolean,

        /**
         * When `true`, server-streaming RPC responses are documented with content-type
         * `application/x-ndjson` and a single-message schema (rather than the default
         * `application/json`).  This reflects Envoy's `stream_newline_delimited` PrintOption,
         * which causes each streamed message to be emitted as a separate newline-delimited JSON
         * object rather than as a comma-separated array.
         *
         * Unary (non-streaming) method responses are not affected.
         *
         * **Precedence:** if [streamSseStyleDelimited] is also `true`, this option has no effect —
         * the compiler emits `text/event-stream` instead.  Setting both is a misconfiguration;
         * enable at most one.
         *
         * See: [PrintOptions.stream_newline_delimited](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
         *
         * Passed via `--openapi_out=streamNewlineDelimited=true:outdir`.
         */
        val streamNewlineDelimited: Boolean,

        /**
         * When `true`, server-streaming RPC responses are documented with content-type
         * `text/event-stream` and a single-message schema, reflecting Envoy's
         * `stream_sse_style_delimited` PrintOption which frames each streamed message as an
         * SSE `data:` line.
         *
         * This option takes precedence over [streamNewlineDelimited] when both are `true`.
         * Unary (non-streaming) method responses are not affected.
         *
         * See: [PrintOptions.stream_sse_style_delimited](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
         *
         * Passed via `--openapi_out=streamSseStyleDelimited=true:outdir`.
         */
        val streamSseStyleDelimited: Boolean,

        /**
         * Threshold at which the plugin emits log records via SLF4J.  Accepts any value of
         * [org.slf4j.event.Level] (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`); a record is
         * emitted when its level is greater than or equal to this threshold.  Defaults to
         * `ERROR` so the plugin is quiet by default but still surfaces error-level reports.
         *
         * The option is realised at runtime by programmatically reconfiguring the Log4j 2
         * `Configuration` after [Options] is built, so it controls every logger the plugin
         * (and its dependencies) creates.
         *
         * Passed via `--openapi_out=logLevel=DEBUG:outdir` (case-insensitive).
         */
        val logLevel: Level,

        /**
         * Optional path to a file that receives timestamped log records in addition to the
         * stderr console output.  The stderr `Console` appender is always attached — its lines
         * are prefixed with `[protoc-gen-openapi]` so they stand out from other compiler
         * output protoc may multiplex on the same stream.  When this option is set, a `File`
         * appender is *also* attached at the given path with a `%d{HH:mm:ss.SSS}`-prefixed
         * pattern.
         *
         * Passed via `--openapi_out=logFile=/tmp/protoc.log:outdir`.
         */
        val logFile: String?,

        /**
         * A Java-compatible regular expression tested via [Regex.containsMatchIn] against the
         * fully-qualified service name (`<package>.<ServiceName>`, e.g.
         * `com.example.v1.WidgetService`).  A service is **included** in the generated output
         * only when its fully-qualified name contains at least one match for this expression.
         * Services that do not match are silently omitted; schema types in
         * `components/schemas` that are referenced exclusively by omitted services are also
         * omitted.
         *
         * The fully-qualified service name follows the proto3 `fullIdent` grammar:
         * one or more `.`-separated identifiers, each starting with a letter and continuing
         * with letters, decimal digits, or underscores.
         *
         * **Evaluation order:** [serviceInclude] is tested first.  A service that does not
         * match is dropped immediately, regardless of [serviceExclude].  A service that
         * matches [serviceInclude] but also matches [serviceExclude] is then dropped.
         *
         * **Default:** `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$` — matches
         * every syntactically valid fully-qualified proto service name, so all services
         * pass by default.
         *
         * Passed via `--openapi_out=serviceInclude=<pattern>:outdir`.
         */
        val serviceInclude: String,

        /**
         * A Java-compatible regular expression tested via [Regex.containsMatchIn] against the
         * fully-qualified service name (`<package>.<ServiceName>`).  A service is
         * **excluded** from the generated output when its fully-qualified name contains at
         * least one match for this expression, even if it also matched [serviceInclude].
         * Schema types referenced exclusively by excluded services are also omitted.
         *
         * **Default:** `null` — no services are excluded.
         *
         * Passed via `--openapi_out=serviceExclude=<pattern>:outdir`.
         */
        val serviceExclude: String?,
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

        /**
         * Controls how proto enum value names are written into OAS `enum` arrays.
         * All values are matched case-insensitively in `--openapi_out` parameters.
         */
        public enum class EnumValueFormat {
            /** Write each value exactly as its proto name, e.g. `MY_ENUM_VALUE`. */
            CANONICAL,

            /**
             * Write each value as its integer number (the `EnumValue.number` field, not
             * its ordinal position in the file).  The schema `type` changes to `"integer"`
             * and the `enum` array contains JSON integers.
             *
             * Description bullets include the integer, the proto name, and the leading
             * comment when present — e.g. `` `1 (GREETING_HELLO)` — A formal hello greeting. ``
             *
             * In request payloads Envoy's gRPC-JSON transcoder accepts **either** the
             * canonical name (e.g. `"GREETING_HELLO"`) or the integer (e.g. `1`); in
             * response payloads only the integer form is emitted.  Use this format when
             * configuring the transcoder with `always_print_enums_as_ints = true`.
             *
             * See: [PrintOptions.always_print_enums_as_ints](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
             */
            NUMERIC_VALUE,

            /**
             * Write each value as its proto name lowercased, e.g. `my_enum_value`.
             *
             * Description bullets include the lowercased name and the leading comment
             * when present — e.g. `` `greeting_hello` — A formal hello greeting. ``
             *
             * Envoy's gRPC-JSON transcoder by default only accepts the exact canonical
             * proto name (e.g. `"GREETING_HELLO"`).  Setting
             * `case_insensitive_enum_parsing = true` makes Envoy accept values in any
             * case, including the lowercase form emitted by this format.  Use this
             * format together with that transcoder option so that API clients can send
             * `"greeting_hello"` and Envoy will map it correctly.
             *
             * See: [GrpcJsonTranscoder.case_insensitive_enum_parsing](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder)
             */
            LOWER_CASE,
        }

        /**
         * Controls which package segments are incorporated into `components/schemas` keys.
         * All values are matched case-insensitively in `--openapi_out` parameters.
         */
        public enum class SchemaNamespaceStrategy {
            /** Simple unqualified message name only — current default behaviour. */
            NONE,

            /**
             * All package segments are prepended to the message name.
             * E.g. `.com.example.v1.Widget` → `com_example_v1_Widget` (with `UNDERSCORE`).
             */
            FULL_PACKAGE,

            /**
             * Like [FULL_PACKAGE] but the longest package prefix shared by **every** schema
             * produced in the same output document is stripped first, reducing key verbosity
             * while keeping keys unique.
             * E.g. with common prefix `com.example`, `.com.example.catalog.v1.Widget` →
             * `catalog_v1_Widget` (with `UNDERSCORE`).
             */
            SIMPLIFIED_PACKAGE,
        }

        /**
         * Separator character placed between each segment of a namespaced schema key.
         * All values are matched case-insensitively in `--openapi_out` parameters.
         */
        public enum class SchemaNamespaceSeparator {
            /** No separator — segments are concatenated directly, e.g. `comexampleWidget`. */
            NONE,
            UNDERSCORE,
            DASH,
            DOT,
        }

        /**
         * Case transformation applied to package segments of a namespaced schema key.
         * All values are matched case-insensitively in `--openapi_out` parameters.
         */
        public enum class SchemaNamespaceCasing {
            /** Leave each segment exactly as written in the proto source. */
            NONE,

            /** Capitalise the first character of each package segment, e.g. `catalog` → `Catalog`. */
            CAPITALIZED,

            /** Uppercase every character of each package segment, e.g. `catalog` → `CATALOG`. */
            UPPER_CASE,
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
             * @see [Options.validationErrorsAreFatal]
             */
            public var validationErrorsAreFatal: Boolean =
                parameters.get<Boolean>("validationErrorsAreFatal") ?: false

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

            /**
             * @see [Options.schemaNamespaceStrategy]
             */
            public var schemaNamespaceStrategy: SchemaNamespaceStrategy =
                parameters.get<SchemaNamespaceStrategy>("schemaNamespaceStrategy")
                    ?: SchemaNamespaceStrategy.NONE

            /**
             * @see [Options.schemaNamespaceSeparator]
             */
            public var schemaNamespaceSeparator: SchemaNamespaceSeparator =
                parameters.get<SchemaNamespaceSeparator>("schemaNamespaceSeparator")
                    ?: SchemaNamespaceSeparator.NONE

            /**
             * @see [Options.schemaNamespaceCasing]
             */
            public var schemaNamespaceCasing: SchemaNamespaceCasing =
                parameters.get<SchemaNamespaceCasing>("schemaNamespaceCasing")
                    ?: SchemaNamespaceCasing.NONE

            /**
             * @see [Options.schemaNamespaceVersionExtraction]
             */
            public var schemaNamespaceVersionExtraction: Boolean =
                parameters.get<Boolean>("schemaNamespaceVersionExtraction") ?: false

            /**
             * @see [Options.setSchemaTitleToProtoSimpleName]
             */
            public var setSchemaTitleToProtoSimpleName: Boolean =
                parameters.get<Boolean>("setSchemaTitleToProtoSimpleName") ?: false

            /**
             * @see [Options.inlineEnums]
             */
            public var inlineEnums: Boolean = parameters.get<Boolean>("inlineEnums") ?: false

            /**
             * @see [Options.inlineRequestSchemas]
             */
            public var inlineRequestSchemas: Boolean =
                parameters.get<Boolean>("inlineRequestSchemas") ?: true

            /**
             * @see [Options.inlineResponseSchemas]
             */
            public var inlineResponseSchemas: Boolean =
                parameters.get<Boolean>("inlineResponseSchemas") ?: true

            /**
             * @see [Options.suppressDefaultEnumValues]
             */
            public var suppressDefaultEnumValues: Boolean =
                parameters.get<Boolean>("suppressDefaultEnumValues") ?: false

            /**
             * @see [Options.enumValueFormat]
             */
            public var enumValueFormat: EnumValueFormat =
                parameters.get<EnumValueFormat>("enumValueFormat") ?: EnumValueFormat.CANONICAL

            /**
             * @see [Options.autoMapping]
             */
            public var autoMapping: Boolean =
                parameters.get<Boolean>("autoMapping") ?: false

            /**
             * @see [Options.alwaysPrintPrimitiveFields]
             */
            public var alwaysPrintPrimitiveFields: Boolean =
                parameters.get<Boolean>("alwaysPrintPrimitiveFields") ?: false

            /**
             * @see [Options.preserveProtoFieldNames]
             */
            public var preserveProtoFieldNames: Boolean =
                parameters.get<Boolean>("preserveProtoFieldNames") ?: false

            /**
             * @see [Options.convertGrpcStatus]
             */
            public var convertGrpcStatus: Boolean =
                parameters.get<Boolean>("convertGrpcStatus") ?: false

            /**
             * @see [Options.streamNewlineDelimited]
             */
            public var streamNewlineDelimited: Boolean =
                parameters.get<Boolean>("streamNewlineDelimited") ?: false

            /**
             * @see [Options.streamSseStyleDelimited]
             */
            public var streamSseStyleDelimited: Boolean =
                parameters.get<Boolean>("streamSseStyleDelimited") ?: false

            /**
             * @see [Options.logLevel]
             */
            public var logLevel: org.slf4j.event.Level =
                parameters.get<org.slf4j.event.Level>("logLevel") ?: org.slf4j.event.Level.ERROR

            /**
             * @see [Options.logFile]
             */
            public var logFile: String? = parameters.get<String>("logFile")

            /**
             * @see [Options.serviceInclude]
             */
            public var serviceInclude: String =
                parameters.get<String>("serviceInclude") ?: SERVICE_INCLUDE_DEFAULT

            /**
             * @see [Options.serviceExclude]
             */
            public var serviceExclude: String? =
                parameters.get<String>("serviceExclude")

            public companion object {
                private const val SERVICE_INCLUDE_DEFAULT =
                    """^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)*$"""

                public fun from(parameters: Parameters): Builder = Builder(parameters)
            }

            public fun build(): Options =
                Options(
                    merge = merge,
                    version = version,
                    validateOutput = validateOutput,
                    validationErrorsAreFatal = validationErrorsAreFatal,
                    outputFormat = outputFormat,
                    autoTagServices = autoTagServices,
                    schemaNamespaceStrategy = schemaNamespaceStrategy,
                    schemaNamespaceSeparator = schemaNamespaceSeparator,
                    schemaNamespaceCasing = schemaNamespaceCasing,
                    schemaNamespaceVersionExtraction = schemaNamespaceVersionExtraction,
                    setSchemaTitleToProtoSimpleName = setSchemaTitleToProtoSimpleName,
                    inlineEnums = inlineEnums,
                    inlineRequestSchemas = inlineRequestSchemas,
                    inlineResponseSchemas = inlineResponseSchemas,
                    suppressDefaultEnumValues = suppressDefaultEnumValues,
                    enumValueFormat = enumValueFormat,
                    autoMapping = autoMapping,
                    alwaysPrintPrimitiveFields = alwaysPrintPrimitiveFields,
                    preserveProtoFieldNames = preserveProtoFieldNames,
                    convertGrpcStatus = convertGrpcStatus,
                    streamNewlineDelimited = streamNewlineDelimited,
                    streamSseStyleDelimited = streamSseStyleDelimited,
                    logLevel = logLevel,
                    logFile = logFile,
                    serviceInclude = serviceInclude,
                    serviceExclude = serviceExclude,
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
            val options = Options.Builder.from(cgreq.parameters).apply(block).build()
            applyLoggingConfiguration(options)
            return ProtocGenOpenAPI(cgreq, options)
        }

        /**
         * Reconfigures the Log4j 2 `Configuration` from [Options.logLevel] and [Options.logFile].
         *
         * Appenders are attached to the `com.engine` logger only, so downstream dependencies'
         * loggers stay silent regardless of their own level.  A stderr `Console` appender is
         * always attached, prefixed with `[protoc-gen-openapi]` so its records stand out from
         * other compiler output protoc may multiplex on the same stream.  When [Options.logFile]
         * is non-null a `File` appender is also attached, writing timestamped records to the
         * given path.  The root logger is silenced with `Level.OFF` to discard anything emitted
         * outside the `com.engine` tree.  Invoked from [from] immediately after [Options] is
         * built so subsequent `LoggerFactory.getLogger` calls observe the resolved configuration.
         */
        private fun applyLoggingConfiguration(options: Options) {
            val cb = ConfigurationBuilderFactory.newConfigurationBuilder()
            cb.setStatusLevel(Log4jLevel.OFF)

            cb.add(
                cb.newAppender("stderr", "Console")
                    .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR)
                    .add(
                        cb.newLayout("PatternLayout")
                            .addAttribute("pattern", "[protoc-gen-openapi] %-5level %logger{36} - %msg%n"),
                    ),
            )

            val engine =
                cb.newLogger("com.engine", options.logLevel.toLog4j())
                    .addAttribute("additivity", false)
                    .add(cb.newAppenderRef("stderr"))

            options.logFile?.let { path ->
                cb.add(
                    cb.newAppender("file", "File")
                        .addAttribute("fileName", path)
                        .add(
                            cb.newLayout("PatternLayout")
                                .addAttribute("pattern", "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"),
                        ),
                )
                engine.add(cb.newAppenderRef("file"))
            }

            cb.add(engine)
            cb.add(cb.newRootLogger(Log4jLevel.OFF))
            val config = cb.build(false)
            // `initialize` first so a fresh LoggerContext starts with our
            // configuration directly, bypassing Log4j's default config-file
            // probing (~24 file paths) that breaks under native-image's
            // strict missing-resource registration. `reconfigure` then
            // re-applies the same config so subsequent `from(...)` calls
            // in the same process pick up updated log levels.
            Configurator.initialize(config)
            Configurator.reconfigure(config)
        }

        private fun Level.toLog4j(): Log4jLevel =
            when (this) {
                Level.ERROR -> Log4jLevel.ERROR
                Level.WARN -> Log4jLevel.WARN
                Level.INFO -> Log4jLevel.INFO
                Level.DEBUG -> Log4jLevel.DEBUG
                Level.TRACE -> Log4jLevel.TRACE
            }
    }

    public fun compile(): PluginProtos.CodeGeneratorResponse = Compiler(request, options).compile()
}
