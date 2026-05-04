import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.google.api.AnnotationsProto
import com.google.api.HttpRule
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class AutoMappingConflictTest :
    FunSpec({
        val pkg = "example.conflict"
        val svcName = "ConflictService"
        // The path autoMapping would synthesize for MethodB: POST /pkg.Service/MethodB
        val conflictPath = "/$pkg.$svcName/MethodB"

        // MethodA is explicitly annotated with the same route that MethodB would be auto-mapped to.
        val conflictProto = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("conflict.proto")
            .setSyntax("proto3")
            .setPackage(pkg)
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Request"))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Response"))
            .addService(
                DescriptorProtos.ServiceDescriptorProto.newBuilder()
                    .setName(svcName)
                    .addMethod(
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("MethodA")
                            .setInputType(".$pkg.Request")
                            .setOutputType(".$pkg.Response")
                            .setOptions(
                                DescriptorProtos.MethodOptions.newBuilder()
                                    .setExtension(
                                        AnnotationsProto.http,
                                        HttpRule.newBuilder().setPost(conflictPath).setBody("*").build(),
                                    ),
                            ),
                    )
                    .addMethod(
                        // No annotation — autoMapping synthesizes POST conflictPath, clashing with MethodA.
                        DescriptorProtos.MethodDescriptorProto.newBuilder()
                            .setName("MethodB")
                            .setInputType(".$pkg.Request")
                            .setOutputType(".$pkg.Response"),
                    ),
            )
            .build()

        val rawRequest = PluginProtos.CodeGeneratorRequest.newBuilder()
            .addFileToGenerate("conflict.proto")
            .addProtoFile(conflictProto)
            .build()

        test("annotated route conflicting with auto-mapped route is a compiler error") {
            val result = ProtocGenOpenAPI.from(rawRequest.toByteArray().inputStream()) {
                autoMapping = true
            }.compile()

            result.hasError() shouldBe true
            result.error shouldContain "POST $conflictPath"
            result.error shouldContain "$svcName/MethodA"
            result.error shouldContain "$svcName/MethodB"
        }
    })
