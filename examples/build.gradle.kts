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
            dependencies {
                implementation(projects.protocGenOpenapi)
                implementation(projects.protocGenOpenapiModel)

                implementation(testFixtures(project()))

                implementation(libs.jackson.databind)
                implementation(libs.jackson.dataformat.yaml)
                implementation(libs.networknt.jsonSchemaValidator)
                implementation(libs.commonmark)
            }
            val testSuiteName = this.name
            tasks.named("process${testSuiteName.capitalized()}Resources", ProcessResources::class) {
                dependsOn("generate${testSuiteName.capitalized()}Proto")
                from(project.layout.buildDirectory.dir("generated/sources/proto/$testSuiteName/recorder").map { it.file("code-generator-request.binpb") })
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
    }
}

protobuf {
    protoc {
        artifact = tools.protoc.compiler.get().toString()
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
    }
    generateProtoTasks {
        all().all {
            /*
             * Matches all proto tasks except the main generateProto... unfortunately,
             * the protobuf plugin doesn't recognize testsuites as "test" tasks, so
             * isTest doesn't work.
             */
            if (name == "generate${this.sourceSet.name.capitalized()}Proto") {
                dependsOn(":protoc-utils-recorder:nativeCompile")
                plugins {
                    create("recorder")
                }
            }
        }
    }
}
