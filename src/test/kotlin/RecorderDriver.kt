import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.ProtocGenOpenAPI.Companion.prepare
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import java.io.File

/**
 * Loads a recorded CodeGeneratorRequest from disk so its contents can be inspected from the IDE.
 *
 * Set a breakpoint on any of the `println` lines (or on the lazy properties below) and run this
 * spec under the IDE debugger to walk the request, the wrapped request, the proto files, the
 * file_to_generate list, etc.
 *
 * Point at a recording with the `RECORDER_BINPB` env var or `-Drecorder.binpb=…` system property.
 * When neither is set the spec silently skips, so `./gradlew test` stays green on machines that
 * don't have a recording staged.
 */
class RecorderDriver :
    FunSpec({
        val path =
            System.getenv("RECORDER_BINPB")
                ?: System.getProperty("recorder.binpb")

        val enabled = path != null
        val file = path?.let(::File)

        // Raw proto (no wrapper, no options application) — handy for `proto.toString()` dumps.
        val raw: PluginProtos.CodeGeneratorRequest by lazy {
            val registry = ExtensionRegistry.newInstance().prepare()
            file!!.inputStream().use { PluginProtos.CodeGeneratorRequest.parseFrom(it, registry) }
        }

        // Plugin's own facade — applies logging configuration and gives us the wrapped CGRequest
        // along with parsed Options. Useful when reproducing real plugin behaviour.
        val plugin: ProtocGenOpenAPI by lazy {
            file!!.inputStream().use { ProtocGenOpenAPI.from(it) }
        }

        test("recorder binpb exists").config(enabled = enabled) {
            check(file!!.exists()) { "Recorder file does not exist: $path" }
            println("Loaded recorder request from: $path (${file.length()} bytes)")
        }

        test("dump request summary").config(enabled = enabled) {
            println("=== parameter ===")
            println(raw.parameter)

            println()
            println("=== files_to_generate (${raw.fileToGenerateCount}) ===")
            raw.fileToGenerateList.forEach { println("  $it") }

            println()
            println("=== proto_file count: ${raw.protoFileCount} ===")

            println()
            println("=== services in files_to_generate ===")
            val targetSet = raw.fileToGenerateList.toSet()
            raw.protoFileList
                .filter { it.name in targetSet }
                .forEach { f ->
                    val pkg = if (f.hasPackage()) f.`package` else "<no-package>"
                    println("  [${f.name}] package=$pkg services=${f.serviceCount}")
                    f.serviceList.forEach { svc ->
                        println("    - ${svc.name} methods=${svc.methodCount}")
                    }
                }

            println()
            println("=== services in proto_file (every file, target or dependency) ===")
            raw.protoFileList
                .filter { it.serviceCount > 0 }
                .forEach { f ->
                    val tag = if (f.name in targetSet) "[target]" else "[dep]   "
                    val pkg = if (f.hasPackage()) f.`package` else "<no-package>"
                    println("  $tag ${f.name} (package=$pkg) services=${f.serviceCount}")
                    f.serviceList.forEach { svc ->
                        println("    - ${svc.name}")
                    }
                }
        }

        // Set a breakpoint inside this test to interactively poke at the wrapped objects.
        // `plugin.request` is a CodeGeneratorRequestWrapper; `plugin.options` are the parsed Options.
        test("debug breakpoint").config(enabled = enabled) {
            val req = plugin // force load — put a breakpoint on the next line
            println("Wrapped request ready. Inspect `req`, `raw`, etc. in the debugger.")
            check(req.toString().isNotEmpty())
        }
    })
