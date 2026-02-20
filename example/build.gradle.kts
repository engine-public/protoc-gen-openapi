import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("com.google.protobuf")
}

description = "An example project that uses the protoc-gen-openpi plugin"

dependencies {
    api(libs.protobuf.java)
    implementation(projects.protocGenOpenapiModel)
}

kotlin {
    explicitApi = ExplicitApiMode.Disabled
}

protobuf {
    protoc {
        artifact = tools.protoc.compiler.get().toString()
    }
    plugins {
        create("openapi") {
            path = rootProject
                .layout
                .buildDirectory
                .map { it.dir("native/nativeCompile").file("${rootProject.name}-${osdetector.arch}") }
                .get()
                .asFile
                .absolutePath
        }
    }
    generateProtoTasks {
        all().all {
            dependsOn(
                project
                    .project(projects.protocGenOpenapi.path)
                    .tasks
                    .named("nativeCompile")
            )
            plugins {
                create("openapi") {
                    option("recordCodeGeneratorRequest=/var/tmp/protoc-gen-openapi.cgreq")
                    option("recordCodeGeneratorResponse=/var/tmp/protoc-gen-openapi.cgresp")
                }
            }
        }
    }
}
