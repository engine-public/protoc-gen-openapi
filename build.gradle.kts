import com.github.jk1.license.LicenseReportExtension
import com.github.jk1.license.filter.LicenseBundleNormalizer
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.jreleaser.gradle.plugin.JReleaserExtension
import org.jreleaser.model.Active
import java.util.Calendar

buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            /*
             * GHSA-f58c-gq56-vjjf — Apache Tika XXE. Transitive of JReleaser.
             */
            if (requested.group == "org.apache.tika" && requested.name == "tika-core") {
                useVersion("3.2.2")
                because("Apache Tika XXE (GHSA-f58c-gq56-vjjf)")
            }
            /*
             * GHSA-6fmv-xxpf-w3cw — plexus-utils path traversal. Transitive of JReleaser.
             */
            if (requested.group == "org.codehaus.plexus" && requested.name == "plexus-utils") {
                useVersion("3.6.1")
                because("plexus-utils directory traversal (GHSA-6fmv-xxpf-w3cw)")
            }
        }
    }
}

plugins {
    application
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.license.report).apply(false)
    alias(libs.plugins.osdetector)
    alias(libs.plugins.protobuf).apply(false)
    `maven-publish`
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

val mavenStagingDir = layout.buildDirectory.dir("staging/maven-central")

configure<JReleaserExtension> {
    project {
        description = "protoc compiler to turn gRPC services into openapi v3.1 specs"
        copyright = "Copyright ${Calendar.getInstance().get(Calendar.YEAR)} HotelEngine, Inc., d/b/a Engine"
        license = "Apache-2.0"
    }
    signing {
        active.set(Active.ALWAYS)
        armored.set(true)
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository(mavenStagingDir.get().asFile.relativeTo(rootDir).path)
                }
            }
        }
    }
}

val jreleaserCreateBuildDir = tasks.register("jreleaserCreateBuildDir") {
    group = "publishing"
    doFirst { project.layout.buildDirectory.dir("jreleaser").get().asFile.mkdirs() }
}
tasks.named("jreleaserDeploy") {
    dependsOn(jreleaserCreateBuildDir)
}

val stageMavenCentral = tasks.register("stageMavenCentral") {
    group = "publishing"
}

val licenseAllowlistFile = rootProject.file("gradle/license/allowed-licenses.json")

dependencies {
    implementation(projects.protocGenOpenapiModel)

    implementation(libs.commonmark)
    implementation(libs.engine.protoc.utils)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.kotlin.reflect)
    implementation(libs.networknt.jsonSchemaValidator)
    implementation(libs.protobuf.java)

    // slf4j-api is already on the classpath transitively (networknt). Ship
    // Log4j 2 as the binding so the plugin's logLevel / logFile options can
    // be applied programmatically via the Configurator API. log4j-core is
    // used directly in applyLoggingConfiguration() (ConfigurationBuilder,
    // Configurator, ConsoleAppender.Target), so it sits on the compile
    // classpath alongside log4j-api. log4j-slf4j2-impl bridges SLF4J 2.x
    // (networknt's transitive slf4j-api line) to log4j-core at runtime.
    // log4j-core 2.25.0+ ships its own GraalVM native-image reachability
    // metadata, so no hand-rolled reflect/resource config is required. The
    // project publishes native binaries (pom-only) so these aren't visible
    // to library consumers as transitive deps.
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    runtimeOnly(libs.log4j.slf4j2.impl)

    // GraalVM hosted API used by native-image Feature classes under
    // com.engine.protoc.openapi.nativeimage. Compile-only — the
    // org.graalvm.nativeimage module is provided by the GraalVM JDK at
    // native-image build time and is not shipped to consumers.
    compileOnly(libs.graalvm.sdk)
}

