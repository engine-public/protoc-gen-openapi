import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    application
    idea
    `java-test-fixtures`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.ktlint)
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
    implementation(projects.protocUtils)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.kotlin.reflect)
    implementation(libs.networknt.jsonSchemaValidator)
    implementation(libs.protobuf.java)

    testFixturesImplementation(libs.jackson.databind)
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

    configurations.named("ktlint").configure {
        resolutionStrategy {
            eachDependency {
                /*
                 * https://github.com/HotelEngine/protoc-gen-openapi/security/dependabot/3
                 * https://nvd.nist.gov/vuln/detail/CVE-2026-1225
                 */
                if (requested.group == "ch.qos.logback" && requested.module.name.startsWith("logback-")) {
                    useVersion("[1.5.25,)")
                }
            }
        }
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
        version.set("1.8.0")
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
        version.set("1.8.0")
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
                implementation(project())

                // Explicitly extend the implementation configuration
                configurations.named(sources.implementationConfigurationName) {
                    extendsFrom(project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME))
                }
            }
            val testSuiteName = this.name
            tasks.named("process${testSuiteName.capitalized()}Resources", ProcessResources::class) {
                dependsOn("generate${testSuiteName.capitalized()}Proto")
                from(project.layout.buildDirectory.dir("generated/sources/proto/$testSuiteName/recorder").map { it.file("code-generator-request.binpb") })
            }
        }

        /*
         * then one line here per protoc compilation run...
         */
        register<JvmTestSuite>("petstore") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
        register<JvmTestSuite>("complete") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
        register<JvmTestSuite>("merged") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
        register<JvmTestSuite>("unmerged") {
            dependencies {
                implementation(testFixtures(project()))
            }
        }
        register<JvmTestSuite>("responseBodyError") {
            dependencies {
                implementation(testFixtures(project()))
            }
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
            mainClass = application.mainClass
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
//        create("openapi") {
//            path = project
//                .layout
//                .buildDirectory
//                .map { it.dir("native/nativeCompile").file("${project.name}-${osdetector.arch}") }
//                .get()
//                .asFile
//                .absolutePath
//        }
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
//                dependsOn(":nativeCompile")
                plugins {
                    create("recorder")
//                    create("openapi")
                }
            }
        }
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

gradle.taskGraph.whenReady {
    gradle.taskGraph.allTasks.forEach {
        if (project.hasProperty("codeql")) {
            if (it.name.startsWith("nativeCompile")) {
                logger.quiet("Disabling ${it.path} due to codeql run.")
                it.enabled = false
            }
        }
    }
}
