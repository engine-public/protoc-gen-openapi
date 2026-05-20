@file:OptIn(ExperimentalTime::class)

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.idea
import org.gradle.kotlin.dsl.`java-test-fixtures`
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

plugins {
    idea
    `java-test-fixtures`
    alias(libs.plugins.protobuf)
    alias(libs.plugins.graalvm.native)
}

dependencies {
    testFixturesImplementation(libs.jackson.databind)
}

testing {
    suites {
        /*
         * we want the compiler to generate multiple CodeGeneratorRequests so we can
         * isolate different types of proto files to be compiled in a single session.
         * this block configures them all...
         */
        withType<JvmTestSuite> {
            useJUnitJupiter()
            val testSuiteName = this.name

            dependencies {
                implementation(projects.protocGenOpenapi)
                implementation(projects.protocGenOpenapiModel)

                implementation(testFixtures(project()))

                implementation(libs.jackson.databind)
                implementation(libs.jackson.dataformat.yaml)
                implementation(libs.jackson.module.kotlin)
                implementation(libs.networknt.jsonSchemaValidator)
                implementation(libs.commonmark)
            }
            tasks.named("process${testSuiteName.capitalized()}Resources", ProcessResources::class) {
                dependsOn("generate${testSuiteName.capitalized()}Proto")
                from(
                    project.layout.buildDirectory
                        .dir("generated/sources/proto/$testSuiteName/recorder")
                        .map { it.file("code-generator-request.binpb") }
                )
            }

            tasks.named("check") {
                dependsOn(this@withType)
            }
        }

        /*
         * then one line here per protoc compilation run...
         */
        register<JvmTestSuite>("petstore")
        register<JvmTestSuite>("complete")
        register<JvmTestSuite>("merged")
        register<JvmTestSuite>("unmerged")
        register<JvmTestSuite>("responseBodyError")
        register<JvmTestSuite>("conventions")
        register<JvmTestSuite>("version")
        register<JvmTestSuite>("namespacing")
        register<JvmTestSuite>("enums")
        register<JvmTestSuite>("filtering")
        register<JvmTestSuite>("wellKnownTypes")
        register<JvmTestSuite>("inlineSchemas")

        /*
         * Envoy integration suite: runs Envoy in a container (via testcontainers) and exercises
         * GrpcJsonTranscoder options against a live in-process gRPC server.
         */
        register<JvmTestSuite>("envoy") {
            dependencies {
                implementation(libs.protobuf.java)
                implementation(libs.grpc.netty)
                implementation(libs.grpc.protobuf)
                implementation(libs.grpc.stub)
                implementation(libs.grpc.kotlin.stub)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.testcontainers)
                runtimeOnly(libs.slf4j.simple)
            }
            tasks.named("processEnvoyResources", ProcessResources::class) {
                dependsOn("generateEnvoyProto")
                from(project.layout.buildDirectory.file("descriptors/hello.pb"))
            }
        }
    }
}

/*
 * Netty arrives transitively via grpc-netty (consumed only by the `envoy`
 * test suite above). grpc-netty 1.81.0 — the latest available release — still
 * pulls Netty 4.1.132.Final, affected by a batch of CVEs disclosed in
 * November 2026. Until grpc-netty publishes against a patched Netty,
 * constrain the affected modules to 4.1.133.Final. Constraints (not
 * `useVersion` forces) so a future grpc upgrade that brings a newer Netty
 * still wins cleanly.
 *
 * Alerts: https://github.com/HotelEngine/protoc-gen-openapi/security/dependabot
 */
dependencies {
    constraints {
        "envoyImplementation"("io.netty:netty-codec-http2:4.1.133.Final") {
            because(
                "CVE-2026-42587 (alert #37): HttpContentDecompressor maxAllocation bypass " +
                    "for br/zstd/snappy content encodings leads to decompression-bomb DoS.",
            )
        }
        "envoyImplementation"("io.netty:netty-codec-http:4.1.133.Final") {
            because(
                "CVE-2026-42587 (#36, decompression DoS), CVE-2026-42585 (#35, malformed " +
                    "Transfer-Encoding smuggling), CVE-2026-42584 (#34, HttpClientCodec " +
                    "response desync), CVE-2026-42581 (#32, HTTP/1.0 TE+CL coexistence " +
                    "smuggling), CVE-2026-42580 (#31, chunk-size parsing smuggling), " +
                    "CVE-2026-41417 (#29, DefaultHttpRequest.setUri start-line injection).",
            )
        }
        "envoyImplementation"("io.netty:netty-codec:4.1.133.Final") {
            because(
                "CVE-2026-42583 (alert #33): Lz4FrameDecoder is vulnerable to resource exhaustion.",
            )
        }
        "envoyImplementation"("io.netty:netty-handler-proxy:4.1.133.Final") {
            because(
                "CVE-2026-42578 (alert #30): HTTP header injection via HttpProxyHandler " +
                    "with validation disabled (incomplete fix for CVE-2025-67735).",
            )
        }
    }
}

