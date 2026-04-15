package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.Operation
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.putExtensionsInto
import com.engine.protoc.openapi.compile.json.toJson
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.service.MethodDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
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
    fun build(files: List<FileDescriptorProtoWrapper>): ObjectNode = buildForServices(files.flatMap { it.services })

    /** Collects paths for a single [service] into a `paths` ObjectNode. */
    fun buildForService(service: ServiceDescriptorProtoWrapper): ObjectNode = buildForServices(listOf(service))

    private fun buildForServices(services: List<ServiceDescriptorProtoWrapper>): ObjectNode {
        val byPath = LinkedHashMap<String, ObjectNode>()

        for (service in services) {
            for (method in service.methods) {
                val httpRule = method.options
                    ?.findExtension(AnnotationsProto.http)?.value
                    ?: continue

                val binding = httpRule.primaryBinding() ?: continue
                val (effectivePath, operationNode) = buildOperation(method, binding, httpRule)
                val pathItem = byPath.getOrPut(effectivePath) { ctx.obj() }
                pathItem.set<JsonNode>(binding.httpMethod, operationNode)
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
    ): Pair<String, ObjectNode> {
        val defaultInstance = Operation.getDefaultInstance()
        val annotation: Operation? = method.options
            ?.findExtension(Annotations.method)?.value
            ?.takeIf { it != defaultInstance }

        val inputTypeName = method.proto.inputType
        val outputTypeName = method.proto.outputType

        // When response_body names a field, the HTTP response carries that field's value directly
        // rather than the full output message.  The wrapper message is not referenced in the
        // output schema, so we collect the field's type instead.
        val responseBodyField = httpRule.responseBody.takeIf { it.isNotEmpty() }

        // Always collect the output type — it is an API entity that clients receive.
        // When response_body names a field, we collect that field's type instead of the wrapper.
        // If the field cannot be resolved the annotation is misconfigured; fail loudly so the
        // developer gets a clear error rather than a silently invalid spec.
        if (responseBodyField != null) {
            if (!collectBodyFieldType(outputTypeName, responseBodyField)) {
                val outputSimpleName = ctx.messageIndex.simpleNameOf(outputTypeName)
                throw IllegalArgumentException(
                    "response_body field '$responseBodyField' not found in $outputSimpleName",
                )
            }
        } else {
            collector.collect(outputTypeName)
        }

        // Collect schema types for the request body, mirroring what the schema-emission
        // phase will actually $ref.
        //
        //   body == ""  — The input message is a pure parameter bag; its fields become
        //                 path/query/header parameters.  No body schema is emitted, so
        //                 nothing needs to be collected.
        //
        //   body == "*" with explicit requestBody annotation — The annotation provides its
        //                 own schema, so the input message itself is not $ref'd.  Types
        //                 referenced via proto_message_ref in the annotation are handled by
        //                 collectFromSchema at the annotation-processing site.
        //
        //   body == "*" without explicit requestBody annotation — inferRequestBody() emits a
        //                 $ref to the full input type, so we must collect it.
        //
        //   body == "<field>" — inferRequestBodyField() emits a $ref to the named field's
        //                 message type (if it is a message), so we must collect that type.
        //                 If the field cannot be resolved the annotation is misconfigured;
        //                 fail loudly rather than silently emitting an empty or dangling schema.
        val hasExplicitRequestBody = annotation?.hasRequestBody() == true
        if (binding.body == "*" && !hasExplicitRequestBody) {
            collector.collect(inputTypeName)
        } else if (binding.body.isNotEmpty() && binding.body != "*" && !hasExplicitRequestBody) {
            if (!collectBodyFieldType(inputTypeName, binding.body)) {
                val inputSimpleName = ctx.messageIndex.simpleNameOf(inputTypeName)
                throw IllegalArgumentException(
                    "body field '${binding.body}' not found in $inputSimpleName",
                )
            }
        }

        val node = ctx.obj()

        // ---- Summary (leading comment → annotation override) -------------
        val comment = method.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        val summary = annotation?.takeIf { it.hasSummary() }?.summary ?: comment?.firstSentence()
        if (summary != null) node.put("summary", summary)

        val description = when {
            annotation?.hasDescription() == true -> annotation.description
            comment != null -> comment
            else -> null
        }
        if (description != null) node.put("description", description)
        if (annotation?.hasOperationId() == true) node.put("operationId", annotation.operationId)

        if (annotation?.tagsList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (t in annotation.tagsList) arr.add(t)
            node.set<JsonNode>("tags", arr)
        }

        // ---- Parameters -------------------------------------------------
        val annotatedParams = annotation?.parametersList ?: emptyList()
        val annotatedPathParams = annotatedParams
            .filter { it.hasParameter() && it.parameter.`in` == "path" }

        val (effectivePath, pathParamNodes) = if (annotatedPathParams.isEmpty()) {
            binding.path to inferPathParameters(binding.path, inputTypeName)
        } else {
            buildAnnotatedPathParams(binding.path, inputTypeName, annotatedPathParams)
        }

        val allParams = if (annotatedPathParams.isEmpty()) {
            // No annotated path params: inferred path params first, then annotated non-path params
            val annotatedNonPathParams = annotatedParams
                .filter { !it.hasParameter() || it.parameter.`in` != "path" }
            pathParamNodes + annotatedNonPathParams.map { it.toJson(ctx) }
        } else {
            // Annotated path params exist: preserve full annotation order
            var pathParamIndex = 0
            annotatedParams.map { param ->
                if (param.hasParameter() && param.parameter.`in` == "path") {
                    pathParamNodes[pathParamIndex++]
                } else {
                    param.toJson(ctx)
                }
            }
        }
        if (allParams.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (p in allParams) arr.add(p)
            node.set<JsonNode>("parameters", arr)
        }

        // ---- Request body -----------------------------------------------
        val body = httpRule.body
        when {
            annotation?.hasRequestBody() == true -> {
                val requestBodyNode = annotation.requestBody.toJson(ctx)
                injectMissingRef(requestBodyNode.get("content") as? ObjectNode, inputTypeName)
                node.set<JsonNode>("requestBody", requestBodyNode)
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
            node.set<JsonNode>("responses", inferResponses(outputTypeName, responseBodyField))
        }

        // ---- Security / deprecated --------------------------------------
        if (annotation?.securityList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (s in annotation.securityList) arr.add(s.toJson(ctx))
            node.set<JsonNode>("security", arr)
        }
        if (annotation?.hasDeprecated() == true) node.put("deprecated", annotation.deprecated)
        if (annotation?.hasExternalDocs() == true) {
            node.set<JsonNode>("externalDocs", annotation.externalDocs.toJson(ctx))
        }
        if (annotation?.callbacksMap?.isNotEmpty() == true) {
            val cbNode = ctx.obj()
            for ((k, v) in annotation.callbacksMap) cbNode.set<JsonNode>(k, v.toJson(ctx))
            node.set<JsonNode>("callbacks", cbNode)
        }
        if (annotation?.serversList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (s in annotation.serversList) arr.add(s.toJson(ctx))
            node.set<JsonNode>("servers", arr)
        }
        annotation?.extensionsMap?.putExtensionsInto(node, ctx)

        return effectivePath to node
    }

    // ---- Annotated path parameter merging --------------------------------

    /**
     * Aligns [annotatedParams] positionally against path parameters inferred from
     * [originalPath]/[inputTypeName].  For each (inferred, annotated) pair:
     *   - the annotated parameter's fields override the inferred ones
     *   - schemas are deep-merged (inferred as base, annotation overrides on top)
     *   - the path template key is rewritten to use the annotated parameter name
     *
     * Any inferred parameters beyond the length of [annotatedParams] are appended unchanged.
     */
    private fun buildAnnotatedPathParams(
        originalPath: String,
        inputTypeName: String,
        annotatedParams: List<com.engine.protoc.openapi.model.ParameterOrReference>,
    ): Pair<String, List<ObjectNode>> {
        val inferredNodes = inferPathParameters(originalPath, inputTypeName)
        val inferredNames = pathParamRegex.findAll(originalPath).map { it.groupValues[1] }.toList()

        var rewrittenPath = originalPath
        val mergedNodes = annotatedParams.mapIndexed { i, annotatedParam ->
            val inferredNode = inferredNodes.getOrNull(i) ?: ctx.obj()
            val annotatedNode = annotatedParam.toJson(ctx)

            val annotatedName = annotatedNode.path("name").asText("")
            val inferredName = inferredNames.getOrNull(i) ?: ""
            if (annotatedName.isNotEmpty() && inferredName.isNotEmpty() && annotatedName != inferredName) {
                rewrittenPath = rewrittenPath.replace("{$inferredName}", "{$annotatedName}")
            }

            // Deep-merge schemas: inferred as base, annotation overrides on top
            val mergedSchema = (inferredNode.get("schema") as? ObjectNode)?.deepCopy() ?: ctx.obj()
            (annotatedNode.get("schema") as? ObjectNode)?.also { annotatedSchema ->
                with(ctx) { mergedSchema.deepMerge(annotatedSchema) }
            }

            // Build merged param: inferred fields as base, annotation fields override
            val merged = ctx.obj()
            for ((k, v) in inferredNode.fields()) if (k != "schema") merged.set<JsonNode>(k, v)
            for ((k, v) in annotatedNode.fields()) if (k != "schema") merged.set<JsonNode>(k, v)
            if (mergedSchema.size() > 0) merged.set<JsonNode>("schema", mergedSchema)
            merged
        }

        // Keep any inferred params beyond what the annotation covers
        val remaining = inferredNodes.drop(annotatedParams.size)
        return rewrittenPath to (mergedNodes + remaining)
    }

    // ---- $ref injection --------------------------------------------------

    /**
     * For each media type in [contentNode] whose `schema` lacks a `$ref`, injects one derived
     * from [inputTypeName].  No-ops for well-known scalar wrapper types since those inline
     * directly rather than referencing a component schema.
     */
    private fun injectMissingRef(
        contentNode: ObjectNode?,
        inputTypeName: String,
    ) {
        if (contentNode == null) return
        if (ctx.wellKnownScalarSchema(inputTypeName) != null) return
        val ref = "#/components/schemas/${ctx.messageIndex.simpleNameOf(inputTypeName)}"
        for ((_, mediaTypeNode) in contentNode.fields()) {
            if (mediaTypeNode !is ObjectNode) continue
            val schemaNode = mediaTypeNode.get("schema") as? ObjectNode ?: continue
            if (schemaNode.size() == 0) schemaNode.put("\$ref", ref)
        }
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

    private fun inferResponses(
        outputTypeName: String,
        responseBodyField: String? = null,
    ): ObjectNode {
        val responses = ctx.obj()
        val response = ctx.obj()
        response.put("description", "OK")
        if (outputTypeName.isNotEmpty() && outputTypeName != ".google.protobuf.Empty") {
            val content = ctx.obj()
            val mediaType = if (responseBodyField != null) {
                responseBodyFieldSchema(outputTypeName, responseBodyField)
                    ?.let { schema -> ctx.obj().also { it.set<JsonNode>("schema", schema) } }
                    ?: schemaMediaType(outputTypeName)
            } else {
                schemaMediaType(outputTypeName)
            }
            content.set<JsonNode>("application/json", mediaType)
            response.set<JsonNode>("content", content)
        }
        responses.set<JsonNode>("200", response)
        return responses
    }

    /**
     * Looks up [fieldName] (by proto name or JSON name) in the message identified by [typeName]
     * and, if the field is message-typed, collects it.
     *
     * Returns `true` when the message and field were both found (regardless of whether collection
     * was needed — primitive/enum fields inline and require no schema entry).
     * Returns `false` when the message or field cannot be resolved; callers should treat this as
     * a misconfigured annotation and emit a compile error.
     */
    private fun collectBodyFieldType(
        typeName: String,
        fieldName: String,
    ): Boolean {
        val msg = ctx.messageIndex.find(typeName) ?: return false
        val field = msg.proto.fieldList
            .find { it.name == fieldName || it.jsonName == fieldName } ?: return false
        if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
            collector.collect(field.typeName)
        }
        return true
    }

    private fun responseBodyFieldSchema(
        outputTypeName: String,
        fieldName: String,
    ): ObjectNode? {
        val msg = ctx.messageIndex.find(outputTypeName) ?: return null
        val field = msg.proto.fieldList
            .find { it.name == fieldName || it.jsonName == fieldName } ?: return null
        return fieldTypeSchema(field)
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

private fun String.firstSentence(): String {
    for (i in indices) {
        when (this[i]) {
            '.', '!', '?' -> {
                val next = getOrNull(i + 1)
                if (next != null && next.isWhitespace()) return substring(0, i + 1).trim()
            }
        }
    }
    return trim()
}
