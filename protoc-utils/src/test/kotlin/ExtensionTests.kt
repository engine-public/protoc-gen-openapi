import com.engine.protoc.util.extensions.findUnregisteredExtension
import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.utils.RegisteredExtensions
import engine.protoc.utils.UnregisteredExtensions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ExtensionTests :
    FunSpec({

        val registry = ExtensionRegistry.newInstance().apply {
            RegisteredExtensions.registerAllExtensions(this)
            // do not add UnregisteredExtensions here!
        }

        val cgreq = this::class.java.getResourceAsStream("/code-generator-request.binpb")
            .shouldNotBeNull()
            .use { input ->
                PluginProtos.CodeGeneratorRequest.parseFrom(input, registry)
            }
            .wrap()

        val file = cgreq.sourceFileDescriptors.find {
            it.name == "engine/protoc/utils/extensions.proto" // TODO make a new proto file for this test
        }.shouldNotBeNull()

        val options = file.options.shouldNotBeNull()

        test("registered file extension") {
            options.findExtension(RegisteredExtensions.registeredExample).shouldNotBeNull().value shouldBe RegisteredExtensions.RegisteredFileExtensionExample.newBuilder().apply { one = 1 }.build()
        }

        test("unregistered file extension") {
            options.findExtension(UnregisteredExtensions.unregisteredExample).shouldBeNull()
            options.proto.findUnregisteredExtension(UnregisteredExtensions.unregisteredExample).shouldNotBeNull() shouldBe UnregisteredExtensions.UnregisteredFileExtensionExample.newBuilder().apply { two = 2 }.build()
        }

        val enumOptions = file.enumTypes[0].options.shouldNotBeNull()

        test("registered enum extension") {
            enumOptions.findExtension(RegisteredExtensions.registeredEnumExample).shouldNotBeNull().value shouldBe RegisteredExtensions.RegisteredEnumExtensionExample.newBuilder().apply { three = 3 }.build()
        }

        test("unregistered enum extension") {
            enumOptions.findExtension(UnregisteredExtensions.unregisteredEnumExample).shouldBeNull()
            enumOptions.proto.findUnregisteredExtension(UnregisteredExtensions.unregisteredEnumExample).shouldNotBeNull() shouldBe UnregisteredExtensions.UnregisteredEnumExtensionExample.newBuilder().apply { four = 4 }.build()
        }

        val serviceOptions = file.services[0].options.shouldNotBeNull()

        test("registered service extension") {
            serviceOptions.findExtension(RegisteredExtensions.registeredServiceExample).shouldNotBeNull().value shouldBe RegisteredExtensions.RegisteredServiceExtensionExample.newBuilder().apply { five = 5 }.build()
        }

        test("unregistered service extension") {
            serviceOptions.findExtension(UnregisteredExtensions.unregisteredServiceExample).shouldBeNull()
            serviceOptions.proto.findUnregisteredExtension(UnregisteredExtensions.unregisteredServiceExample).shouldNotBeNull() shouldBe UnregisteredExtensions.UnregisteredServiceExtensionExample.newBuilder().apply { seven = 7 }.build()
        }

        val methodOptions = file.services[0].methods[0].options.shouldNotBeNull()

        test("registered method extension") {
            methodOptions.findExtension(RegisteredExtensions.registeredMethodExample).shouldNotBeNull().value shouldBe RegisteredExtensions.RegisteredMethodExtensionExample.newBuilder().apply { six = 6 }.build()
        }

        test("unregistered method extension") {
            methodOptions.findExtension(UnregisteredExtensions.unregisteredMethodExample).shouldBeNull()
            methodOptions.proto.findUnregisteredExtension(UnregisteredExtensions.unregisteredMethodExample).shouldNotBeNull() shouldBe UnregisteredExtensions.UnregisteredMethodExtensionExample.newBuilder().apply { eight = 8 }.build()
        }
    })
