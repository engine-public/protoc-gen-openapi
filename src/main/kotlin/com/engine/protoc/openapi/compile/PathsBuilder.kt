package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.Operation
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.toJson
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.service.MethodDescriptorProtoWrapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos

/**
 * Builds the `paths` section of the OpenAPI document from gRPC service definitions and their
 * `google.api.http` + `engine.protoc.openapi.method` annotations.
 *
 * Paths with the same URL are merged so that multiple HTTP methods end up in a single path item.
 */
internal class PathsBuilder(
    private val ctx: JsonContext,
    private val collector: MessageCollector,
) {

    /** Collects all paths from [files] into a merged `paths` ObjectNode. */
    fun build(files: List<FileDescriptorProtoWrapper>): ObjectNode {
        val byPath = LinkedHashMap<String, ObjectNode>()

        for (file in files) {
            for (service in file.services) {
                for (method in service.methods) {
                    val httpRule = method.options
                        ?.findExtension(AnnotationsProto.http)?.value
                        ?: continue

                    val binding = httpRule.primaryBinding() ?: continue
                    val operationNode = buildOperation(method, binding, httpRule)
                    val pathItem = byPath.getOrPut(binding.path) { ctx.obj() }
                    pathItem.set<JsonNode>(binding.httpMethod, operationNode)
                }
            }
        }

        val pathsNode = ctx.obj()
        for ((path, pathItem) in byPath) {
            pathsNode.set<JsonNode>(path, pathItem)
        }
        return pathsNode
    }

    @Suppress("LongMethod")
    private fun buildOperation(
        method: MethodDescriptorProtoWrapper,
        binding: HttpBinding,
        httpRule: HttpRule,
    ): ObjectNode {
        val defaultInstance = Operation.getDefaultInstance()
        val annotation: Operation? = method.options
            ?.findExtension(Annotations.method)?.value
            ?.takeIf { it != defaultInstance }

        val inputTypeName = method.proto.inputType
        val outputTypeName = method.proto.outputType

        // Seed the collector so schemas are generated for in/out types
        collector.collect(inputTypeName)
        collector.collect(outputTypeName)

        val node = ctx.obj()

        // ---- Summary (leading comment → annotation override) -------------
        val comment = method.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        val summary = annotation?.takeIf { it.hasSummary() }?.summary ?: comment?.firstLine()
        if (summary != null) node.put("summary", summary)

        if (annotation?.hasDescription() == true) node.put("description", annotation.description)
        if (annotation?.hasOperationId() == true) node.put("operationId", annotation.operationId)

        if (annotation?.tagsList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (t in annotation.tagsList) arr.add(t)
            node.set<JsonNode>("tags", arr)
        }

        // ---- Parameters -------------------------------------------------
        val pathParamNodes = inferPathParameters(binding.path, inputTypeName)
        val annotationParamNodes = (annotation?.parametersList ?: emptyList())
            .map { it.toJson(ctx) }
        val allParams = pathParamNodes + annotationParamNodes
        if (allParams.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (p in allParams) arr.add(p)
            node.set<JsonNode>("parameters", arr)
        }

        // ---- Request body -----------------------------------------------
        val body = httpRule.body
        when {
            annotation?.hasRequestBody() == true -> {
                node.set<JsonNode>("requestBody", annotation.requestBody.toJson(ctx))
                if (annotation.requestBody.hasRequestBody()) {
                    annotation.requestBody.requestBody.contentMap.values.forEach { mt ->
                        if (mt.hasSchema()) collector.collectFromSchema(mt.schema)
                    }
                }
            }

            body == "*" -> node.set<JsonNode>("requestBody", inferRequestBody(inputTypeName))

            body.isNotEmpty() -> node.set<JsonNode>(
                "requestBody",
                inferRequestBodyField(inputTypeName, body),
            )
        }

        // ---- Responses --------------------------------------------------
        if (annotation?.hasResponses() == true) {
            node.set<JsonNode>("responses", annotation.responses.toJson(ctx))
        } else {
            node.set<JsonNode>("responses", inferResponses(outputTypeName))
        }

        // ---- Security / deprecated --------------------------------------
        if (annotation?.securityList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (s in annotation.securityList) arr.add(s.toJson(ctx))
            node.set<JsonNode>("security", arr)
        }
        if (annotation?.hasDeprecated() == true) node.put("deprecated", annotation.deprecated)

        return node
    }

    // ---- Path parameter inference ----------------------------------------

    private val pathParamRegex = Regex("""\{(\w+)\}""")

    private fun inferPathParameters(
        pathTemplate: String,
        inputTypeName: String,
    ): List<ObjectNode> =
        pathParamRegex.findAll(pathTemplate)
            .map { buildPathParam(it.groupValues[1], inputTypeName) }
            .toList()

    private fun buildPathParam(
        name: String,
        inputTypeName: String,
    ): ObjectNode {
        val node = ctx.obj()
        node.put("name", name)
        node.put("in", "path")
        node.put("required", true)
        node.set<JsonNode>("schema", paramFieldSchema(name, inputTypeName))
        return node
    }

    /**
     * Infers the JSON schema for path parameter [name] from the RPC input type [inputTypeName].
     * For well-known wrapper types (e.g. `Int64Value`) the inner `value` field type is used.
     */
    private fun paramFieldSchema(
        name: String,
        inputTypeName: String,
    ): ObjectNode {
        val wkt = ctx.wellKnownScalarSchema(inputTypeName)
        if (wkt != null && name == "value") return wkt

        val msgWrapper = ctx.messageIndex.find(inputTypeName) ?: return ctx.obj()
        val field = msgWrapper.proto.fieldList
            .find { it.jsonName == name || it.name == name }
            ?: return ctx.obj()
        return fieldTypeSchema(field)
    }

    // ---- Request body inference ------------------------------------------

    private fun inferRequestBody(inputTypeName: String): ObjectNode {
        val node = ctx.obj()
        node.put("required", true)
        val content = ctx.obj()
        content.set<JsonNode>("application/json", schemaMediaType(inputTypeName))
        node.set<JsonNode>("content", content)
        return node
    }

    private fun inferRequestBodyField(
        inputTypeName: String,
        fieldName: String,
    ): ObjectNode {
        val msg = ctx.messageIndex.find(inputTypeName)
        val field = msg?.proto?.fieldList?.find { it.name == fieldName || it.jsonName == fieldName }
        val schema = if (field != null) fieldTypeSchema(field) else ctx.obj()

        val node = ctx.obj()
        node.put("required", true)
        val content = ctx.obj()
        val mediaType = ctx.obj()
        mediaType.set<JsonNode>("schema", schema)
        content.set<JsonNode>("application/json", mediaType)
        node.set<JsonNode>("content", content)
        return node
    }

    internal fun schemaMediaType(typeName: String): ObjectNode {
        val schema = ctx.wellKnownScalarSchema(typeName)
            ?: ctx.obj().also {
                it.put("\$ref", "#/components/schemas/${ctx.messageIndex.simpleNameOf(typeName)}")
            }
        val mediaType = ctx.obj()
        mediaType.set<JsonNode>("schema", schema)
        return mediaType
    }

    // ---- Response inference ---------------------------------------------

    private fun inferResponses(outputTypeName: String): ObjectNode {
        val responses = ctx.obj()
        val response = ctx.obj()
        response.put("description", "OK")
        if (outputTypeName.isNotEmpty() && outputTypeName != ".google.protobuf.Empty") {
            val content = ctx.obj()
            content.set<JsonNode>("application/json", schemaMediaType(outputTypeName))
            response.set<JsonNode>("content", content)
        }
        responses.set<JsonNode>("200", response)
        return responses
    }

    // ---- Field type → JSON Schema ----------------------------------------

    internal fun fieldTypeSchema(field: DescriptorProtos.FieldDescriptorProto): ObjectNode {
        val isRepeated =
            field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
        val base = baseFieldSchema(field)
        return if (isRepeated && !isMapField(field)) {
            ctx.obj().also {
                it.put("type", "array")
                it.set<JsonNode>("items", base)
            }
        } else {
            base
        }
    }

    private fun baseFieldSchema(field: DescriptorProtos.FieldDescriptorProto): ObjectNode {
        val node = ctx.obj()
        return when (field.type) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32,
            -> node.also {
                it.put("type", "integer")
                it.put("format", "int32")
            }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64,
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64,
            -> node.also {
                it.put("type", "integer")
                it.put("format", "int64")
            }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT ->
                node.also {
                    it.put("type", "number")
                    it.put("format", "float")
                }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE ->
                node.also {
                    it.put("type", "number")
                    it.put("format", "double")
                }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL ->
                node.also { it.put("type", "boolean") }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING ->
                node.also { it.put("type", "string") }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES ->
                node.also {
                    it.put("type", "string")
                    it.put("format", "byte")
                }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM ->
                node.also { it.put("type", "string") }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> {
                val typeName = field.typeName
                ctx.wellKnownScalarSchema(typeName) ?: node.also {
                    it.put("\$ref", "#/components/schemas/${ctx.messageIndex.simpleNameOf(typeName)}")
                }
            }

            else -> node
        }
    }

    private fun isMapField(field: DescriptorProtos.FieldDescriptorProto): Boolean {
        if (field.type != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) return false
        return ctx.messageIndex.find(field.typeName)?.proto?.options?.mapEntry == true
    }
}

// ---------------------------------------------------------------------------
// HttpRule helpers
// ---------------------------------------------------------------------------

internal data class HttpBinding(val httpMethod: String, val path: String, val body: String)

internal fun HttpRule.primaryBinding(): HttpBinding? {
    val body = body
    return when (patternCase) {
        HttpRule.PatternCase.GET -> HttpBinding("get", getGet(), body)
        HttpRule.PatternCase.POST -> HttpBinding("post", getPost(), body)
        HttpRule.PatternCase.PUT -> HttpBinding("put", getPut(), body)
        HttpRule.PatternCase.DELETE -> HttpBinding("delete", getDelete(), body)
        HttpRule.PatternCase.PATCH -> HttpBinding("patch", getPatch(), body)
        else -> null
    }
}

// ---------------------------------------------------------------------------
// String helpers
// ---------------------------------------------------------------------------

private fun String.firstLine(): String? = lines().firstOrNull { it.isNotBlank() }?.trim()
