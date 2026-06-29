package com.engine.protoc.openapi.compile.json

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.compile.EnumIndex
import com.engine.protoc.openapi.compile.MessageIndex
import com.engine.protoc.openapi.compile.RpcIndex
import com.engine.protoc.openapi.compile.SchemaKeyResolver
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/** Shared state passed through all serialization functions. */
internal class JsonContext(
    val mapper: ObjectMapper,
    val messageIndex: MessageIndex,
    val enumIndex: EnumIndex,
    val rpcIndex: RpcIndex,
    val schemaKeyResolver: SchemaKeyResolver,
    val inlineEnums: Boolean,
    val suppressDefaultEnumValues: Boolean,
    val enumValueFormat: ProtocGenOpenAPI.Options.EnumValueFormat,
    /**
     * When true, schema property keys use the raw proto field name (e.g. `my_field`) instead of
     * the `json_name` option value or lowerCamelCase default (e.g. `myField`).
     *
     * Mirrors Envoy's `preserve_proto_field_names` PrintOption.
     */
    val preserveProtoFieldNames: Boolean = false,
    /**
     * When true, every non-repeated, non-message field is added to the schema `required` array.
     * Proto3 JSON omits scalar and enum fields whose value equals the type default; enabling this
     * option signals that Envoy will always include them, making them `required` in the OAS sense.
     *
     * Mirrors Envoy's `always_print_primitive_fields` PrintOption.
     */
    val alwaysPrintPrimitiveFields: Boolean = false,
    /**
     * When true, a `google.rpc.Status` schema is injected into `components/schemas` and a
     * `"default"` error response referencing it is added to every operation's `responses` map.
     *
     * Mirrors Envoy's `convert_grpc_status` option.
     */
    val convertGrpcStatus: Boolean = false,
    /**
     * When true, server-streaming responses use content-type `application/x-ndjson` with a
     * single-message schema instead of `application/json`.
     *
     * Mirrors Envoy's `stream_newline_delimited` PrintOption.
     */
    val streamNewlineDelimited: Boolean = false,
    /**
     * When true, server-streaming responses use content-type `text/event-stream` with a
     * single-message schema.  Takes precedence over [streamNewlineDelimited].
     *
     * Mirrors Envoy's `stream_sse_style_delimited` PrintOption.
     */
    val streamSseStyleDelimited: Boolean = false,
) {
    fun obj(): ObjectNode = mapper.createObjectNode()

    /**
     * Deep-merges [other] into this node.  Object nodes are recursively merged; all other values
     * are overwritten by the value in [other].
     */
    fun ObjectNode.deepMerge(other: ObjectNode): ObjectNode {
        for ((key, value) in other.properties()) {
            val existing = get(key)
            if (existing is ObjectNode && value is ObjectNode) {
                existing.deepMerge(value)
            } else {
                set(key, value)
            }
        }
        return this
    }

    /**
     * Resolves a `proto_message_ref` type URL to an OpenAPI `$ref` string pointing into
     * `#/components/schemas`.
     *
     * The type URL format is `type.googleapis.com/<package>.<MessageName>`.
     */
    fun resolveProtoRef(typeUrl: String): String {
        val typeName = ".${typeUrl.substringAfterLast('/')}"
        return "#/components/schemas/${schemaKeyResolver.buildPhaseKeyOf(typeName)}"
    }

    /**
     * Resolves a `proto_rpc_ref` service method reference (format `package.Service#Method`) to
     * a valid RFC 3986 URI-reference pointing into `#/paths`.
     *
     * When [includeMethod] is true the HTTP method is appended (for `Link.operationRef`).
     * When false only the path item pointer is returned (for PathItem `$ref` and Reference `$ref`).
     *
     * Path template variables (e.g. `{id}`) are percent-encoded in the URI fragment so that
     * `{` and `}` do not violate the URI-reference syntax.
     */
    fun resolveProtoRpcRef(
        rpcRef: String,
        includeMethod: Boolean,
    ): String {
        val binding = rpcIndex.findBinding(rpcRef) ?: return "#/paths/unknown"
        val pointer = binding.path
            .replace("~", "~0")
            .replace("/", "~1")
            .replace("{", "%7B")
            .replace("}", "%7D")
        return if (includeMethod) "#/paths/$pointer/${binding.httpMethod}" else "#/paths/$pointer"
    }

    /** Returns an inline JSON Schema for a well-known protobuf scalar wrapper type, or null. */
    fun wellKnownScalarSchema(typeName: String): ObjectNode? =
        when (typeName) {
            ".google.protobuf.StringValue" -> scalar("string")
            ".google.protobuf.Int32Value" -> scalar("integer", "int32")
            ".google.protobuf.Int64Value" -> scalar("integer", "int64")
            ".google.protobuf.UInt32Value" -> scalar("integer", "int32")
            ".google.protobuf.UInt64Value" -> scalar("integer", "int64")
            ".google.protobuf.FloatValue" -> scalar("number", "float")
            ".google.protobuf.DoubleValue" -> scalar("number", "double")
            ".google.protobuf.BoolValue" -> scalar("boolean")
            ".google.protobuf.BytesValue" -> scalar("string", "byte")
            ".google.protobuf.Duration" -> scalar("string", "duration")
            ".google.protobuf.Timestamp" -> scalar("string", "date-time")
            else -> null
        }

    /**
     * Returns an inline JSON Schema for any well-known protobuf type, including the structural
     * messages (`Any`, `Struct`, `Value`, `ListValue`, `FieldMask`, `Empty`) that have no useful
     * `components/schemas` representation derived from their proto fields.  Returns `null` when
     * the type is not well-known and must be emitted via `$ref` or recursive expansion.
     */
    fun wellKnownTypeSchema(typeName: String): ObjectNode? = wellKnownScalarSchema(typeName) ?: wellKnownStructuralSchema(typeName)

    /**
     * Inline schemas for protobuf structural well-known types.  These messages encode as
     * arbitrary JSON in proto3 JSON (`Any` carries `@type` + free-form members, `Struct` is an
     * open object, `Value` is any JSON value, `ListValue` is an array of those, `FieldMask` is a
     * comma-joined path string, `Empty` is the empty object), so the proto field structure of
     * the message itself is not a useful schema to surface to API clients.
     */
    private fun wellKnownStructuralSchema(typeName: String): ObjectNode? =
        when (typeName) {
            ".google.protobuf.Any" -> openObject()

            ".google.protobuf.Struct" -> openObject()

            ".google.protobuf.Value" -> obj()

            ".google.protobuf.ListValue" -> obj().also {
                it.put("type", "array")
                it.set("items", obj())
            }

            ".google.protobuf.FieldMask" -> scalar("string")

            ".google.protobuf.Empty" -> obj().also {
                it.put("type", "object")
                it.set("properties", obj())
                it.put("additionalProperties", false)
            }

            else -> null
        }

    private fun openObject(): ObjectNode =
        obj().also {
            it.put("type", "object")
            it.put("additionalProperties", true)
        }

    private fun scalar(
        type: String,
        format: String? = null,
    ): ObjectNode {
        val node = obj()
        node.put("type", type)
        if (format != null) node.put("format", format)
        return node
    }
}
