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
    })
