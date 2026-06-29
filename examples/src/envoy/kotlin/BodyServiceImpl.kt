package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.example.envoy.BodyRequest
import com.engine.protoc.openapi.example.envoy.BodyServiceGrpcKt
import com.engine.protoc.openapi.example.envoy.Greeting
import com.engine.protoc.openapi.example.envoy.HelloResponse
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.fail

/**
 * Backs the BodyService RPCs declared in hello.proto.  Each method translates a [BodyRequest] into
 * a [HelloResponse] mirroring the fields supplied by the caller, so envoy-driven tests can verify
 * that Envoy's transcoder routed path/query/body bindings into the proto fields the OpenAPI spec
 * advertises.
 */
internal class BodyServiceImpl : BodyServiceGrpcKt.BodyServiceCoroutineImplBase() {
    private fun describe(request: BodyRequest): HelloResponse {
        if (request.yourName.isEmpty()) {
            throw StatusException(Status.NOT_FOUND.withDescription("yourName is required"))
        }
        val greeting = when (request.greetingType) {
            Greeting.GREETING_UNSPECIFIED, Greeting.GREETING_HELLO -> "Hello"
            Greeting.GREETING_HI -> "Hi"
            Greeting.UNRECOGNIZED -> fail("Unrecognized greeting")
        }
        val tagSuffix = if (request.tagsList.isEmpty()) "" else " [${request.tagsList.joinToString(",")}]"
        val addressSuffix = if (request.address.city.isEmpty() && request.address.zip.isEmpty()) {
            ""
        } else {
            " from ${request.address.city} ${request.address.zip}".trimEnd()
        }
        return HelloResponse.newBuilder()
            .setReplyMessage("$greeting, ${request.yourName}$addressSuffix$tagSuffix!")
            .setGreetingUsed(request.greetingType)
            .build()
    }

    override suspend fun greetByPath(request: BodyRequest): HelloResponse = describe(request)
    override suspend fun echoNoBody(request: BodyRequest): HelloResponse = describe(request)
    override suspend fun greetWithNamedBody(request: BodyRequest): HelloResponse = describe(request)
    override suspend fun deleteByName(request: BodyRequest): HelloResponse = describe(request)
}
