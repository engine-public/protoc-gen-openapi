import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    `maven-publish`
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

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
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

/*
 * Ship the raw .proto sources as a zip on the GitHub release so non-Gradle
 * consumers can drop them on `protoc`'s include path without having to extract
 * them out of the published jar.
 */
val zipModelProtos = tasks.register<Zip>("zipModelProtos") {
    group = "distribution"
    description = "Bundle the model .proto sources as a zip for the GitHub release."
    archiveBaseName.set("${project.name}-protos")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(layout.projectDirectory.dir("src/main/proto"))
}

tasks.named("assemble") {
    dependsOn(zipModelProtos)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(layout.buildDirectory.file("reports/cyclonedx-direct/bom.json")) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(tasks.named("cyclonedxDirectBom"))
            }
            pom {
                name.set(project.name)
                inceptionYear.set("2025")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://github.com/hotelengine/protoc-gen-openapi/blob/${version}/LICENSE")
                    }
                }
                developers {
                    developer {
                        organizationUrl.set("https://github.com/hotelengine")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/hotelengine/protoc-gen-openapi.git")
                    developerConnection.set("scm:git:https://github.com/hotelengine/protoc-gen-openapi.git")
                    url.set("https://github.com/hotelengine/protoc-gen-openapi")
                }
            }
        }
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("maven") {
        pom {
            description.set(project.description)
            url.set("https://github.com/hotelengine/protoc-gen-openapi/blob/${version}/model/README.md")
        }
    }
}