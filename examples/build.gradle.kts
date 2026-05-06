import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.idea
import org.gradle.kotlin.dsl.`java-test-fixtures`

plugins {
    idea
    `java-test-fixtures`
    alias(libs.plugins.protobuf)
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

protobuf {
    protoc {
        artifact = libs.tools.protoc.compiler.get().toString()
    }
    plugins {
        create("recorder") {
            path = project
                .project(projects.protocUtilsRecorder.path)
                .layout
                .buildDirectory
                .map { it.dir("native/nativeCompile").file("${projects.protocUtilsRecorder.name}-${osdetector.arch}") }
                .get()
                .asFile
                .absolutePath
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
                dependsOn(":protoc-utils-recorder:nativeCompile")
                plugins {
                    create("recorder")
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
