package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.putExtensionsInto
import com.engine.protoc.openapi.compile.json.toJson
import com.engine.protoc.openapi.model.Operation
import com.engine.protoc.openapi.model.ParameterOrReference
import com.engine.protoc.openapi.model.Schema
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.enums.EnumValueDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.service.MethodDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.markdown.MarkdownRenderer
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

private val markdownParser: Parser = Parser.builder().build()
private val markdownRenderer: MarkdownRenderer = MarkdownRenderer.builder().build()

/**
 * Builds the `paths` section of the OpenAPI document from gRPC service definitions and their
 * `google.api.http` + `engine.protoc.openapi.method` annotations.
 *
 * Paths with the same URL are merged so that multiple HTTP methods end up in a single path item.
 *
 * When [autoTagServices] is true, each operation automatically receives the name of its enclosing
 * service as an OAS tag (prepended before any explicitly-annotated tags).  The set of services
 * that contributed at least one operation is tracked so callers can retrieve tag metadata via
 * [buildServiceTags].
 */
internal class PathsBuilder(
    private val ctx: JsonContext,
    private val collector: MessageCollector,
    private val autoTagServices: Boolean = false,
    private val autoMapping: Boolean = false,
) {
    // Services (name → leading comment) that contributed ≥1 operation, in encounter order.
    // Only populated when autoTagServices is true.
    private val contributingServices = LinkedHashMap<String, String?>()

    /** Collects all paths from [files] into a merged `paths` ObjectNode. */
    fun build(files: List<FileDescriptorProtoWrapper>): ObjectNode =
        buildForServicePairs(
            files.flatMap { file ->
                file.services.map { file.`package`?.value to it }
            },
        )

    /** Collects paths for a single [service] into a `paths` ObjectNode. */
    fun buildForService(
        service: ServiceDescriptorProtoWrapper,
        filePackage: String? = null,
    ): ObjectNode = buildForServicePairs(listOf(filePackage to service))

    /**
     * Returns a JSON array of tag objects for every service that contributed at least one
     * operation during previous [build] / [buildForService] calls.  Each entry has a `name`
     * field and, when the service carries a leading proto comment, a `description` field.
     *
     * The returned array is empty when [autoTagServices] is false or when no operations were
     * emitted.  Callers should merge the result into the document-level `tags` array.
     */
    internal fun buildServiceTags(): ArrayNode {
        val arr = ctx.mapper.createArrayNode()
        for ((name, description) in contributingServices) {
            val tag = ctx.obj()
            tag.put("name", name)
            description?.let { tag.put("description", it) }
            arr.add(tag)
        }
        return arr
    }

    private fun buildForServicePairs(
        services: List<Pair<String?, ServiceDescriptorProtoWrapper>>,
    ): ObjectNode {
        val byPath = LinkedHashMap<String, ObjectNode>()

        for ((filePackage, service) in services) {
            // Resolve the auto-tag name once per service; null when feature is disabled.
            val autoTagName = if (autoTagServices) service.name?.value else null
            // Service-level tags applied to every operation in this service.
            val serviceTags: List<String> = service.options?.proto?.getExtension(Annotations.tags)
                ?: emptyList()
            var contributed = false

            for (method in service.methods) {
                val httpRule = method.options
                    ?.findExtension(AnnotationsProto.http)?.value

                val binding: HttpBinding = if (httpRule != null) {
                    httpRule.primaryBinding() ?: continue
                } else if (autoMapping) {
                    // Synthesise POST /<pkg.Service>/<Method> body:* for unannotated methods.
                    val pkg = filePackage?.let { "$it." } ?: ""
                    val svcName = service.name?.value ?: continue
                    val methodName = method.proto.name ?: continue
                    HttpBinding("post", "/$pkg$svcName/$methodName", "*")
                } else {
                    continue
                }

                val (effectivePath, operationNode) = buildOperation(method, binding, httpRule, autoTagName, serviceTags)
                val pathItem = byPath.getOrPut(effectivePath) { ctx.obj() }
                pathItem.set(binding.httpMethod, operationNode)
                contributed = true
            }

            // Register this service as a tag source if it produced at least one operation.
            if (autoTagServices && contributed) {
                val name = service.name?.value ?: continue
                val description = service.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
                contributingServices[name] = description
            }
        }

        val pathsNode = ctx.obj()
        for ((path, pathItem) in byPath) {
            pathsNode[path] = pathItem
        }
        return pathsNode
    }

    @Suppress("LongMethod")
    private fun buildOperation(
        method: MethodDescriptorProtoWrapper,
        binding: HttpBinding,
        // Null for auto-mapped methods that have no google.api.http annotation.
        httpRule: HttpRule?,
        // Non-null when autoTagServices is true; the service name becomes the first tag.
        autoTagName: String? = null,
        // Tags from the service-level `engine.protoc.openapi.tags` option, applied to all methods.
        serviceTags: List<String> = emptyList(),
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
        val responseBodyField = httpRule?.responseBody?.takeIf { it.isNotEmpty() }

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

        // Auto-tag (service name) is prepended; service-level tags follow; explicit annotation tags last.
        // Distinct preserves order while dropping any accidental duplicate.
        val allTags = buildList {
            autoTagName?.let { add(it) }
            addAll(serviceTags)
            annotation?.tagsList?.forEach { add(it) }
        }.distinct()
        if (allTags.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (t in allTags) arr.add(t)
            node.set("tags", arr)
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
            node.set("parameters", arr)
        }

        // ---- Request body -----------------------------------------------
        // For auto-mapped methods (httpRule == null) the body defaults to "*" per Envoy convention.
        val body = httpRule?.body ?: binding.body
        when {
            annotation?.hasRequestBody() == true -> {
                val requestBodyNode = annotation.requestBody.toJson(ctx)
                injectMissingRef(requestBodyNode.get("content") as? ObjectNode, inputTypeName)
                node.set("requestBody", requestBodyNode)
                if (annotation.requestBody.hasRequestBody()) {
                    annotation.requestBody.requestBody.contentMap.values.forEach { mt ->
                        if (mt.hasSchema()) collector.collectFromSchema(mt.schema)
                    }
                }
            }

            body == "*" -> node.set("requestBody", inferRequestBody(inputTypeName))

            body.isNotEmpty() -> node.set(
                "requestBody",
                inferRequestBodyField(inputTypeName, body),
            )
        }

        // ---- Responses --------------------------------------------------
        if (annotation?.hasResponses() == true) {
            node.set("responses", annotation.responses.toJson(ctx))
        } else {
            node.set("responses", inferResponses(outputTypeName, responseBodyField, method.proto.serverStreaming))
        }

        // ---- Security / deprecated --------------------------------------
        if (annotation?.securityList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (s in annotation.securityList) arr.add(s.toJson(ctx))
            node.set("security", arr)
        }
        if (annotation?.hasDeprecated() == true) node.put("deprecated", annotation.deprecated)
        if (annotation?.hasExternalDocs() == true) {
            node.set("externalDocs", annotation.externalDocs.toJson(ctx))
        }
        if (annotation?.callbacksMap?.isNotEmpty() == true) {
            val cbNode = ctx.obj()
            for ((k, v) in annotation.callbacksMap) cbNode.set(k, v.toJson(ctx))
            node.set("callbacks", cbNode)
        }
        if (annotation?.serversList?.isNotEmpty() == true) {
            val arr = ctx.mapper.createArrayNode()
            for (s in annotation.serversList) arr.add(s.toJson(ctx))
            node.set("servers", arr)
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
        annotatedParams: List<ParameterOrReference>,
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
            for ((k, v) in inferredNode.properties()) if (k != "schema") merged.set(k, v)
            for ((k, v) in annotatedNode.properties()) if (k != "schema") merged.set(k, v)
            if (mergedSchema.size() > 0) merged.set("schema", mergedSchema)
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
        val ref = "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(inputTypeName)}"
        for ((_, mediaTypeNode) in contentNode.properties()) {
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
        node.set("schema", paramFieldSchema(name, inputTypeName))
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
        content.set("application/json", schemaMediaType(inputTypeName))
        node.set("content", content)
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
        mediaType.set("schema", schema)
        content.set("application/json", mediaType)
        node.set("content", content)
        return node
    }

    internal fun schemaMediaType(typeName: String): ObjectNode {
        val schema = ctx.wellKnownScalarSchema(typeName)
            ?: ctx.obj().also {
                it.put("\$ref", "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}")
            }
        val mediaType = ctx.obj()
        mediaType.set("schema", schema)
        return mediaType
    }

    // ---- Response inference ---------------------------------------------

    private fun inferResponses(
        outputTypeName: String,
        responseBodyField: String? = null,
        isServerStreaming: Boolean = false,
    ): ObjectNode {
        val responses = ctx.obj()
        val response = ctx.obj()
        response.put("description", "OK")
        if (outputTypeName.isNotEmpty() && outputTypeName != ".google.protobuf.Empty") {
            val content = ctx.obj()
            // When a streaming option is active for a server-streaming method, use the
            // appropriate content type and a single-message schema (no array wrapper).
            // SSE takes precedence over ndjson when both are enabled.
            val useSse = isServerStreaming && ctx.streamSseStyleDelimited
            val useNdjson = isServerStreaming && ctx.streamNewlineDelimited && !useSse
            val mediaType = if (useSse || useNdjson) {
                schemaMediaType(outputTypeName)
            } else if (responseBodyField != null) {
                responseBodyFieldSchema(outputTypeName, responseBodyField)
                    ?.let { schema -> ctx.obj().also { it.set("schema", schema) } }
                    ?: schemaMediaType(outputTypeName)
            } else {
                schemaMediaType(outputTypeName)
            }
            val contentType = when {
                useSse -> "text/event-stream"
                useNdjson -> "application/x-ndjson"
                else -> "application/json"
            }
            content.set(contentType, mediaType)
            response.set("content", content)
        }
        responses.set("200", response)
        if (ctx.convertGrpcStatus) {
            val errorResponse = ctx.obj()
            errorResponse.put("description", "Error")
            val content = ctx.obj()
            val mediaType = ctx.obj()
            mediaType.set("schema", ctx.obj().also { it.put("\$ref", "#/components/schemas/google.rpc.Status") })
            content.set("application/json", mediaType)
            errorResponse.set("content", content)
            responses.set("default", errorResponse)
        }
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
        when (field.type) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE ->
                collector.collect(field.typeName)

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM ->
                collector.collectEnum(field.typeName)

            else -> Unit
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
                it.set("items", base)
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

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> {
                val typeName = field.typeName
                val enumWrapper = ctx.enumIndex.find(typeName)
                val annotationInline = enumWrapper?.options?.findExtension(Annotations.inline)?.value
                val shouldInline = annotationInline ?: ctx.inlineEnums
                if (shouldInline) {
                    buildEnumSchema(typeName, enumWrapper)
                } else {
                    collector.collectEnum(typeName)
                    node.also {
                        it.put(
                            "\$ref",
                            "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}",
                        )
                    }
                }
            }

            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> {
                val typeName = field.typeName
                ctx.wellKnownScalarSchema(typeName) ?: node.also {
                    it.put(
                        "\$ref",
                        "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}",
                    )
                }
            }

            else -> node
        }
    }

    private fun isMapField(field: DescriptorProtos.FieldDescriptorProto): Boolean {
        if (field.type != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) return false
        return ctx.messageIndex.find(field.typeName)?.proto?.options?.mapEntry == true
    }

    /**
     * Builds a fully-populated enum schema `{ "type": "string", "enum": [...] }` for [typeName].
     * Used for both inline field schemas and `components/schemas` entries.  Suppresses values per
     * [JsonContext.suppressDefaultEnumValues] and per-value annotations, then deep-merges any
     * `engine.protoc.openapi.enum` annotation on top.
     *
     * [includeTitle] should be `true` only when building a component schema entry; inline field
     * schemas must not carry a title.
     */
    internal fun buildEnumSchema(
        typeName: String,
        enumWrapper: EnumDescriptorProtoWrapper?,
        includeTitle: Boolean = false,
    ): ObjectNode {
        val node = ctx.obj()
        val isNumeric = ctx.enumValueFormat == ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE
        val isLowerCase = ctx.enumValueFormat == ProtocGenOpenAPI.Options.EnumValueFormat.LOWER_CASE
        node.put("type", if (isNumeric) "integer" else "string")
        if (includeTitle) ctx.schemaKeyResolver.titleFor(typeName)?.let { node.put("title", it) }

        if (enumWrapper != null) {
            val visibleValues = enumWrapper.values.filterNot {
                isValueSuppressed(it, ctx.suppressDefaultEnumValues)
            }

            buildEnumDescription(
                enumComment = enumWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null },
                visibleValues = visibleValues,
            )?.let { node.put("description", it) }

            val valuesArr = ctx.mapper.createArrayNode()
            for (valueWrapper in visibleValues) {
                if (isNumeric) {
                    valuesArr.add(valueWrapper.proto.number)
                } else if (isLowerCase) {
                    valuesArr.add(valueWrapper.proto.name.lowercase())
                } else {
                    valuesArr.add(valueWrapper.proto.name)
                }
            }
            if (valuesArr.size() > 0) node.set("enum", valuesArr)

            val annotation = enumWrapper.options?.findExtension(Annotations.enum_)?.value
            if (annotation != null && annotation.schemaValueCase == Schema.SchemaValueCase.OBJECT) {
                with(ctx) { node.deepMerge(annotation.`object`.toJson(ctx)) }
            }
        }

        return node
    }

    private fun buildEnumDescription(
        enumComment: String?,
        visibleValues: List<EnumValueDescriptorProtoWrapper>,
    ): String? {
        val isNumeric = ctx.enumValueFormat == ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE
        val isLowerCase = ctx.enumValueFormat == ProtocGenOpenAPI.Options.EnumValueFormat.LOWER_CASE

        // For NUMERIC_VALUE and LOWER_CASE every visible value gets a bullet regardless of whether
        // it carries a comment (the bullet documents the exact accepted value even without prose).
        // For other formats only values that carry a leading comment produce a bullet.
        val labeledValues: List<Pair<String, String?>> = if (isNumeric || isLowerCase) {
            visibleValues.map { valueWrapper ->
                Pair(
                    formatEnumLabel(valueWrapper.proto.name, valueWrapper.proto.number, ctx.enumValueFormat),
                    valueWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null },
                )
            }
        } else {
            visibleValues.mapNotNull { valueWrapper ->
                val comment = valueWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
                    ?: return@mapNotNull null
                Pair(
                    formatEnumLabel(valueWrapper.proto.name, valueWrapper.proto.number, ctx.enumValueFormat),
                    comment,
                )
            }
        }
        if (enumComment == null && labeledValues.isEmpty()) return null

        val documentNode = if (enumComment != null) {
            markdownParser.parse(enumComment) as? Document ?: Document()
        } else {
            Document()
        }

        if (labeledValues.isNotEmpty()) {
            val list = BulletList().apply {
                for ((name, comment) in labeledValues) {
                    val commentDoc = comment?.let { markdownParser.parse(it) as? Document }
                    appendChild(
                        ListItem().apply {
                            val firstBlock = commentDoc?.firstChild
                            appendChild(
                                Paragraph().apply {
                                    appendChild(Code(name))
                                    if (comment != null) {
                                        appendChild(Text(" — "))
                                        if (firstBlock is Paragraph) {
                                            spliceInlineNodes(firstBlock, this)
                                        } else {
                                            appendChild(Text(comment))
                                        }
                                    }
                                },
                            )
                            var block = firstBlock?.next
                            while (block != null) {
                                val next = block.next
                                if (block is Paragraph) {
                                    appendChild(Paragraph().also { spliceInlineNodes(block, it) })
                                }
                                block = next
                            }
                        },
                    )
                }
            }
            documentNode.appendChild(list)
        }

        return markdownRenderer
            .render(documentNode)
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()
            .ifEmpty { null }
    }

    /**
     * Moves all inline children of [src] into [dest], replacing each [SoftLineBreak] with a
     * space so the paragraph renders as a single line.
     */
    private fun spliceInlineNodes(
        src: Paragraph,
        dest: Paragraph,
    ) {
        var node = src.firstChild
        while (node != null) {
            val next = node.next
            node.unlink()
            dest.appendChild(if (node is SoftLineBreak) Text(" ") else node)
            node = next
        }
    }

    private fun isValueSuppressed(
        valueWrapper: EnumValueDescriptorProtoWrapper,
        suppressDefaults: Boolean,
    ): Boolean {
        val annotation = valueWrapper.options?.findExtension(Annotations.suppress)?.value
        return annotation ?: (suppressDefaults && valueWrapper.proto.number == 0)
    }

    private fun formatEnumLabel(
        protoName: String,
        protoNumber: Int,
        format: ProtocGenOpenAPI.Options.EnumValueFormat,
    ): String =
        when (format) {
            ProtocGenOpenAPI.Options.EnumValueFormat.CANONICAL -> protoName
            ProtocGenOpenAPI.Options.EnumValueFormat.NUMERIC_VALUE -> "$protoNumber ($protoName)"
            ProtocGenOpenAPI.Options.EnumValueFormat.LOWER_CASE -> protoName.lowercase()
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
