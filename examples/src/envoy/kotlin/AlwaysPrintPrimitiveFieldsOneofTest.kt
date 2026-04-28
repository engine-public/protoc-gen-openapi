import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode

class AlwaysPrintPrimitiveFieldsOneofTest :
    FunSpec({
        // Proto:
        //   message TestMsg {
        //     string regular_string = 1;   // plain scalar — always printed; should be required
        //     oneof choice {
        //       string option_a = 2;        // oneof member — only set field is serialized; NOT required
        //       int32  option_b = 3;        // oneof member — only set field is serialized; NOT required
        //     }
        //   }
        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("oneof_test.proto")
            .setSyntax("proto3")
            .setPackage("example.oneof")
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("TestMsg")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("regular_string")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setJsonName("regularString"),
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("option_a")
                            .setNumber(2)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setOneofIndex(0)
                            .setJsonName("optionA"),
                    )
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("option_b")
                            .setNumber(3)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setOneofIndex(0)
                            .setJsonName("optionB"),
                    )
                    .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("choice")),
            )
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("EmptyMsg"))
            .addService(
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName("TestService")
                    .addMethod(
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("TestMethod")
                            .setInputType(".example.oneof.TestMsg")
                            .setOutputType(".example.oneof.EmptyMsg"),
                    ),
            )
            .build()

        val rawRequest = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate("oneof_test.proto")
            .addProtoFile(file)
            .build()

        val mapper = ObjectMapper()

        test("oneof members are excluded from required with alwaysPrintPrimitiveFields") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                alwaysPrintPrimitiveFields = true
                autoMapping = true
            }.compile()

            result.hasError() shouldBe false

            val doc = mapper.readTree(result.fileList.single().content)
            val required = doc
                .path("components").path("schemas").path("TestMsg")
                .path("required") as? ArrayNode

            val requiredFields = required?.elements()?.asSequence()?.map { it.asText() }?.toList().orEmpty()
            requiredFields shouldBe listOf("regularString")
        }

        test("without alwaysPrintPrimitiveFields no required array is emitted") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                alwaysPrintPrimitiveFields = false
                autoMapping = true
            }.compile()

            result.hasError() shouldBe false

            val doc = mapper.readTree(result.fileList.single().content)
            val schema = doc.path("components").path("schemas").path("TestMsg")
            schema.has("required") shouldBe false
        }
    })
