plugins {
    id("com.google.protobuf")
}

description = "Utilities to assist in the building of a protoc plugin."

dependencies {
    api(libs.protobuf.java)
    testImplementation(libs.protobuf.java)
    testImplementation(testLibs.kotest.framework.datatest)
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
    }
    generateProtoTasks {
        all().all {
            dependsOn(":protoc-utils-recorder:nativeCompile")
            tasks.findByPath(":protoc-utils:processTestResources")!!.dependsOn(this)
            plugins {
                create("recorder")
            }
        }
    }
}

tasks.named("processTestResources", ProcessResources::class) {
    from(project.layout.buildDirectory.dir("generated/source/proto/test/recorder").map { it.file("code-generator-request.binpb") })
}
