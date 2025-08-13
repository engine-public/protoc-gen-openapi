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
        id("org.jlleitschuh.gradle.ktlint").version("13.0.0")
    }
}

dependencyResolutionManagement {
    val kotlinVersion: String by settings // this comes from gradle.properties
    val protobuf = "4.31.1"

    versionCatalogs {
        create("libs") {
            val kotlin = version("kotlin", kotlinVersion)
            val protobuf = version("protobuf", protobuf)
            val slf4j = version("slf4j", "2.0.17")

            library("kotlin.reflect", "org.jetbrains.kotlin", "kotlin-reflect").versionRef(kotlin)
            library("google.api.grpc.googleCommonProtos", "com.google.api.grpc", "proto-google-common-protos").version("2.22.0")
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
