rootProject.name = "protoc-gen-openapi"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
    plugins {
        val kotlinVersion: String by settings // this comes from gradle.properties
        kotlin("jvm").version(kotlinVersion)

        id("com.google.osdetector").version("1.7.3")
        id("com.google.protobuf").version("0.9.4")
        id("org.graalvm.buildtools.native").version("0.11.0")
        id("org.jlleitschuh.gradle.ktlint").version("14.2.0")
    }
}

dependencyResolutionManagement {
    val kotlinVersion: String by settings // this comes from gradle.properties
    val protobuf = "4.31.1"

    versionCatalogs {
        create("libs") {
            val jackson = version("jackson", "2.21.2")
            val kotlin = version("kotlin", kotlinVersion)
            val networknt = version("networknt", "3.0.1")
            val protobuf = version("protobuf", protobuf)
            val slf4j = version("slf4j", "2.0.17")

            library("google.api.grpc.googleCommonProtos", "com.google.api.grpc", "proto-google-common-protos").version("2.22.0")
            library("jackson.databind", "com.fasterxml.jackson.core", "jackson-databind").versionRef(jackson)
            library("kotlin.reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef(kotlin)
            library("networknt.json.schema.validator", "com.networknt", "json-schema-validator").versionRef(networknt)
            library("protobuf.java", "com.google.protobuf", "protobuf-java").versionRef(protobuf)
            library("slf4j.api", "org.slf4j", "slf4j-api").versionRef(slf4j)
        }
        create("testLibs") {
            val kotest = version("kotest", "5.9.1")

            val krj5 = "kotest.runner.junit5".apply {
                library(this, "io.kotest", "kotest-runner-junit5").versionRef(kotest)
            }
            val kac = "kotest.assertions.core".apply {
                library(this, "io.kotest", "kotest-assertions-core").versionRef(kotest)
            }

            library("kotest.framework.datatest", "io.kotest", "kotest-framework-datatest").versionRef(kotest)

            bundle("kotest", listOf(krj5, kac))
        }
        create("tools") {
            val protobuf = version("protobuf", protobuf)

            library("protoc.compiler", "com.google.protobuf", "protoc").versionRef(protobuf)
            library("protoc.gen.doc", "io.github.pseudomuto", "protoc-gen-doc").version("1.5.1")
        }
    }
}

rootDir
    .walkTopDown()
    .filter { it != rootDir }
    .filter { it.isDirectory }
    .filterNot { it.name.startsWith(".") }
    .filterNot { setOf("build", "buildSrc", "tmp", "scratch").contains(it.name) }
    .filterNot { it.resolve(".gradle_ignore").exists() }
    .filter { it.resolve("build.gradle.kts").let { it.exists() && it.isFile } }
    .forEach {
        val relativePath = it.relativeTo(rootDir)
        val projectName = ":${rootProject.name}-${relativePath.path.replace("/", "-")}"
        include(projectName)
        project(projectName).projectDir = it
    }

// manually include projects intended to be moved out of monorepo
include(":protoc-utils")
project(":protoc-utils").projectDir = rootDir.resolve("protoc-utils")

include(":protoc-utils-recorder")
project(":protoc-utils-recorder").projectDir = rootDir.resolve("protoc-utils/recorder")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
