import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.core.spec.style.FunSpec
import java.io.File

class Driver: FunSpec({
    test("doit") {
        val plugin = File("/var/tmp/protoc-gen-openapi.cgreq").inputStream().use {
            ProtocGenOpenAPI.from(it) {
                recordCodeGeneratorRequest = null
                recordCodeGeneratorResponse = null
            }
        }
        plugin.compile()
    }
})
