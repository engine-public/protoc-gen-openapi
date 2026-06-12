package com.engine.protoc.openapi.example

import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.example.envoy.Greeting
import com.google.protobuf.compiler.PluginProtos
import io.kotest.assertions.withClue
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import tools.jackson.module.kotlin.readValue

/**
 * Exercises the three HttpRule.body modes (unset, "*", named field) against a live Envoy
 * transcoder and snapshots the generated OpenAPI for the BodyService defined in hello.proto.
 *
 * The runtime assertions confirm that the parameter bindings declared by the generated spec line
 * up with what Envoy actually accepts: path variables travel in the URL path, named-body fields
 * travel in the JSON body, and the remaining fields are read from the query string regardless of
 * HTTP verb.
 */
class BodyHandlingTest :
    EnvoyTestBase(
        GrpcJsonTranscoder(
            services = listOf(
                "engine.protoc.openapi.example.envoy.HelloService",
                "engine.protoc.openapi.example.envoy.BodyService",
            ),
        ),
    ) {
    init {
        context("body unset — POST /echo binds every field from the query string") {
            test("POST /echo with all fields in the query string echoes back the request") {
                val response = postJsonNoBody(
                    "/echo",
                    mapOf(
                        "yourName" to "World",
                        "greetingType" to Greeting.GREETING_HI.name,
                        "address.city" to "NYC",
                        "address.zip" to "10001",
                        "tags" to "alpha",
                    ),
                )
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"] shouldBe "Hi, World from NYC 10001 [alpha]!"
                body["greeting"] shouldBe Greeting.GREETING_HI.name
            }
        }

        context("body unset on GET — path variable + remaining fields as query parameters") {
            test("GET /greet/{your_name}?greetingType=…&address.city=… returns a greeting") {
                val response = getJson(
                    "/greet/World",
                    mapOf(
                        "greetingType" to Greeting.GREETING_HELLO.name,
                        "address.city" to "Boston",
                    ),
                )
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"] shouldBe "Hello, World from Boston!"
            }
        }

        context("body=\"address\" — named-field body, the rest become query parameters") {
            test("POST /greet/named with Address body and other fields in query string") {
                val response = postJson(
                    "/greet/named",
                    body = mapOf("city" to "Paris", "zip" to "75001"),
                    query = mapOf(
                        "yourName" to "World",
                        "greetingType" to Greeting.GREETING_HELLO.name,
                    ),
                )
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"] shouldBe "Hello, World from Paris 75001!"
            }
        }

        context("DELETE behaves identically to GET — body unset, fields as query") {
            test("DELETE /greet/{your_name}?greetingType=… returns the same greeting shape") {
                val response = deleteJson(
                    "/greet/World",
                    mapOf("greetingType" to Greeting.GREETING_HI.name),
                )
                response.statusCode() shouldBe 200
                val body = jsonMapper.readValue<Map<String, Any>>(response.body())
                body["replyMessage"] shouldBe "Hi, World!"
            }
        }

        context("validate BodyService openapi output") {
            fun request() =
                BodyHandlingTest::class.java
                    .getResourceAsStream("/code-generator-request.binpb")
                    .shouldNotBeNull()

            val result = ProtocGenOpenAPI.from(request()) {

                inlineRequestSchemas = false

                inlineResponseSchemas = false
                serviceInclude = "BodyService"
                version = "1.0.0"
            }.compile()

            test("no errors") {
                result.hasError() shouldBe false
                result.error shouldBe ""
            }

            withData<PluginProtos.CodeGeneratorResponse.File>(
                { "matches reference: " + it.name },
                result.fileList,
            ) { file ->
                GoldenFiles.maybeWriteGolden("envoy", "${file.name}.BodyHandlingTest.json", file.content)
                val expected = jsonMapper.readTree(
                    BodyHandlingTest::class.java
                        .getResourceAsStream("/${file.name}.BodyHandlingTest.json")
                        .shouldNotBeNull()
                        .reader()
                        .readText(),
                )
                collectJsonDiffs(expected, jsonMapper.readTree(file.content)).forEach { (path, exp, act) ->
                    withClue("at $path -- expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    }
}
