import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    alias(libs.plugins.protobuf)
}

description = "OpenAPI annotations for protobuf Messages, Enums, Services, and RPCs"

dependencies {
    api(libs.protobuf.java)
    api(libs.google.api.grpc.googleCommonProtos)
}

kotlin {
    explicitApi = ExplicitApiMode.Disabled
}

protobuf {
    protoc {
        artifact = libs.tools.protoc.compiler.get().toString()
    }
    plugins {
        create("doc") {
            artifact = libs.tools.protoc.gen.doc.get().toString()
        }
    }
    generateProtoTasks {
        all().all {
            generateDescriptorSet = true
            descriptorSetOptions.includeImports = true

            plugins {
                create("doc") {
                    option("markdown,${project.name}-${version}.md")
                }
            }
        }
    }
}


