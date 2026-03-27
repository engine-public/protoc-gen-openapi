package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.mergeInto
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.engine.protoc.util.compiler.CodeGeneratorResponseWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.compiler.PluginProtos
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

/**
 * Main compilation entry point.
 *
 * For each file in [CodeGeneratorRequestWrapper.filesToGenerate]:
 * 1. File-level `engine.protoc.openapi.file` annotations are merged left-to-right into a single
 *    OpenAPI document (later files overwrite earlier ones for conflicting keys).
 * 2. Services are traversed to build the `paths` section from `google.api.http` and
 *    `engine.protoc.openapi.method` annotations.
 * 3. All message types reachable from RPC inputs/outputs and `proto_ref` usages are collected and
 *    turned into `components/schemas` entries.
 *
 * When [ProtocGenOpenAPI.Options.merge] is true the output is a single file covering all target
 * files.  When false each service produces its own output file; the annotation layering order is
 * file-level → service-derived attributes → service-level annotation.
 *
 * The output file is named `<package>.<ServiceName>.openapi.json` when the compilation covers a
 * single service, or `<common-package-prefix>.openapi.json` when multiple services are merged.
 */
internal class Compiler(
    private val request: CodeGeneratorRequestWrapper,
    private val options: ProtocGenOpenAPI.Options,
) {
    private val oasSchema by lazy {
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) {
            it.schemaIdResolvers {
                it.mapPrefix(
                    "https://spec.openapis.org/oas/3.1",
                    "classpath:schemas/spec.openapis.org/oas/3.1",
                )
            }
        }.getSchema(SchemaLocation.of("https://spec.openapis.org/oas/3.1/schema-base/2022-10-07"))
    }

    fun compile(): PluginProtos.CodeGeneratorResponse = if (options.merge) compileMerged() else compileUnmerged()

    // -------------------------------------------------------------------------
    // Merged path — one output covering all target files (original behaviour)
    // -------------------------------------------------------------------------

    private fun compileMerged(): PluginProtos.CodeGeneratorResponse {
        val response = CodeGeneratorResponseWrapper()
        val (mapper, ctx, targetFiles) = setup()

        val doc: ObjectNode = ctx.obj()
        doc.put("openapi", "3.1.0")

        for (file in targetFiles) {
            try {
                val extension = file.options?.findExtension(Annotations.file)?.value ?: continue
                extension.mergeInto(doc, ctx)
            } catch (e: Exception) {
                response.addError("[${file.name}] Error merging OpenAPI extension: ${e.detail()}")
            }
        }

        val collector = MessageCollector(ctx.messageIndex)
        val pathsBuilder = PathsBuilder(ctx, collector)

        for (file in targetFiles) {
            try {
                mergePaths(doc, pathsBuilder.build(listOf(file)), ctx)
            } catch (e: Exception) {
                response.addError("[${file.name}] Error building paths: ${e.detail()}")
            }
        }

        try {
            mergeSchemas(doc, SchemaBuilder(ctx, pathsBuilder).build(collector), ctx)
        } catch (e: Exception) {
            response.addError("Error building component schemas: ${e.detail()}")
        }

        if (!response.hasErrors) {
            try {
                response.addFile(outputFileName(targetFiles), mapper.writeValueAsString(doc))
            } catch (e: Exception) {
                response.addError("Error serializing output: ${e.detail()}")
            }
        }

        return response.build()
    }

    // -------------------------------------------------------------------------
    // Unmerged path — one output per service (or per file when no services)
    // -------------------------------------------------------------------------

    private fun compileUnmerged(): PluginProtos.CodeGeneratorResponse {
        val response = CodeGeneratorResponseWrapper()
        val (mapper, ctx, targetFiles) = setup()

        for (file in targetFiles) {
            val fileAnnotation = try {
                file.options?.findExtension(Annotations.file)?.value
            } catch (e: Exception) {
                response.addError("[${file.name}] Error reading file-level annotation: ${e.detail()}")
                null
            }

            if (file.services.isEmpty()) {
                // File-only output: emit when a file-level annotation exists, no services present.
                if (fileAnnotation == null) continue
                try {
                    val doc = ctx.obj().also { it.put("openapi", "3.1.0") }
                    fileAnnotation.mergeInto(doc, ctx)
                    val pkg = file.`package`?.value.orEmpty()
                    val fileName = if (pkg.isEmpty()) "openapi.json" else "$pkg.openapi.json"
                    response.addFile(fileName, mapper.writeValueAsString(doc))
                } catch (e: Exception) {
                    response.addError("[${file.name}] Error generating file-only output: ${e.detail()}")
                }
                continue
            }

            for (service in file.services) {
                val svcLabel = "${file.name}/${service.name?.value}"
                try {
                    val doc = ctx.obj().also { it.put("openapi", "3.1.0") }

                    // Layer 1: attributes derived from the service itself (lowest priority)
                    applyServiceDerivedAttributes(doc, service, ctx)

                    // Layer 2: file-level annotation overwrites derived values
                    fileAnnotation?.mergeInto(doc, ctx)

                    // Layer 3: explicit service-level annotation (highest priority)
                    service.options?.findExtension(Annotations.service)?.value
                        ?.mergeInto(doc, ctx)

                    // Paths — only this service's methods
                    val collector = MessageCollector(ctx.messageIndex)
                    val pathsBuilder = PathsBuilder(ctx, collector)
                    mergePaths(doc, pathsBuilder.buildForService(service), ctx)

                    // Schemas — only messages responsive to this service
                    mergeSchemas(doc, SchemaBuilder(ctx, pathsBuilder).build(collector), ctx)

                    val pkg = file.`package`?.value.orEmpty()
                    val svcName = service.name?.value.orEmpty()
                    val fileName = listOf(pkg, svcName)
                        .filter { it.isNotEmpty() }
                        .joinToString(".") + ".openapi.json"
                    val content = mapper.writeValueAsString(doc)
                    response.addFile(fileName, content)

                    if (options.validateOutput) {
                        oasSchema.validate(content, InputFormat.JSON) { ctx ->
                            ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
                        }.forEach {
                            response.addError(it.toString())
                        }
                    }
                } catch (e: Exception) {
                    response.addError("[$svcLabel] Error generating output: ${e.detail()}")
                }
            }
        }

        return response.build()
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private data class Setup(
        val mapper: ObjectMapper,
        val ctx: JsonContext,
        val targetFiles: List<FileDescriptorProtoWrapper>,
    )

    private fun setup(): Setup {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val messageIndex = MessageIndex(request.protoFiles)
        val ctx = JsonContext(mapper, messageIndex)
        val targetFiles = request.filesToGenerate.mapNotNull { name ->
            request.protoFiles.find { it.name == name }
        }
        return Setup(mapper, ctx, targetFiles)
    }

    private fun mergePaths(
        doc: ObjectNode,
        pathsNode: ObjectNode,
        ctx: JsonContext,
    ) {
        if (pathsNode.size() == 0) return
        val existingPaths = doc.get("paths") as? ObjectNode
            ?: ctx.obj().also { doc.set<JsonNode>("paths", it) }
        pathsNode.fields().forEach { (path, rpcPathItem) ->
            val existing = existingPaths.get(path) as? ObjectNode
            if (existing != null) {
                with(ctx) { existing.deepMerge(rpcPathItem as ObjectNode) }
            } else {
                existingPaths.set<JsonNode>(path, rpcPathItem)
            }
        }
    }

    private fun mergeSchemas(
        doc: ObjectNode,
        schemas: ObjectNode,
        ctx: JsonContext,
    ) {
        if (schemas.size() == 0) return
        val components = doc.get("components") as? ObjectNode
            ?: ctx.obj().also { doc.set<JsonNode>("components", it) }
        val existingSchemas = components.get("schemas") as? ObjectNode
            ?: ctx.obj().also { components.set<JsonNode>("schemas", it) }
        schemas.fields().forEach { (name, schema) ->
            existingSchemas.set<JsonNode>(name, schema)
        }
    }

    private fun applyServiceDerivedAttributes(
        doc: ObjectNode,
        service: ServiceDescriptorProtoWrapper,
        ctx: JsonContext,
    ) {
        val infoNode = doc.get("info") as? ObjectNode
            ?: ctx.obj().also { doc.set<JsonNode>("info", it) }
        service.name?.value?.let { infoNode.put("title", it) }
        service.location?.proto?.leadingComments?.trim()?.ifEmpty { null }
            ?.let { infoNode.put("description", it) }
    }

    private fun Exception.detail(): String =
        buildString {
            append(this@detail.javaClass.simpleName)
            message?.let { append(": $it") }
            var cause = cause
            while (cause != null) {
                append("\n  Caused by: ${cause.javaClass.simpleName}")
                cause.message?.let { append(": $it") }
                cause = cause.cause
            }
        }

    private fun outputFileName(targetFiles: List<FileDescriptorProtoWrapper>): String {
        val services = targetFiles.flatMap { file ->
            file.services.map { svc -> file.`package`?.value.orEmpty() to svc.name?.value.orEmpty() }
        }
        return when (services.size) {
            1 -> "${services[0].first}.${services[0].second}.openapi.json"

            else -> {
                val packages = services.map { it.first }
                    .ifEmpty { targetFiles.map { it.`package`?.value.orEmpty() } }
                val prefix = commonPackagePrefix(packages)
                if (prefix.isEmpty()) "openapi.json" else "$prefix.openapi.json"
            }
        }
    }

    private fun commonPackagePrefix(packages: List<String>): String {
        val nonEmpty = packages.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return ""
        val parts = nonEmpty.map { it.split(".") }
        val common = mutableListOf<String>()
        for (i in 0 until parts.minOf { it.size }) {
            if (parts.all { it[i] == parts[0][i] }) common.add(parts[0][i]) else break
        }
        return common.joinToString(".")
    }
}
