package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.compile.json.JsonContext
import com.engine.protoc.openapi.compile.json.mergeInto
import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.openapi.Annotations

private const val OUTPUT_FILE_NAME = "swagger.openapi.json"

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
 * The result is a single `swagger.openapi.json` file in the [PluginProtos.CodeGeneratorResponse].
 */
internal class Compiler(private val request: CodeGeneratorRequestWrapper) {

    fun compile(): PluginProtos.CodeGeneratorResponse {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        val messageIndex = MessageIndex(request.protoFiles)
        val ctx = JsonContext(mapper, messageIndex)

        // ---- Find target files in declaration order ----------------------
        val targetFiles = request.filesToGenerate.mapNotNull { name ->
            request.protoFiles.find { it.name == name }
        }

        // ---- Merge file-level OpenAPI extensions ------------------------
        val doc: ObjectNode = ctx.obj()
        doc.put("openapi", "3.1.0")

        for (file in targetFiles) {
            val extension = file.options?.findExtension(Annotations.file)?.value ?: continue
            extension.mergeInto(doc, ctx)
        }

        // ---- Build paths from services ----------------------------------
        val collector = MessageCollector(messageIndex)
        val pathsBuilder = PathsBuilder(ctx, collector)
        val pathsNode = pathsBuilder.build(targetFiles)

        // Merge paths: annotation-defined paths take precedence over RPC-inferred paths
        val existingPaths = doc.get("paths") as? ObjectNode ?: ctx.obj()
        pathsNode.fields().forEach { (path, rpcPathItem) ->
            val existing = existingPaths.get(path) as? ObjectNode
            if (existing != null) {
                with(ctx) { existing.deepMerge(rpcPathItem as ObjectNode) }
            } else {
                existingPaths.set<JsonNode>(path, rpcPathItem)
            }
        }
        if (existingPaths.size() > 0) doc.set<JsonNode>("paths", existingPaths)

        // ---- Build component schemas ------------------------------------
        val schemaBuilder = SchemaBuilder(ctx, pathsBuilder)
        val schemas = schemaBuilder.build(collector)

        if (schemas.size() > 0) {
            val components = doc.get("components") as? ObjectNode
                ?: ctx.obj().also { doc.set<JsonNode>("components", it) }
            val existingSchemas = components.get("schemas") as? ObjectNode
                ?: ctx.obj().also { components.set<JsonNode>("schemas", it) }
            schemas.fields().forEach { (name, schema) ->
                existingSchemas.set<JsonNode>(name, schema)
            }
        }

        // ---- Serialize --------------------------------------------------
        val json = mapper.writeValueAsString(doc)

        val file = PluginProtos.CodeGeneratorResponse.File.newBuilder()
            .setName(OUTPUT_FILE_NAME)
            .setContent(json)
            .build()

        return PluginProtos.CodeGeneratorResponse.newBuilder()
            .setSupportedFeatures(
                PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL.number.toLong(),
            )
            .addFile(file)
            .build()
    }
}
