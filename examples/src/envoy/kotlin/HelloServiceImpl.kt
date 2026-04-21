import com.engine.protoc.openapi.example.envoy.Greeting
import com.engine.protoc.openapi.example.envoy.HelloRequest
import com.engine.protoc.openapi.example.envoy.HelloResponse
import com.engine.protoc.openapi.example.envoy.HelloServiceGrpcKt
import io.kotest.assertions.fail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class HelloServiceImpl : HelloServiceGrpcKt.HelloServiceCoroutineImplBase() {
    private fun createGreeting(
        request: HelloRequest,
        index: Int? = null,
    ): HelloResponse =
        HelloResponse
            .newBuilder()
            .setReplyMessage(
                when (request.greetingType) {
                    Greeting.GREETING_UNSPECIFIED, Greeting.GREETING_HELLO -> "Hello"
                    Greeting.GREETING_HI -> "Hi"
                    Greeting.UNRECOGNIZED -> fail("Unrecognized greeting")
                } + ", ${request.yourName}" + (index?.let { " #$index" } ?: "") + "!",
            )
            .setGreetingUsed(request.greetingType)
            .build()

    override suspend fun sayHello(request: HelloRequest): HelloResponse = createGreeting(request)

    override fun streamHellos(request: HelloRequest): Flow<HelloResponse> =
        flow {
            repeat(3) { i ->
                emit(createGreeting(request, i))
            }
        }
}
