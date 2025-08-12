import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")

    id("com.google.osdetector")
    id("org.graalvm.buildtools.native")
    application

    // load plugins for downstream, even though they aren't used in the root project
    // this prevents multiple copies of the plugins from being active, and allows us to configure them here
    id("com.google.protobuf").apply(false)
    id("org.jlleitschuh.gradle.ktlint").apply(false)
}

fun calculateVersion(): String {
    return System
        .getenv("ENGINE_BUILD_VERSION")
        ?.let {
            it.ifEmpty {
                null
            }
        }
        ?: "0.0.0-pre.0" // temporary fallback version
}

description = "protoc compiler to turn gRPC services into openapi v3.1 specs"

dependencies {
    implementation(projects.protocGenOpenapiModel)

    implementation(libs.kotlin.reflect)
    implementation(libs.protobuf.java)
}

allprojects {
    apply<IdeaPlugin>()
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    group = "com.engine"
    version = calculateVersion()

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
        toolchain {
            languageVersion.set(JavaLanguageVersion.of("21"))
            vendor.set(JvmVendorSpec.GRAAL_VM)
        }
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    configure<KotlinJvmProjectExtension> {
        explicitApi()
    }

    configure<KtlintExtension> {
        version.set("1.5.0")
        filter {
            /*
             * work around bug in the ktlint plugin that doesn't honor exclusions of
             * generated code (protobuf, etc.)
             */
            exclude {
                it.file.absolutePath.startsWith(layout.buildDirectory.get().asFile.absolutePath)
            }
        }
        reporters {
            reporter(ReporterType.CHECKSTYLE)
            reporter(ReporterType.HTML)
        }
    }

    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                "Name" to project.name,
                "Specification-Title" to rootProject.name,
                "Specification-Version" to version,
                "Specification-Vendor" to "HotelEngine, Inc., d/b/a Engine",
            )
        }
    }

    afterEvaluate {
        configure<TestingExtension> {
            suites {
                configureEach {
                    if (this is JvmTestSuite) {
                        useJUnitJupiter()
                        dependencies {
                            implementation.bundle(testLibs.bundles.kotest)
                        }
                    }
                }
            }
        }

        tasks.withType<Test>().configureEach {
            jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
        }
    }

    configure<KtlintExtension> {
        version.set("1.7.1")
        filter {
            /*
             * work around bug in the ktlint plugin that doesn't honor exclusions of
             * generated code (protobuf, etc.)
             */
            exclude {
                it.file.absolutePath.startsWith(layout.buildDirectory.get().asFile.absolutePath)
            }
        }
        reporters {
            reporter(ReporterType.CHECKSTYLE)
            reporter(ReporterType.HTML)
        }
    }
}

application {
    mainClass.set("com.engine.protoc.openapi.MainKt")
}

graalvmNative {
    toolchainDetection = false
    binaries {
        named("main") {
            imageName = "${project.name}-${osdetector.arch}"
            mainClass = "com.engine.protoc.openapi.MainKt"
            sharedLibrary = false
            verbose = true
            resources.autodetect()
            fallback = false
        }
        all {
            verbose = true
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            buildArgs.add("-H:ThrowMissingRegistrationErrors=")
        }
    }
    agent {
        enabled = true
        metadataCopy {
            inputTaskNames.add("run")
            outputDirectories.add("src/main/resources/META-INF/native-image/com.engine/protoc-gen-openapi")
            mergeWithExisting = true
        }
    }
    metadataRepository {
        enabled = true
        version = "0.3.24"
    }
}

val writeVersion = tasks.register("writeVersion") {
    val versionFile = project.layout.buildDirectory.map { it.file("version.txt") }
    group = "build"
    outputs.file(versionFile)
    outputs.upToDateWhen { !versionFile.get().asFile.exists() || versionFile.get().asFile.readText() != version.toString() }
    doFirst {
        versionFile
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(version.toString())
    }
}
