rootProject.name = "protoc-gen-openapi"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("testLibs") {
            from(files("gradle/testLibs.versions.toml"))
        }
        create("tools") {
            from(files("gradle/tools.versions.toml"))
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