protobuf {
    protoc {
        artifact = libs.tools.protoc.compiler.get().toString()
    }
    plugins {
        create("recorder") {
            artifact = libs.tools.protoc.recorder.get().toString()
        }
        create("grpc") {
            artifact = libs.grpc.protoc.java.get().toString()
        }
        create("grpckt") {
            artifact = libs.grpc.protoc.kotlin.get().toString() + ":jdk8@jar"
        }
    }
    generateProtoTasks {
        all().all {
            val suiteName = this.sourceSet.name
            /*
             * Matches all proto tasks except the main generateProto... unfortunately,
             * the protobuf plugin doesn't recognize testsuites as "test" tasks, so
             * isTest doesn't work.
             */
            if (name == "generate${suiteName.capitalized()}Proto") {
                plugins {
                    create("recorder") {
                        option("logLevel=TRACE")
                        option("logFile=${project.layout.buildDirectory.dir("logs/${Clock.System.now().epochSeconds}").map { it.file("${suiteName}.txt") }.get().asFile.absolutePath}")
                    }
                }
                if (suiteName == "envoy") {
                    generateDescriptorSet = true
                    descriptorSetOptions.includeImports = true
                    descriptorSetOptions.path =
                        project.layout.buildDirectory.file("descriptors/hello.pb").get().asFile.absolutePath
                    plugins {
                        create("grpc")
                        create("grpckt")
                    }
                }
            }
        }
    }
}

/*
 * Native-image reflection metadata is recorded by running these example test
 * suites with the GraalVM agent attached (`-Pagent`). Each suite compiles a
 * real `CodeGeneratorRequest` through `ProtocGenOpenAPI.compile()` with a
 * specific permutation of plugin options, so the union of their agent output
 * is the canonical reflection surface of the compiler.
 */
/*
 * Every registered test suite contributes agent recordings except the
 * built-in `test` suite, which is empty (the per-feature suites above are
 * what actually exercise the compiler). New example suites are picked up
 * automatically — no list to maintain alongside the registrations above.
 */
val agentMetadataSuites = testing.suites
    .map { it.name }
    .filter { it != "test" }

graalvmNative {
    agent {
        defaultMode.set("standard")
        callerFilterFiles.from(
            rootProject.file("gradle/native-image-agent/caller-filter.json"),
        )
        accessFilterFiles.from(
            rootProject.file("gradle/native-image-agent/access-filter.json"),
        )
        metadataCopy {
            agentMetadataSuites.forEach { inputTaskNames.add(it) }
            outputDirectories.add(
                rootProject
                    .file("src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi")
                    .absolutePath,
            )
            mergeWithExisting.set(true)
        }
    }
}

/*
 * Single source of truth for which packages are test-only. The agent reads
 * these JSON files at recording time; pruneNativeImageMetadata reads the same
 * files at task-config time to derive its prefix and substring lists so the
 * three can't drift.
 */
val agentFilterFiles = listOf(
    rootProject.file("gradle/native-image-agent/caller-filter.json"),
    rootProject.file("gradle/native-image-agent/access-filter.json"),
)

/*
 * Read the JSON filter files and collect every `excludeClasses` rule, then
 * convert each agent-pattern to a `startsWith` prefix:
 *   "io.kotest.**" → "io.kotest."   (matches "io.kotest." and "io.kotest.X")
 *   "io.kotest"    → "io.kotest."   (treated as a package boundary)
 */
