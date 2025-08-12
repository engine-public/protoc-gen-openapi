package com.engine.protoc.openapi

public fun main() {
    ProtocGenOpenAPI
        .from(System.`in`)
        .compile()
        .apply {
            writeTo(System.out)
        }
}
