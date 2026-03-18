import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.utils.RegisteredExtensions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ServiceAndMethodWrapperTests :
    FunSpec({

        val registry = ExtensionRegistry.newInstance().apply {
            RegisteredExtensions.registerAllExtensions(this)
        }

        val cgreq = this::class.java.getResourceAsStream("/code-generator-request.binpb")
            .shouldNotBeNull()
            .use { input -> PluginProtos.CodeGeneratorRequest.parseFrom(input, registry) }
            .wrap()

        val file = cgreq.sourceFileDescriptors.find {
            it.name == "engine/protoc/utils/comments.proto"
        }.shouldNotBeNull()

        // ---------------------------------------------------------------------------
        // ServiceDescriptorProtoWrapper.name
        // ---------------------------------------------------------------------------

        context("service name") {
            test("ServiceWithComment name value") {
                file.services[0].name.shouldNotBeNull().value shouldBe "ServiceWithComment"
            }

            test("ServiceWithoutComment name value") {
                file.services[1].name.shouldNotBeNull().value shouldBe "ServiceWithoutComment"
            }

            test("file has exactly two services") {
                file.services shouldHaveSize 2
            }
        }

        // ---------------------------------------------------------------------------
        // ServiceDescriptorProtoWrapper.methods
        // ---------------------------------------------------------------------------

        context("service methods") {
            test("ServiceWithComment has two methods") {
                file.services[0].methods shouldHaveSize 2
            }

            test("ServiceWithoutComment has one method") {
                file.services[1].methods shouldHaveSize 1
            }
        }

        // ---------------------------------------------------------------------------
        // MethodDescriptorProtoWrapper — name, inputType, outputType
        // ---------------------------------------------------------------------------

        context("method name") {
            test("first method of ServiceWithComment is MethodWithComment") {
                file.services[0].methods[0].name.shouldNotBeNull().value shouldBe "MethodWithComment"
            }

            test("second method of ServiceWithComment is MethodWithoutComment") {
                file.services[0].methods[1].name.shouldNotBeNull().value shouldBe "MethodWithoutComment"
            }
        }

        context("method inputType and outputType") {
            test("MethodWithComment input type is google.protobuf.Empty") {
                // protoc writes fully-qualified names with a leading dot
                file.services[0].methods[0].inputType.shouldNotBeNull().value shouldBe
                    ".google.protobuf.Empty"
            }

            test("MethodWithComment output type is google.protobuf.Empty") {
                file.services[0].methods[0].outputType.shouldNotBeNull().value shouldBe
                    ".google.protobuf.Empty"
            }

            test("MethodWithoutComment input type is google.protobuf.Empty") {
                file.services[0].methods[1].inputType.shouldNotBeNull().value shouldBe
                    ".google.protobuf.Empty"
            }
        }

        // ---------------------------------------------------------------------------
        // MethodDescriptorProtoWrapper — clientStreaming / serverStreaming
        //
        // For unary RPCs (no `stream` keyword) these fields are not set in the proto
        // descriptor, so the wrapper returns null rather than false.
        // ---------------------------------------------------------------------------

        context("streaming flags") {
            test("unary method has null clientStreaming") {
                file.services[0].methods[0].clientStreaming.shouldBeNull()
            }

            test("unary method has null serverStreaming") {
                file.services[0].methods[0].serverStreaming.shouldBeNull()
            }

            test("second unary method also has null clientStreaming") {
                file.services[0].methods[1].clientStreaming.shouldBeNull()
            }

            test("second unary method also has null serverStreaming") {
                file.services[0].methods[1].serverStreaming.shouldBeNull()
            }
        }

        // ---------------------------------------------------------------------------
        // ServiceDescriptorProtoWrapper.options
        // ---------------------------------------------------------------------------

        context("service options") {
            test("ServiceWithComment has non-null options") {
                file.services[0].options.shouldNotBeNull()
            }

            test("ServiceWithoutComment has null options") {
                file.services[1].options.shouldBeNull()
            }
        }

        // ---------------------------------------------------------------------------
        // MethodDescriptorProtoWrapper.options
        // ---------------------------------------------------------------------------

        context("method options") {
            test("MethodWithComment has non-null options") {
                file.services[0].methods[0].options.shouldNotBeNull()
            }

            test("MethodWithoutComment has null options") {
                file.services[0].methods[1].options.shouldBeNull()
            }
        }
    })