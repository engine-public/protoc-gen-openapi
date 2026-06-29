package com.engine.protoc.openapi.example

import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden-file (reference output) helpers for the example suites.
 *
 * Reference files live at `examples/src/<suite>/resources/<name>` and are compared against freshly
 * generated output by each suite's test.  When the generator's output legitimately changes, set the
 * environment variable `UPDATE_GOLDENS=true` and re-run the suites: every [maybeWriteGolden] call
 * then overwrites its reference file with the freshly generated content instead of asserting.
 *
 * The suite test task runs with its working directory at the `examples` module root, so the source
 * `resources` directory is resolved relative to `user.dir`.  Calls are inert (a no-op) unless the
 * environment variable is set, so they are safe to leave in the test sources permanently.
 *
 * The checked-in references are mixed-format: some JSON files are canonicalized with `jq -S`
 * (sorted keys), others are the generator's own pretty-printed output (insertion order).  To keep
 * regeneration diffs minimal, [maybeWriteGolden] matches whichever format the existing reference
 * already uses — a file that round-trips through `jq -S` unchanged is rewritten with `jq -S`,
 * otherwise the generator output is written verbatim.  `jq` is therefore only required when an
 * existing reference is `jq`-formatted; this affects only the developer-driven regeneration path,
 * never normal test runs.
 */
public object GoldenFiles {
    public val updateEnabled: Boolean = System.getenv("UPDATE_GOLDENS") == "true"

    /**
     * When `UPDATE_GOLDENS=true`, writes [content] to `src/<suite>/resources/<fileName>` (creating
     * the directory if needed) and returns `true`.  Otherwise returns `false` and writes nothing.
     * The written content matches the existing reference's formatting (see the class doc).
     */
    public fun maybeWriteGolden(
        suite: String,
        fileName: String,
        content: String,
    ): Boolean {
        if (!updateEnabled) return false
        val dir = Path.of(System.getProperty("user.dir"), "src", suite, "resources")
        Files.createDirectories(dir)
        val target = dir.resolve(fileName)
        val canonical = if (fileName.endsWith(".json") && existingIsJqFormatted(target)) {
            jqSort(content)
        } else {
            content
        }
        Files.writeString(target, if (canonical.endsWith("\n")) canonical else "$canonical\n")
        return true
    }

    /** True when [target] exists and is byte-identical to its own `jq -S` canonicalization. */
    private fun existingIsJqFormatted(target: Path): Boolean {
        if (!Files.exists(target)) return false
        val existing = Files.readString(target)
        return runCatching { jqSort(existing).trimEnd() == existing.trimEnd() }.getOrDefault(false)
    }

    /** Canonicalizes JSON via `jq -S` (sorted keys), matching the checked-in reference format. */
    private fun jqSort(json: String): String {
        val process = ProcessBuilder("jq", "-S", ".").redirectErrorStream(false).start()
        process.outputStream.use { it.write(json.toByteArray()) }
        val out = process.inputStream.readBytes().decodeToString()
        val code = process.waitFor()
        check(code == 0) { "jq -S failed (exit $code) while canonicalizing a golden file" }
        return out
    }
}
