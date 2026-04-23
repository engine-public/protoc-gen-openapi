import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.io.File

/**
 * Generates the four reference JSON files for the enums example.
 * Run once via `./gradlew :protoc-gen-openapi-examples:enums -Dkotest.tags=generate`
 * (actually just run this main directly from the build classpath).
 */
fun main() {
    val binpb = File("examples/build/generated/sources/proto/enums/recorder/code-generator-request.binpb")
    val outDir = File("examples/src/enums/resources")
    outDir.mkdirs()

    val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    data class Run(val label: String, val block: ProtocGenOpenAPI.Options.Builder.() -> Unit)

    val runs = listOf(
        Run("run1") {
            merge = true
            validateOutput = false
        },
        Run("run2") {
            merge = true
            inlineEnums = true
            validateOutput = false
        },
        Run("run3") {
            merge = true
            suppressDefaultEnumValues = true
            validateOutput = false
        },
        Run("run4") {
            merge = true
            inlineEnums = true
            suppressDefaultEnumValues = true
            validateOutput = false
        },
    )

    for (run in runs) {
        val response = ProtocGenOpenAPI.from(binpb.inputStream(), block = run.block).compile()
        check(!response.hasError()) { "Error in ${run.label}: ${response.error}" }
        for (file in response.fileList) {
            val node = mapper.readTree(file.content)
            val out = File(outDir, "${file.name}.${run.label}.json")
            mapper.writeValue(out, node)
            println("Wrote ${out.name}")
        }
    }
}
