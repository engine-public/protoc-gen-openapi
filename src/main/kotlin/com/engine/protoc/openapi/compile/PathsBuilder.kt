package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.putExtensionsInto
import com.engine.protoc.openapi.compile.json.toJson
import com.engine.protoc.openapi.model.ErrorResponse
import com.engine.protoc.openapi.model.Operation
import com.engine.protoc.openapi.model.ParameterOrReference
import com.engine.protoc.openapi.model.Schema
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.enums.EnumValueDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper
import com.engine.protoc.util.message.FieldDescriptorProtoWrapper
import com.engine.protoc.util.service.MethodDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.markdown.MarkdownRenderer
import org.slf4j.LoggerFactory
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

private val markdownParser: Parser = Parser.builder().build()
private val markdownRenderer: MarkdownRenderer = MarkdownRenderer.builder().build()
private val log = LoggerFactory.getLogger(PathsBuilder::class.java)

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

    // Stack of "currently expanding" type names used by [expandInline] for cycle detection.
    // Each frame is the snapshot of expanded types up to the current depth; pushed on entry to
    // expandInline and popped on exit.  Inside any [buildMessageSchema] / [baseFieldSchema] call
    // chain triggered from expandInline, the top frame controls $ref-vs-inline decisions for
    // nested message-typed fields.
    private val inlineExpandingStack = ArrayDeque<Set<String>>()

    // Set during the seed pass when any operation declares `error_responses`.  Consulted by
    // [SchemaBuilder] so `google.rpc.Status` lands in `components/schemas` even when the
    // `convertGrpcStatus` compiler option is off.
    internal var requiresGrpcStatus: Boolean = false
        private set

    /**
     * Collects all paths from [files] into a merged `paths` ObjectNode.
     *
     * When [serviceFilter] is non-null, only services for which it returns `true` contribute
     * paths.  The first argument to [serviceFilter] is the file's proto package (may be null
     * when the file declares no package), and the second is the service descriptor.
     */
    fun build(
        files: List<FileDescriptorProtoWrapper>,
        serviceFilter: ((pkg: String?, svc: ServiceDescriptorProtoWrapper) -> Boolean)? = null,
    ): ObjectNode =
        buildForServicePairs(
            files.flatMap { file ->
                val pkg = file.`package`?.value
                file.services
                    .filter { svc -> serviceFilter == null || serviceFilter(pkg, svc) }
                    .map { pkg to it }
            },
        )

    /** Collects paths for a single [service] into a `paths` ObjectNode. */
    fun buildForService(
        service: ServiceDescriptorProtoWrapper,
        filePackage: String? = null,
    ): ObjectNode = buildForServicePairs(listOf(filePackage to service))

    /**
     * Seeding pass: walks every RPC across [files] and populates [collector] with the message
     * types that must appear in `components/schemas`.  RPCs annotated with
     * `inline_request_schema = true` or `inline_response_schema = true` do not contribute their
     * request/response type to the collected set (those schemas will be inlined at the use site
     * during [build]).
     *
     * Must be called before [build] / [buildForService] when method-level inline annotations may
     * be present, so that emission-time `$ref`-vs-inline decisions see the final collected set.
     * Safe to call multiple times — [MessageCollector.collect] is idempotent.
     */
    fun seed(
        files: List<FileDescriptorProtoWrapper>,
        serviceFilter: ((pkg: String?, svc: ServiceDescriptorProtoWrapper) -> Boolean)? = null,
    ) {
        for (file in files) {
            val pkg = file.`package`?.value
            for (service in file.services) {
                if (serviceFilter != null && !serviceFilter(pkg, service)) continue
                for (method in service.methods) {
                    seedOperation(method, pkg, service.name?.value)
                }
            }
        }
    }

    /** Single-service seed counterpart of [buildForService]. */
    fun seedForService(
        service: ServiceDescriptorProtoWrapper,
        filePackage: String? = null,
    ) {
        for (method in service.methods) {
            seedOperation(method, filePackage, service.name?.value)
        }
    }

    private fun seedOperation(
        method: MethodDescriptorProtoWrapper,
        filePackage: String?,
        serviceName: String?,
    ) {
        val httpRule = method.options?.findExtension(AnnotationsProto.http)?.value
        val methodWrapper = method.options?.findExtension(Annotations.method)?.value
        val annotation = methodWrapper?.takeIf { it.hasOperation() }?.operation
        val inlineRequest = methodWrapper?.inlineRequest == true
        val inlineResponse = methodWrapper?.inlineResponse == true

        val binding: HttpBinding = if (httpRule != null) {
            httpRule.primaryBinding() ?: return
        } else if (autoMapping) {
            val pkg = filePackage?.let { "$it." } ?: ""
            val svc = serviceName ?: return
            val name = method.proto.name ?: return
            HttpBinding("post", "/$pkg$svc/$name", "*")
        } else {
            return
        }

        // Response — skip when inlined.
        if (!inlineResponse) {
            val responseBodyField = httpRule?.responseBody?.takeIf { it.isNotEmpty() }
            if (responseBodyField != null) {
                // collectBodyFieldType is idempotent and tolerant of missing fields here; the
                // build pass will surface a hard error for misconfigured response_body.
                collectBodyFieldType(method.proto.outputType, responseBodyField)
            } else {
                collector.collect(method.proto.outputType)
            }
        }

        // Request body — skip when inlined.
        val hasExplicitRequestBody = annotation?.hasRequestBody() == true
        if (!inlineRequest) {
            when {
                binding.body == "*" && !hasExplicitRequestBody ->
                    collector.collect(method.proto.inputType)

                binding.body.isNotEmpty() && binding.body != "*" && !hasExplicitRequestBody ->
                    collectBodyFieldType(method.proto.inputType, binding.body)
            }
        }

        // Explicit requestBody annotation seeds proto_message_ref'd component schemas — these
        // are independent of the inline flag (the user explicitly requested a $ref to them).
        // Only the inlined RequestBody branch carries a contentMap; the Reference branch points
        // at a `components/requestBodies` entry that doesn't need additional collection here.
        if (hasExplicitRequestBody && annotation.requestBody.hasRequestBody()) {
            annotation.requestBody.requestBody.contentMap.values.forEach { mt ->
                if (mt.hasSchema()) collector.collectFromSchema(mt.schema)
            }
        }

        // error_responses shortcut: collect each referenced error type, and mark that
        // google.rpc.Status must be emitted into components/schemas.
        methodWrapper?.errorResponsesList?.forEach { er ->
            errorResponseTypeName(er)?.let { collector.collect(it) }
            requiresGrpcStatus = true
        }
    }

    /**
     * Returns the fully-qualified proto type name (leading dot, e.g. `.pkg.MyError`) referenced
     * by an [ErrorResponse]'s `error_type`, or `null` when no type URL is set.
     */
    private fun errorResponseTypeName(er: ErrorResponse): String? {
        if (!er.hasErrorType()) return null
        val typeUrl = er.errorType.typeUrl
        if (typeUrl.isEmpty()) return null
        return ".${typeUrl.substringAfterLast('/')}"
    }

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

    /**
     * Returns [services] reordered by `(engine.protoc.openapi.index_order)` on the service,
     * falling back to encounter ordinal for any service without the annotation.  Stable: ties
     * (including the implicit `index = encounter ordinal` baseline for un-annotated services)
     * preserve source order.
     *
     * Logs a WARN for every sort-key collision (two explicit annotations landing on the same
     * value, or an explicit value colliding with an un-annotated service's encounter ordinal)
     * naming the conflicting services and the index they share.  The stable-sort fallback then
     * resolves the order automatically.
     */
    private fun orderByIndex(
        services: List<Pair<String?, ServiceDescriptorProtoWrapper>>,
    ): List<Pair<String?, ServiceDescriptorProtoWrapper>> {
        data class Keyed(
            val pair: Pair<String?, ServiceDescriptorProtoWrapper>,
            val sortKey: Int,
            val explicit: Boolean,
        )

        val keyed = services.mapIndexed { encounterOrdinal, pair ->
            val explicit = pair.second.options
                ?.findExtension(Annotations.service)?.value
                ?.takeIf { it.hasIndexOrder() }
                ?.indexOrder
            Keyed(pair, explicit ?: encounterOrdinal, explicit != null)
        }

        keyed.groupBy { it.sortKey }
            .filterValues { it.size > 1 }
            .forEach { (index, group) ->
                val parties = group.joinToString(", ") {
                    val tag = if (it.explicit) "explicit" else "implicit"
                    "${fqn(it.pair.first, it.pair.second)} ($tag)"
                }
                log.warn(
                    "index_order conflict at {}: {} — falling back to source order",
                    index,
                    parties,
                )
            }

        return keyed.sortedBy { it.sortKey }.map { it.pair }
    }

    private fun fqn(
        filePackage: String?,
        service: ServiceDescriptorProtoWrapper,
    ): String {
        val pkg = filePackage.orEmpty()
        val name = service.name?.value ?: "<unknown>"
        return if (pkg.isEmpty()) name else "$pkg.$name"
    }

    private fun buildForServicePairs(
        services: List<Pair<String?, ServiceDescriptorProtoWrapper>>,
    ): ObjectNode {
        val byPath = LinkedHashMap<String, ObjectNode>()
        // Tracks (path, httpMethod) → "Service/Method" for conflict reporting.
        val occupiedSlots = mutableMapOf<Pair<String, String>, String>()

        // Per `(engine.protoc.openapi.index_order)` on the service, reorder before emission.
        // Sort key: explicit annotation value when present, otherwise the encounter ordinal
        // (the first service is 0, the second 1, ...).  Kotlin's `sortedBy` is stable so
        // services that tie on sort key keep their original source order.
        val ordered = orderByIndex(services)

        for ((filePackage, service) in ordered) {
            // Resolve the auto-tag name once per service; null when feature is disabled.
            val autoTagName = if (autoTagServices) service.name?.value else null
            // Service-level tags applied to every operation in this service.
            val serviceTags: List<String> = service.options
                ?.findExtension(Annotations.service)?.value
                ?.tagsList
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

                val slotKey = effectivePath to binding.httpMethod
                val existingOwner = occupiedSlots[slotKey]
                if (existingOwner != null) {
                    val currentOwner = "${service.name?.value ?: "<unknown>"}/${method.proto.name ?: "<unknown>"}"
                    val currentType = if (httpRule == null) "auto-mapped" else "annotated"
                    throw IllegalArgumentException(
                        "Route ${binding.httpMethod.uppercase()} $effectivePath is claimed by both " +
                            "$existingOwner and $currentOwner ($currentType). " +
                            "Resolve the conflict by adjusting http annotations or disabling autoMapping.",
                    )
                }
                occupiedSlots[slotKey] = "${service.name?.value ?: "<unknown>"}/${method.proto.name ?: "<unknown>"}"

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
        val methodWrapper = method.options?.findExtension(Annotations.method)?.value
        val defaultInstance = Operation.getDefaultInstance()
        val annotation: Operation? = methodWrapper
            ?.takeIf { it.hasOperation() }?.operation
            ?.takeIf { it != defaultInstance }

        val inputTypeName = method.proto.inputType
        val outputTypeName = method.proto.outputType

        val inlineRequest = methodWrapper?.inlineRequest == true
        val inlineResponse = methodWrapper?.inlineResponse == true

        // When response_body names a field the HTTP response carries that field's value
        // directly rather than the full output message.  Collection has already happened in
        // the [seed] pass; here we only validate that the named field actually exists so the
        // operator sees a clear error rather than a silently invalid spec.
        val responseBodyField = httpRule?.responseBody?.takeIf { it.isNotEmpty() }
        if (responseBodyField != null && !fieldExists(outputTypeName, responseBodyField)) {
            val outputSimpleName = ctx.messageIndex.simpleNameOf(outputTypeName)
            throw IllegalArgumentException(
                "response_body field '$responseBodyField' not found in $outputSimpleName",
            )
        }

        // Same validation for `body: "<field>"` on a request binding.
        val hasExplicitRequestBody = annotation?.hasRequestBody() == true
        if (binding.body.isNotEmpty() && binding.body != "*" && !hasExplicitRequestBody) {
            if (!fieldExists(inputTypeName, binding.body)) {
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

        // Per the canonical HttpRule spec (and Envoy's gRPC-JSON transcoder), the `body` field has
        // three modes — identical across all five verbs:
        //   body == "*"     →  whole request message is the body; no query parameters
        //   body unset/""   →  no body; every non-path field becomes a query parameter
        //   body == "<f>"   →  field `f` is the body; every other non-path field is a query param
        // The Envoy default for auto-mapped methods (no `google.api.http` annotation) is "*", so
        // bare auto-mapped methods carry no query parameters.  Explicit `(engine.protoc.openapi
        // .method).request_body` annotations short-circuit body inference — when present, the
        // operator is in full control and we skip query-parameter auto-derivation as well.
        val body = httpRule?.body ?: binding.body
        val hasExplicitRequestBodyAnnotation = annotation?.hasRequestBody() == true
        // Path-bound field names come from the *original* URL template — annotation-driven path
        // param renaming changes the URL placeholder but the underlying field that binds the path
        // is still named after the proto field, not the rewritten placeholder.
        val pathVarNames = pathParamRegex.findAll(binding.path).map { it.groupValues[1] }.toSet()
        val methodLabel = "${binding.httpMethod.uppercase()} ${binding.path}"

        // Collect every name declared manually via `(engine.protoc.openapi.parameters)`, regardless
        // of `in`.  A top-level request field is considered "claimed by the operator" when its
        // proto name or JSON name matches one of these — whether the operator chose to expose it
        // as a query parameter, a header, a cookie, or anything else.  Auto-derivation honours
        // that choice by leaving the field alone (and logging a WARN so the overlap is visible).
        val manualClaimedNames: Set<String> = annotatedParams
            .filter { it.hasParameter() }
            .mapNotNull { it.parameter.name.takeIf { n -> n.isNotEmpty() } }
            .toSet()

        val autoQueryParams: List<ObjectNode> = when {
            hasExplicitRequestBodyAnnotation -> emptyList()

            body == "*" -> emptyList()

            body.isEmpty() ->
                inferQueryParameters(inputTypeName, pathVarNames, null, manualClaimedNames, methodLabel)

            else ->
                inferQueryParameters(inputTypeName, pathVarNames, body, manualClaimedNames, methodLabel)
        }

        val allParams = if (annotatedPathParams.isEmpty()) {
            // No annotated path params: inferred path params, then annotated non-path params in
            // annotation order, then any remaining auto-derived query params at the end.
            val annotatedNonPathParams = annotatedParams
                .filter { !it.hasParameter() || it.parameter.`in` != "path" }
            pathParamNodes +
                annotatedNonPathParams.map { it.toJson(ctx) } +
                autoQueryParams
        } else {
            // Annotated path params exist: preserve full annotation order, then auto-derived
            // query params after.
            var pathParamIndex = 0
            annotatedParams.map { param ->
                if (param.hasParameter() && param.parameter.`in` == "path") {
                    pathParamNodes[pathParamIndex++]
                } else {
                    param.toJson(ctx)
                }
            } + autoQueryParams
        }
        if (allParams.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (p in allParams) arr.add(p)
            node.set("parameters", arr)
        }

        // ---- Request body -----------------------------------------------
        // Explicit requestBody annotations always emit exactly what the user wrote — the
        // `inline_request_schema` flag affects only the inferred-from-proto path.
        when {
            hasExplicitRequestBodyAnnotation -> {
                val requestBodyNode = annotation.requestBody.toJson(ctx)
                injectMissingRef(requestBodyNode.get("content") as? ObjectNode, inputTypeName)
                node.set("requestBody", requestBodyNode)
            }

            body == "*" -> node.set("requestBody", inferRequestBody(inputTypeName, inlineRequest))

            body.isNotEmpty() -> node.set(
                "requestBody",
                inferRequestBodyField(inputTypeName, body, inlineRequest),
            )
        }

        // ---- Responses --------------------------------------------------
        val responsesNode: ObjectNode = if (annotation?.hasResponses() == true) {
            annotation.responses.toJson(ctx)
        } else {
            inferResponses(
                outputTypeName,
                responseBodyField,
                method.proto.serverStreaming,
                inlineResponse,
            )
        }

        // error_responses shortcut: apply each entry on top of `responsesNode` with
        // last-wins semantics, warning on collisions (within error_responses, or against
        // an entry already produced by the inferred / explicit `responses` path).
        if (methodWrapper?.errorResponsesList?.isNotEmpty() == true) {
            val operationFqn = "${binding.httpMethod.uppercase()} ${binding.path}"
            val seen = mutableMapOf<String, ErrorResponse>()
            for (er in methodWrapper.errorResponsesList) {
                val status = er.status
                val incoming = errorResponseTypeName(er) ?: "<unset>"
                val prevInErrors = seen[status]
                if (prevInErrors != null) {
                    val prevName = errorResponseTypeName(prevInErrors) ?: "<unset>"
                    log.warn(
                        "$operationFqn: error_responses declares status '{}' twice (previous error_type={}, new error_type={}) — last declaration wins",
                        status,
                        prevName,
                        incoming,
                    )
                } else if (responsesNode.has(status)) {
                    log.warn(
                        "$operationFqn: status '{}' already declared by `responses`; the `error_responses` entry (error_type={}) overwrites — last declaration wins",
                        status,
                        incoming,
                    )
                }
                seen[status] = er
                responsesNode.set(status, buildErrorResponseEntry(er))
            }
        }

        node.set("responses", responsesNode)

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

            val annotatedName = annotatedNode.path("name").stringValue("")
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
        if (ctx.wellKnownTypeSchema(inputTypeName) != null) return
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

    // ---- Query parameter inference ---------------------------------------

    /**
     * Infers query parameters from non-path, non-body fields of the request message, mirroring the
     * `google.api.HttpRule` body semantics enforced by Envoy's gRPC-JSON transcoder.  Called for
     * the two body modes that imply query parameters:
     *
     *  - `body` unset (`""`)  — every non-path field of the request message
     *  - `body: "<field>"`    — every non-path field of the request message except the named one
     *
     * Walks nested messages recursively, flattening to leaf scalars with dotted parameter names
     * (`address.city`) so the emitted spec matches Envoy's permissive runtime binding.  Repeated
     * scalar / enum fields produce `style=form, explode=true` array parameters.  Repeated message
     * and map fields are skipped with a WARN — OpenAPI 3.1 has no faithful query-parameter
     * representation for them and callers should declare a manual override or move them into the
     * body.
     *
     * Parameter names follow [JsonContext.preserveProtoFieldNames]: the JSON name (camelCase by
     * default, or the explicit `json_name` annotation) when false, the raw proto name when true.
     * This mirrors how the same flag drives response-body field naming and is one of the two
     * forms Envoy's `TypeInfo::FindField` accepts at runtime.
     */
    private fun inferQueryParameters(
        inputTypeName: String,
        pathVarNames: Set<String>,
        excludeFieldName: String?,
        manualClaimedNames: Set<String>,
        methodLabel: String,
    ): List<ObjectNode> {
        val msg = ctx.messageIndex.find(inputTypeName) ?: return emptyList()
        val result = mutableListOf<ObjectNode>()
        for (fieldWrapper in msg.fields) {
            val field = fieldWrapper.proto
            // Skip the named-body field (it lives in the request body, not the query string).
            if (excludeFieldName != null &&
                (field.name == excludeFieldName || field.jsonName == excludeFieldName)
            ) {
                continue
            }
            // Skip path variables — match both proto and JSON names so that the URL template
            // `{your_name}` correctly elides whether the field's JSON name is `yourName` or
            // `your_name`.
            if (field.name in pathVarNames || field.jsonName in pathVarNames) continue
            // Skip fields the operator has already declared manually via
            // `(engine.protoc.openapi.parameters)` — manual declarations win regardless of the
            // chosen `in` position (query, header, cookie, …) and we leave the field's nested
            // children alone too, since manual annotations cover the field semantically.
            if (field.name in manualClaimedNames || field.jsonName in manualClaimedNames) {
                log.warn(
                    "{}: request field '{}' is auto-derivable as a query parameter but is also declared in (engine.protoc.openapi.parameters); using the manual declaration.",
                    methodLabel,
                    field.name,
                )
                continue
            }
            appendQueryParam(fieldWrapper, prefix = "", out = result, methodLabel = methodLabel)
        }
        return result
    }

    /**
     * Emits one parameter into [out] for a leaf-scalar or repeated-scalar field, or recurses into
     * a nested message field with the given dotted [prefix].  Repeated message and map fields are
     * skipped with a WARN.
     *
     * The emitted parameter's `description` comes from the field's leading proto comment so that
     * the same documentation that drives the response-schema property's `description` also shows
     * up on the auto-derived query parameter.  For nested-message recursion the description of
     * the leaf sub-field is used — parent prefixes do not contribute prose, since the dotted
     * name (`address.city`) already signals the hierarchy.
     */
    private fun appendQueryParam(
        fieldWrapper: FieldDescriptorProtoWrapper,
        prefix: String,
        out: MutableList<ObjectNode>,
        methodLabel: String,
    ) {
        val field = fieldWrapper.proto
        val isRepeated =
            field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
        val isMessage = field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE
        val name = prefix + queryParamFieldName(field)
        val description = fieldWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }

        if (isMessage && isMapField(field)) {
            log.warn(
                "{}: map field '{}' cannot be expressed as a query parameter; skipping. Declare it manually via (engine.protoc.openapi.parameters) or move it into the request body.",
                methodLabel,
                name,
            )
            return
        }
        if (isRepeated && isMessage) {
            // Well-known scalar wrappers are messages structurally but encode as scalars in JSON,
            // so `repeated google.protobuf.Int64Value` is still a flat array parameter.
            if (ctx.wellKnownScalarSchema(field.typeName) == null) {
                log.warn(
                    "{}: repeated message field '{}' cannot be expressed as a query parameter; skipping. Declare it manually via (engine.protoc.openapi.parameters) or move it into the request body.",
                    methodLabel,
                    name,
                )
                return
            }
        }
        if (isMessage && !isRepeated) {
            val wktScalar = ctx.wellKnownScalarSchema(field.typeName)
            if (wktScalar != null) {
                out += buildQueryParam(name, wktScalar, repeated = false, description = description)
                return
            }
            // Skip structural well-known types (Struct, Any, Value, ...) — there is no useful
            // query-string representation of arbitrary JSON.
            if (ctx.wellKnownTypeSchema(field.typeName) != null) {
                log.warn(
                    "{}: structural well-known type field '{}' (type {}) cannot be expressed as a query parameter; skipping.",
                    methodLabel,
                    name,
                    field.typeName,
                )
                return
            }
            val nested = ctx.messageIndex.find(field.typeName) ?: return
            for (sub in nested.fields) {
                appendQueryParam(sub, "$name.", out, methodLabel)
            }
            return
        }

        out += buildQueryParam(name, fieldTypeSchema(field), repeated = isRepeated, description = description)
    }

    private fun buildQueryParam(
        name: String,
        schema: ObjectNode,
        repeated: Boolean,
        description: String? = null,
    ): ObjectNode {
        val node = ctx.obj()
        node.put("name", name)
        node.put("in", "query")
        if (description != null) node.put("description", description)
        node.put("required", false)
        if (repeated) {
            node.put("style", "form")
            node.put("explode", true)
        }
        node.set("schema", schema)
        return node
    }

    private fun queryParamFieldName(field: DescriptorProtos.FieldDescriptorProto): String =
        if (ctx.preserveProtoFieldNames) {
            field.name
        } else {
            field.jsonName.ifEmpty { field.name }
        }

    // ---- Request body inference ------------------------------------------

    private fun inferRequestBody(
        inputTypeName: String,
        inline: Boolean,
    ): ObjectNode {
        val node = ctx.obj()
        node.put("required", true)
        val content = ctx.obj()
        content.set("application/json", schemaMediaType(inputTypeName, inline))
        node.set("content", content)
        return node
    }

    private fun inferRequestBodyField(
        inputTypeName: String,
        fieldName: String,
        inline: Boolean,
    ): ObjectNode {
        val msg = ctx.messageIndex.find(inputTypeName)
        val field = msg?.proto?.fieldList?.find { it.name == fieldName || it.jsonName == fieldName }
        val schema = when {
            field == null -> ctx.obj()
            inline -> fieldTypeSchema(field, inlineMode = true)
            else -> fieldTypeSchema(field)
        }

        val node = ctx.obj()
        node.put("required", true)
        val content = ctx.obj()
        val mediaType = ctx.obj()
        mediaType.set("schema", schema)
        content.set("application/json", mediaType)
        node.set("content", content)
        return node
    }

    internal fun schemaMediaType(
        typeName: String,
        inline: Boolean = false,
    ): ObjectNode {
        val schema = ctx.wellKnownTypeSchema(typeName)
            ?: if (inline) {
                expandInline(typeName)
            } else {
                ctx.obj().also {
                    it.put("\$ref", "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}")
                }
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
        inline: Boolean = false,
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
                schemaMediaType(outputTypeName, inline)
            } else if (responseBodyField != null) {
                responseBodyFieldSchema(outputTypeName, responseBodyField, inline)
                    ?.let { schema -> ctx.obj().also { it.set("schema", schema) } }
                    ?: schemaMediaType(outputTypeName, inline)
            } else {
                schemaMediaType(outputTypeName, inline)
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
     * Builds the Response object for a single [`ErrorResponse`] entry: a `google.rpc.Status`
     * schema (via `allOf`) whose `details.items` $refs the named error type, plus the
     * `x-grpc-code` extension and a seeded `examples` block when `grpc_code` is present.
     */
    private fun buildErrorResponseEntry(er: ErrorResponse): ObjectNode {
        val response = ctx.obj()
        response.put("description", resolveErrorDescription(er))

        if (er.hasGrpcCode()) {
            response.put("x-grpc-code", er.grpcCode.name)
        }

        val mediaType = ctx.obj()
        if (er.hasGrpcCode()) {
            val examples = ctx.obj()
            val grpcError = ctx.obj()
            grpcError.put("summary", er.grpcCode.name)
            val value = ctx.obj()
            value.put("code", er.grpcCode.number)
            value.put("message", "")
            value.set("details", ctx.mapper.createArrayNode())
            grpcError.set("value", value)
            examples.set("grpc-error", grpcError)
            mediaType.set("examples", examples)
        }
        mediaType.set("schema", buildErrorAllOfSchema(er))

        val content = ctx.obj()
        content.set("application/json", mediaType)
        response.set("content", content)
        return response
    }

    /**
     * Resolves an [`ErrorResponse`]'s description with the layered fallback documented on the
     * annotation: explicit `description`, then the error type's leading proto comment, then
     * the literal "Error".
     */
    private fun resolveErrorDescription(er: ErrorResponse): String {
        if (er.hasDescription() && er.description.isNotEmpty()) return er.description
        val typeName = errorResponseTypeName(er)
        if (typeName != null) {
            val comment = ctx.messageIndex.find(typeName)
                ?.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
            if (comment != null) return comment
        }
        return "Error"
    }

    /**
     * Produces an `allOf: [ $ref:google.rpc.Status, { properties: { details: { items: $ref:T }}}]`
     * schema that types the Status's `details` array items as the [`ErrorResponse`]'s error type.
     */
    private fun buildErrorAllOfSchema(er: ErrorResponse): ObjectNode {
        val schema = ctx.obj()
        val allOf = ctx.mapper.createArrayNode()

        val statusRef = ctx.obj()
        statusRef.put("\$ref", "#/components/schemas/google.rpc.Status")
        allOf.add(statusRef)

        val typeName = errorResponseTypeName(er)
        if (typeName != null) {
            val override = ctx.obj()
            override.put("type", "object")
            val props = ctx.obj()
            val details = ctx.obj()
            details.put("type", "array")
            details.set(
                "items",
                ctx.obj().also {
                    it.put("\$ref", "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}")
                },
            )
            props.set("details", details)
            override.set("properties", props)
            allOf.add(override)
        }

        schema.set("allOf", allOf)
        return schema
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
        inline: Boolean = false,
    ): ObjectNode? {
        val msg = ctx.messageIndex.find(outputTypeName) ?: return null
        val field = msg.proto.fieldList
            .find { it.name == fieldName || it.jsonName == fieldName } ?: return null
        return fieldTypeSchema(field, inlineMode = inline)
    }

    /**
     * Returns true if [typeName] resolves to a message that declares [fieldName] (by proto name
     * or JSON name).  Side-effect-free counterpart to [collectBodyFieldType], used by build-pass
     * validation; collection is the seed pass's responsibility.
     */
    private fun fieldExists(
        typeName: String,
        fieldName: String,
    ): Boolean {
        val msg = ctx.messageIndex.find(typeName) ?: return false
        return msg.proto.fieldList.any { it.name == fieldName || it.jsonName == fieldName }
    }

    // ---- Inline expansion -----------------------------------------------

    /**
     * Returns the inline-expanded schema body for [typeName] for use at an inline boundary
     * (method-level `inline_request_schema` / `inline_response_schema`, or field-level
     * `inline_schema`).  Recursively expands nested message-typed fields whose target is not
     * present in [collector]; targets that are in the collected set continue to emit `$ref`s
     * pointing at the component schema.
     *
     * Cycles are broken by force-adding the cyclic type to [collector] (so a component schema
     * is emitted for it) and substituting a `$ref` at the cycle point.
     */
    internal fun expandInline(typeName: String): ObjectNode {
        ctx.wellKnownTypeSchema(typeName)?.let { return it }

        val current = inlineExpandingStack.lastOrNull() ?: emptySet()
        if (typeName in current) {
            // Cycle — escape into a component schema and stop recursing.
            collector.collect(typeName)
            return ctx.obj().also {
                it.put("\$ref", "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}")
            }
        }

        val wrapper = ctx.messageIndex.find(typeName) ?: return ctx.obj()
        inlineExpandingStack.addLast(current + typeName)
        try {
            return buildMessageSchema(wrapper, typeName)
        } finally {
            inlineExpandingStack.removeLast()
        }
    }

    // ---- Message and field schema builders (used by SchemaBuilder + inline expansion) -------

    /**
     * Builds the JSON Schema body for a single message type.  Used by [SchemaBuilder] to emit
     * `components/schemas` entries and by [expandInline] to emit inline message bodies at
     * inline boundaries.  When invoked from [expandInline], the inline expansion frame on
     * [inlineExpandingStack] redirects nested message-typed field `$ref`s through
     * [expandInline] when the target is absent from [collector].
     */
    internal fun buildMessageSchema(
        wrapper: DescriptorProtoWrapper,
        typeName: String,
    ): ObjectNode {
        val base = ctx.obj()
        base.put("type", "object")
        ctx.schemaKeyResolver.titleFor(typeName)?.let { base.put("title", it) }

        // ---- Leading comment → description ------------------------------
        val comment = wrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        if (comment != null) base.put("description", comment)

        // ---- Properties from fields -------------------------------------
        val required = mutableListOf<String>()
        val propsNode = ctx.obj()
        for (fieldWrapper in wrapper.fields) {
            val field = fieldWrapper.proto
            val jsonName = if (ctx.preserveProtoFieldNames) {
                // preserve_proto_field_names: always use the raw proto field name.
                // This overrides both auto-camelCase conversion and explicit json_name annotations,
                // matching Envoy's preserve_proto_field_names PrintOption behaviour.
                field.name
            } else {
                field.jsonName.ifEmpty { field.name }
            }
            val fieldSchema = buildFieldSchema(fieldWrapper)
            propsNode.set(jsonName, fieldSchema)

            // proto3 required: proto2 LABEL_REQUIRED, or alwaysPrintPrimitiveFields for
            // non-repeated scalar/enum fields (they will always appear in the response).
            // oneof members (including proto3 optional, which uses a synthetic oneof) are never
            // unconditionally present — only the set field in a oneof is serialized.
            val isPrimitive = field.label != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED &&
                field.type != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE &&
                !field.hasOneofIndex()
            if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REQUIRED ||
                (ctx.alwaysPrintPrimitiveFields && isPrimitive)
            ) {
                required += jsonName
            }
        }
        if (propsNode.size() > 0) base.set("properties", propsNode)
        if (required.isNotEmpty()) {
            val arr = ctx.mapper.createArrayNode()
            for (r in required) arr.add(r)
            base.set("required", arr)
        }

        // ---- engine.protoc.openapi.message annotation override ----------
        val annotation = wrapper.options
            ?.findExtension(Annotations.message)?.value
            ?.takeIf { it.hasSchema() }?.schema

        return if (annotation != null && annotation.schemaValueCase ==
            Schema.SchemaValueCase.OBJECT
        ) {
            with(ctx) { base.deepMerge(annotation.`object`.toJson(ctx)) }
        } else {
            base
        }
    }

    private fun buildFieldSchema(fieldWrapper: FieldDescriptorProtoWrapper): ObjectNode {
        val field = fieldWrapper.proto
        val base = fieldTypeSchema(field)

        // ---- Leading comment → description -----------------------------
        val comment = fieldWrapper.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
        if (comment != null && !base.has("description")) {
            base.put("description", comment)
        }

        // ---- engine.protoc.openapi.field annotation override -----------
        val annotation = fieldWrapper.options
            ?.findExtension(Annotations.field)?.value
            ?.takeIf { it.hasSchema() }?.schema

        return if (annotation != null && annotation.schemaValueCase ==
            Schema.SchemaValueCase.OBJECT
        ) {
            with(ctx) { base.deepMerge(annotation.`object`.toJson(ctx)) }
        } else {
            base
        }
    }

    // ---- Field type → JSON Schema ----------------------------------------

    /**
     * Returns the JSON Schema for a single proto field, wrapping in `{type:array, items:...}`
     * when the field is repeated and not a synthetic map entry.
     *
     * When [inlineMode] is true, the field's top-level message-typed schema (if any) is
     * expanded via [expandInline] rather than emitted as a `$ref` — used by request-body /
     * response-body callers that need to materialise the field's message type inline.  This is
     * independent of [inlineExpandingStack], which controls inline expansion *inside* an
     * already-running expansion.
     */
    internal fun fieldTypeSchema(
        field: DescriptorProtos.FieldDescriptorProto,
        inlineMode: Boolean = false,
    ): ObjectNode {
        val isRepeated =
            field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
        val base = baseFieldSchema(field, inlineMode)
        return if (isRepeated && !isMapField(field)) {
            ctx.obj().also {
                it.put("type", "array")
                it.set("items", base)
            }
        } else {
            base
        }
    }

    private fun baseFieldSchema(
        field: DescriptorProtos.FieldDescriptorProto,
        inlineMode: Boolean = false,
    ): ObjectNode {
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
                val annotationInline = enumWrapper?.options
                    ?.findExtension(Annotations.enum_)?.value
                    ?.takeIf { it.hasInline() }?.inline
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
                val wkt = ctx.wellKnownTypeSchema(typeName)
                val fieldInline = field.options.getExtension(Annotations.field).inline
                when {
                    wkt != null -> wkt

                    // Caller explicitly requested an inline schema for this field's message
                    // (e.g. inline_request_schema on a `body: "<field>"` binding), or the field
                    // itself carries `(engine.protoc.openapi.inline_schema) = true`.
                    inlineMode || fieldInline -> expandInline(typeName)

                    // Inside an active inline expansion: if the target isn't in the collected
                    // set it has no component schema to $ref, so inline-expand it here.
                    inlineExpandingStack.isNotEmpty() && typeName !in collector.collected ->
                        expandInline(typeName)

                    else -> node.also {
                        it.put(
                            "\$ref",
                            "#/components/schemas/${ctx.schemaKeyResolver.buildPhaseKeyOf(typeName)}",
                        )
                    }
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

            val annotation = enumWrapper.options
                ?.findExtension(Annotations.enum_)?.value
                ?.takeIf { it.hasSchema() }?.schema
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
        val annotation = valueWrapper.options
            ?.findExtension(Annotations.enumValue)?.value
            ?.takeIf { it.hasSuppress() }?.suppress
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
