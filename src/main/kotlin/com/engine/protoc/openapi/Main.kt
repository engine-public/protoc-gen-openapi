package com.engine.protoc.openapi

import kotlin.system.exitProcess

public fun main() {
    ProtocGenOpenAPI
        .from(System.`in`)
        .compile()
        .apply {
            writeTo(System.out)
            System.out.flush()
            if (this.hasError()) {
                exitProcess(1)
            }
        }
}