allprojects {
    apply<IdeaPlugin>()
    apply(plugin = "com.github.jk1.dependency-license-report")
    apply(plugin = "org.cyclonedx.bom")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "maven-publish")

    configure<LicenseReportExtension> {
        allowedLicensesFile = licenseAllowlistFile
        filters = arrayOf(LicenseBundleNormalizer())
        /*
         * Audit only what we actually ship. Test, ktlint, and build-tool
         * classpaths can pull in licenses we don't redistribute.
         */
        configurations = arrayOf("runtimeClasspath")
        /*
         * Gradle 9 disallows cross-project configuration resolution at execution
         * time. Pin each module's license report to its own project only — the
         * `apply(...)` above already runs in every subproject, so each has its
         * own report.
         */
        projects = arrayOf(project)
    }

    afterEvaluate {
        tasks.named("check") {
            dependsOn("checkLicense")
        }
    }

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

    tasks.withType<CyclonedxDirectTask>().configureEach {
        includeConfigs = listOf("runtimeClasspath")
    }

    afterEvaluate {
        /*
         * Wire the direct BOM into `assemble` so `./gradlew build` produces a
         * fresh `build/reports/cyclonedx-direct/bom.json` for every module.
         * The maven publications below attach that file as a classified
         * artifact (classifier=cyclonedx, extension=json), so publish tasks
         * also trigger it transitively via `builtBy`.
         */
        tasks.named("assemble") {
            dependsOn(tasks.named("cyclonedxDirectBom"))
        }
        /*
         * Force `cyclonedxDirectBom` to realize here, while we're still inside
         * subproject afterEvaluate. The cyclonedx plugin attaches its BOM
         * output to the `cyclonedxDirectBom` configuration via a lazy
         * `tasks.named(...).configure { … }` block; if we don't realize the
         * task before any cross-project classpath resolution kicks in,
         * root-project resolution observes (and locks) the empty consumable
         * configuration, and the plugin's later artifact-attach fails with
         * "Cannot mutate the artifacts of configuration … after the
         * configuration was consumed as a variant."
         */
        tasks.named("cyclonedxDirectBom").get()

        configure<TestingExtension> {
            suites {
                configureEach {
                    if (this is JvmTestSuite) {
                        useJUnitJupiter()
                        dependencies {
                            implementation.bundle(libs.bundles.test.kotest)
                        }
                    }
                }
            }
        }

        tasks.withType<Test>().configureEach {
            jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
        }

        configure<PublishingExtension> {
            repositories {
                val mavenUser = System.getenv("MAVEN_USERNAME")
                val mavenPassword = System.getenv("MAVEN_PASSWORD")
                val mavenUrl = System.getenv("MAVEN_DEPLOY_URL")
                maven {
                    name = "stagingMaven"
                    url = mavenUrl?.let { uri(it) } ?: mavenStagingDir.get().asFile.toURI()
                    if (mavenUser != null) {
                        credentials {
                            username = mavenUser
                            password = mavenPassword
                        }
                    }
                }
            }
        }

        tasks.findByName("publish")?.also { publishTask ->
            stageMavenCentral.configure { dependsOn(publishTask) }
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
            // GraalVM auto-appends .exe on Windows; everywhere else we add it
            // explicitly so every published native artifact ends in .exe (the
            // io.grpc:protoc-gen-grpc-java convention).
            val exeSuffix = if (osdetector.os == "windows") "" else ".exe"
            imageName = "${project.name}-${osdetector.os}-${osdetector.arch}$exeSuffix"
            mainClass = application.mainClass
            sharedLibrary = false
            resources.autodetect()
            fallback = false
        }
        all {
            verbose = true
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
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

/*
 * Per-platform native binaries are published to Maven Central as classified
 * artifacts on a POM-only artifact (no main jar, mirroring io.grpc:protoc-gen-grpc-java).
 * Every binary uses the .exe extension regardless of host OS, so the artifact
 * coordinates can be resolved with `:<classifier>@exe` on every platform.
 */
val classifiedNativeArtifacts = listOf(
    "linux-x86_64",
    "linux-aarch_64",
    "osx-aarch_64",
    "windows-x86_64",
)

val nativeBinariesDir: Provider<File> = providers
    .environmentVariable("ENGINE_NATIVE_BIN_DIR")
    .map { rootProject.layout.projectDirectory.dir(it).asFile }
    .orElse(layout.buildDirectory.dir("native/nativeCompile").map { it.asFile })

publishing {
    publications {
        create<MavenPublication>("maven") {
            // intentionally no `from(components["java"])` — the plugin ships
            // only the classified native binaries below, and the main pom is
            // <packaging>pom</packaging>.
            artifact(layout.buildDirectory.file("reports/cyclonedx-direct/bom.json")) {
                classifier = "cyclonedx"
                extension = "json"
                builtBy(tasks.named("cyclonedxDirectBom"))
            }
            pom {
                name.set(project.name)
                packaging = "pom"
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
    val pub = publishing.publications.getByName<MavenPublication>("maven")
    pub.pom {
        description.set(project.description)
        url.set("https://github.com/hotelengine/protoc-gen-openapi/blob/${version}/README.md")
    }

    val binDir = nativeBinariesDir.get()
    val localClassifier = "${osdetector.os}-${osdetector.arch}"
    val nativeCompileTask = tasks.named("nativeCompile")
    val localBinary = layout.buildDirectory.file(
        "native/nativeCompile/${project.name}-$localClassifier.exe",
    )

    classifiedNativeArtifacts.forEach { classifier ->
        // CI release staging produces version-tagged file names; prefer that
        // form when present so all classifiers attach to the publication.
        val stagedFile = binDir.resolve("${project.name}-${project.version}-$classifier.exe")
        when {
            stagedFile.exists() -> pub.artifact(stagedFile) {
                this.classifier = classifier
                this.extension = "exe"
            }
            classifier == localClassifier -> pub.artifact(localBinary) {
                this.classifier = classifier
                this.extension = "exe"
                // Build the binary on demand so publishToMavenLocal triggers
                // nativeCompile automatically.
                builtBy(nativeCompileTask)
            }
            else -> logger.info(
                "No native binary for classifier '{}'; skipping artifact.",
                classifier,
            )
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