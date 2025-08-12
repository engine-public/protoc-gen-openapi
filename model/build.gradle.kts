import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("com.google.protobuf")
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
        artifact = tools.protoc.compiler.get().toString()
    }
    plugins {
        create("doc") {
            artifact = tools.protoc.gen.doc.get().toString()
        }
    }
    generateProtoTasks {
        all().forEach {
            it.generateDescriptorSet = true
            it.descriptorSetOptions.includeImports = true

            it.plugins {
                create("doc") {
                    option("markdown,${project.name}-${version}.md")
                }
            }
        }
    }
}
