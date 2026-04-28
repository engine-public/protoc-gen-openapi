import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import tools.jackson.databind.ObjectMapper

/**
 * Verifies that preserveProtoFieldNames applies to nested message schemas resolved via $ref,
 * not just to the top-level message. Each schema is built independently, but all share the
 * same JsonContext, so the option must be consistent across all depths.
 */
class PreserveProtoFieldNamesNestedTest :
    FunSpec({
        // Proto:
        //   message Inner {
        //     string nested_value = 1;   // json_name = "nestedValue" (default camelCase)
        //   }
        //   message Outer {
        //     Inner inner_thing = 1;     // json_name = "innerThing"
        //   }
        val pkg = "example.nested"
        val file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("nested.proto")
            .setSyntax("proto3")
            .setPackage(pkg)
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Inner")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("nested_value")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setJsonName("nestedValue"),
                    ),
            )
            .addMessageType(
                DescriptorProtos.DescriptorProto.newBuilder()
                    .setName("Outer")
                    .addField(
                        DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("inner_thing")
                            .setNumber(1)
                            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName(".$pkg.Inner")
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                            .setJsonName("innerThing"),
                    ),
            )
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Empty"))
            .addService(
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName("NestedService")
                    .addMethod(
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("DoThing")
                            .setInputType(".$pkg.Outer")
                            .setOutputType(".$pkg.Empty")
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

        val rawRequest = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate("nested.proto")
            .addProtoFile(file)
            .build()

        val mapper = ObjectMapper()

        test("preserveProtoFieldNames applies to nested schemas resolved via ref as well as top-level") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                preserveProtoFieldNames = true
            }.compile()

            result.hasError() shouldBe false

            val schemas = mapper.readTree(result.fileList.single().content)
                .path("components").path("schemas")

            // Outer: property key must be proto name "inner_thing", not json_name "innerThing"
            val outerProps = schemas.path("Outer").path("properties")
            outerProps.has("inner_thing") shouldBe true
            outerProps.has("innerThing") shouldBe false

            // Inner (resolved independently via $ref): property key must also be proto name
            val innerProps = schemas.path("Inner").path("properties")
            innerProps.has("nested_value") shouldBe true
            innerProps.has("nestedValue") shouldBe false
        }

        test("without preserveProtoFieldNames nested schemas use json_name") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                preserveProtoFieldNames = false
            }.compile()

            result.hasError() shouldBe false

            val schemas = mapper.readTree(result.fileList.single().content)
                .path("components").path("schemas")

            val outerProps = schemas.path("Outer").path("properties")
            outerProps.has("innerThing") shouldBe true
            outerProps.has("inner_thing") shouldBe false

            val innerProps = schemas.path("Inner").path("properties")
            innerProps.has("nestedValue") shouldBe true
            innerProps.has("nested_value") shouldBe false
        }
    })
