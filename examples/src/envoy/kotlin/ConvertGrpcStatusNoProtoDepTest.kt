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
 * descriptor set. The compiler builds the google.rpc.Status schema inline rather than looking it
 * up in the descriptor, so the $ref it emits on every operation can never dangle.
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
                convertGrpcStatus = true
            }.compile()

            result.hasError() shouldBe false

            val doc = mapper.readTree(result.fileList.single().content)

            // The inline schema must be present — no dangling $ref.
            val statusSchema = doc.path("components").path("schemas").path("google.rpc.Status") as? ObjectNode
            statusSchema?.get("type")?.asString() shouldBe "object"
            val props = statusSchema?.path("properties")
            props?.has("code") shouldBe true
            props?.has("message") shouldBe true
            props?.has("details") shouldBe true

            // Every operation must have a "default" error response referencing the schema.
            val defaultResponse = doc
                .path("paths").path("/do").path("post")
                .path("responses").path("default")
            defaultResponse.isMissingNode shouldBe false
            val ref = defaultResponse.path("content")
                .path("application/json").path("schema").path("\$ref").asString()
            ref shouldBe "#/components/schemas/google.rpc.Status"
        }
    })
