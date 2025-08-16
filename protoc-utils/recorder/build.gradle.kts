plugins {
    application
    id("com.google.osdetector")
    id("org.graalvm.buildtools.native")
}

dependencies {
    implementation(libs.protobuf.java)
}

application {
    mainClass = "com.engine.protoc.util.recorder.MainKt"
}

graalvmNative {
    toolchainDetection = false
    binaries {
        named("main") {
            imageName = "${project.name}-${osdetector.arch}"
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
            buildArgs.add("-H:ThrowMissingRegistrationErrors=")
        }
    }
}
