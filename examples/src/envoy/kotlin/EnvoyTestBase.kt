import io.grpc.Server
import io.grpc.ServerBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.shouldNotBeNull
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.dataformat.yaml.YAMLMapper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

private const val ENVOY_IMAGE = "envoyproxy/envoy:v1.35.0"

private class EnvoyContainer(image: String) : GenericContainer<EnvoyContainer>(image)

abstract class EnvoyTestBase(
    options: GrpcJsonTranscoder,
    protobufDescriptorFile: File = File(
        EnvoyTestBase::class.java.getResource("/hello.pb")!!.toURI(),
    ),
) : FunSpec() {
    protected val httpClient: HttpClient = HttpClient.newHttpClient()
    protected var envoyPort: Int = 0

    private var grpcServer: Server? = null
    private var envoyContainer: GenericContainer<*>? = null

    protected var jsonMapper = ObjectMapper()
    protected var yamlMapper = YAMLMapper()

    init {
        beforeSpec {
            grpcServer = ServerBuilder.forPort(0)
                .addService(HelloServiceImpl())
                .build()
                .start()

            val grpcPort = grpcServer!!.port
            Testcontainers.exposeHostPorts(grpcPort)

            val configTree = yamlMapper
                .readTree(
                    EnvoyTestBase::class.java
                        .getResourceAsStream("envoy/envoy.template.yaml")
                        .shouldNotBeNull()
                        .use { inputStream -> inputStream.reader().readText() }
                        .replace($$"${GRPC_PORT}", grpcPort.toString()),
                )
            val listener0 =
                (configTree["static_resources"]["listeners"] as ArrayNode).first { listeners ->
                    listeners["name"].textValue() == "listener_0"
                }
            val connectionManager = listener0["filter_chains"].flatMap { it["filters"] }.first { it["name"].textValue() == "envoy.filters.network.http_connection_manager" }
            val httpFilters = connectionManager["typed_config"]["http_filters"] as ArrayNode

            val grpcTranscoderConfigNode = yamlMapper.nodeFactory.objectNode().apply {
                put("name", "envoy.filters.http.grpc_json_transcoder")
                set("typed_config", yamlMapper.convertValue(options, ObjectNode::class.java))
            }
            val transcoderIndex = httpFilters.indexOfFirst {
                it["name"].textValue() == "envoy.filters.http.grpc_json_transcoder"
            }
            if (transcoderIndex >= 0) {
                httpFilters.set(transcoderIndex, grpcTranscoderConfigNode)
            } else {
                val routerIndex = httpFilters.indexOfFirst { it["name"].textValue() == "envoy.filters.http.router" }
                httpFilters.insert(routerIndex, grpcTranscoderConfigNode)
            }

            val configFile = tempfile("envoy-", ".yaml").apply {
                setReadable(true, false)
            }.apply {
                yamlMapper.writeValue(this, configTree)
            }

            val container = EnvoyContainer(ENVOY_IMAGE)
                .withCopyFileToContainer(
                    MountableFile.forHostPath(configFile.toPath()),
                    "/etc/envoy/envoy.yaml",
                )
                .withCopyFileToContainer(
                    MountableFile.forHostPath(protobufDescriptorFile.toPath()),
                    "/etc/envoy/hello.pb",
                )
                .withExposedPorts(8080, 9901)
                .waitingFor(Wait.forHttp("/ready").forPort(9901))
            container.start()
            envoyContainer = container

            envoyPort = container.getMappedPort(8080)
        }

        afterSpec {
            envoyContainer?.stop()
            grpcServer?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    protected fun postJson(
        path: String,
        body: Any,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$envoyPort$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