fun parseAgentFilterPrefixes(files: List<File>): List<String> {
    val slurper = JsonSlurper()
    val patterns = mutableSetOf<String>()
    files.forEach { file ->
        val parsed = slurper.parse(file) as Map<*, *>
        (parsed["rules"] as List<*>).forEach { rule ->
            ((rule as Map<*, *>)["excludeClasses"] as? String)?.let { patterns.add(it) }
        }
    }
    return patterns
        .map { pattern ->
            when {
                pattern.endsWith(".**") -> pattern.removeSuffix(".**") + "."
                pattern.endsWith("**") -> pattern.removeSuffix("**")
                else -> "$pattern."
            }
        }
        .sorted()
}

/*
 * The plugin's `accessFilterFiles` wiring appends a bundled default filter
 * after ours, and that bundled file's `{"includeClasses": "**"}` overrides
 * our excludes (last-match-wins). Until that's fixed upstream, post-process
 * the merged metadata to strip test-only entries that leaked through.
 *
 * The filter has two axes:
 *   - name prefix (package): drops both bare and JVM-array forms
 *     (e.g. `[Lio.kotest.X;` → unwrapped `io.kotest.X` matches `io.kotest.`)
 *   - pattern substring: covers resource-config service-loader paths
 *     (e.g. `META-INF/services/org.testcontainers.X`); we emit both dotted
 *     and slashed forms of every prefix
 */
val pruneNativeImageMetadata = tasks.register("pruneNativeImageMetadata") {
    group = "build"
    description = "Strip test-only entries from agent-recorded native-image metadata."

    val targetDir = rootProject.layout.projectDirectory.dir(
        "src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi",
    )
    val excludedPrefixes = parseAgentFilterPrefixes(agentFilterFiles)
    val excludedPatternSubstrings = excludedPrefixes.flatMap { prefix ->
        val base = prefix.removeSuffix(".")
        listOf(base, base.replace('.', '/'))
    } + listOf(
        /*
         * Non-class resource markers not derivable from the package filters.
         * Add here when a framework loads a well-known top-level resource
         * (properties file, service-loader path) outside its own package.
         */
        "kotest.properties",
        "testcontainers.properties",
        "docker-java.properties",
        "junit-platform.properties",
        "code-generator-request.binpb",
        // Reference fixture filenames: every per-suite OAS reference now uses
        // the `engine.protoc.openapi.example.<suite>.*.openapi.{json,yaml}`
        // convention, so this single substring catches all of them.
        "engine.protoc.openapi.example",
        // Petstore's reference file pre-dates the prefix convention; pin it
        // explicitly rather than rename a Swagger-canonical filename.
        "swagger-api.petstore.openapi.yaml",
        // Envoy test setup resources.
        "envoy/envoy.template.yaml",
        "hello.pb",
    )

    doLast {
        fun matchesExcludedPrefix(name: String): Boolean {
            val unwrapped = name.trimStart('[').removePrefix("L").removeSuffix(";")
            return excludedPrefixes.any { unwrapped.startsWith(it) }
        }

        fun isExcluded(entry: Map<*, *>): Boolean {
            (entry["name"] as? String)?.let { if (matchesExcludedPrefix(it)) return true }
            (entry["pattern"] as? String)?.let { pattern ->
                if (excludedPatternSubstrings.any { pattern.contains(it) }) return true
            }
            (entry["interfaces"] as? List<*>)?.let { interfaces ->
                // proxy-config entries declare the interface set their JDK proxy
                // implements; drop the whole entry if any interface is test-only.
                if (interfaces.any { it is String && matchesExcludedPrefix(it) }) {
                    return true
                }
            }
            return false
        }

        fun prune(node: Any?): Any? =
            when (node) {
                is List<*> ->
                    node.mapNotNull { item ->
                        when {
                            item is Map<*, *> && isExcluded(item) -> null
                            else -> prune(item)
                        }
                    }
                is Map<*, *> -> node.mapValues { (_, v) -> prune(v) }
                else -> node
            }

        val slurper = JsonSlurper()
        listOf(
            "reflect-config.json",
            "jni-config.json",
            "resource-config.json",
            "serialization-config.json",
            "proxy-config.json",
        ).forEach { name ->
            val file = targetDir.file(name).asFile
            if (!file.exists()) return@forEach
            val parsed = slurper.parse(file)
            val cleaned = prune(parsed)
            file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(cleaned)) + "\n")
        }
    }
}

tasks.named("metadataCopy") {
    finalizedBy(pruneNativeImageMetadata)
}
