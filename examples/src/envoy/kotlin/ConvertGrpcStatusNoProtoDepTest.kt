package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Verifies that convertGrpcStatus works even when google/rpc/status.proto is absent from the
 * descriptor set. The compiler builds the google.rpc.Status body inline at the use site rather
 * than looking it up in the descriptor or registering it in components/schemas, so no part of
 * the emitted document can dangle on a type the generator never saw.
 */
class ConvertGrpcStatusNoProtoDepTest :
    FunSpec({
        val pkg = "example.status"
        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("status_test.proto")
            .setSyntax("proto3")
            .setPackage(pkg)
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Request"))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Response"))
            .addService(
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName("StatusService")
                    .addMethod(
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("DoThing")
                            .setInputType(".$pkg.Request")
                            .setOutputType(".$pkg.Response")
                            .setOptions(
                                DescriptorProtos.MethodOptions.newBuilder()
                                    .setExtension(
                                        AnnotationsProto.http,
                                        HttpRule.newBuilder().setPost("/do").setBody("*").build(),
                                    ),
                            ),
                    ),
            )
            .build()

        // Intentionally no google/rpc/status.proto in the descriptor set.
        val rawRequest = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate("status_test.proto")
            .addProtoFile(file)
            .build()

        val mapper = ObjectMapper()

        test("convertGrpcStatus emits inline schema without google/rpc/status.proto in the descriptor set") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                inlineRequestSchemas = false
                inlineResponseSchemas = false
                convertGrpcStatus = true
            }.compile()

            result.hasError() shouldBe false

            val doc = mapper.readTree(result.fileList.single().content)

            // The Status envelope must NOT appear in components/schemas — it is always inlined.
            doc.path("components").path("schemas").has("google.rpc.Status") shouldBe false

            // Every operation must have a "default" error response whose schema is the inline
            // google.rpc.Status body (no $ref).
            val defaultResponse = doc
                .path("paths").path("/do").path("post")
                .path("responses").path("default")
            defaultResponse.isMissingNode shouldBe false
            val schema = defaultResponse.path("content")
                .path("application/json").path("schema") as ObjectNode
            schema.has("\$ref") shouldBe false
            schema.path("type").asString() shouldBe "object"
            val props = schema.path("properties")
            props.has("code") shouldBe true
            props.has("message") shouldBe true
            props.has("details") shouldBe true
        }
    })
