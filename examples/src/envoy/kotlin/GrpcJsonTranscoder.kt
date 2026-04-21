import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

/**
 * [EnvoyDocumentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder)
 *
 * [proto](https://github.com/envoyproxy/envoy/blob/0533de0acca281110945e5726bbb306fbb12bde5/api/envoy/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#L29)
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GrpcJsonTranscoder(
    /**
     * Supplies the filename of the proto descriptor set for the gRPC services.
     */
    val protoDescriptor: String = "/etc/envoy/hello.pb",

    /**
     * A list of strings that supplies the fully qualified service names (i.e. “package_name.service_name”) that the transcoder will translate.
     * If the service name doesn’t exist in [protoDescriptor], Envoy will fail at startup.
     * The [protoDescriptor] may contain more services than the service names specified here, but they won’t be translated.
     *
     * By default, the filter will pass through requests that do not map to any specified services.
     * If the list of services is empty, filter is considered disabled.
     * However, this behavior changes if [RequestValidationOptions.rejectUnknownMethod] is enabled.
     */
    val services: List<String> = listOf("engine.protoc.openapi.example.envoy.HelloService"),

    /**
     * Control options for response JSON.
     * These options are passed directly to [JsonPrintOptions](https://developers.google.com/protocol-buffers/docs/reference/cpp/google.protobuf.util.json_util#JsonPrintOptions).
     */
    val printOptions: PrintOptions? = null,

    /**
     * Whether to keep the incoming request route after the outgoing headers have been transformed to the match the upstream gRPC service.
     * Note: This means that routes for gRPC services that are not transcoded cannot be used in combination with [matchIncomingRequestRoute].
     */
    val matchIncomingRequestRoute: Boolean = false,

    /**
     * A list of query parameters to be ignored for transcoding method mapping.
     * By default, the transcoder filter will not transcode a request if there are any unknown/invalid query parameters.
     *
     * Example:
     * ```
     * service Bookstore {
     *   rpc GetShelf(GetShelfRequest) returns (Shelf) {
     *     option (google.api.http) = {
     *       get: "/shelves/{shelf}"
     *     };
     *   }
     * }
     *
     * message GetShelfRequest {
     *   int64 shelf = 1;
     * }
     *
     * message Shelf {}
     * ```
     *
     * The request `/shelves/100?foo=bar` will not be mapped to `GetShelf` because variable binding for foo is not defined.
     * Adding `foo` to [ignoredQueryParameters] will allow the same request to be mapped to `GetShelf`.
     */
    val ignoredQueryParameters: List<String> = emptyList(),

    /**
     * Whether to route methods without the `google.api.http` option.
     *
     * Example:
     * ```
     * package bookstore;
     *
     * service Bookstore {
     *   rpc GetShelf(GetShelfRequest) returns (Shelf) {}
     * }
     *
     * message GetShelfRequest {
     *   int64 shelf = 1;
     * }
     *
     * message Shelf {}
     * ```
     *
     * The client could post a json body `{"shelf": 1234}` with the path of `/bookstore.Bookstore/GetShelfRequest` to call `GetShelfRequest`.
     *
     *
     * Engine note: Explicit `google.api.http` annotations take precedence over [autoMapping].
     * This is the equivalent of adding the following annotation to each rpc lacking the `google.api.http` annotation in the exposed [services]:
     * ```
     * package engine.protoc.openapi.example.envoy;
     * service ExampleService {
     *   rpc ExampleVerb(google.protobuf.Empty) returns (google.protobuf.Empty) {
     *     option (.google.api.http) = {
     *       post: "/engine.protoc.openapi.example.envoy.ExampleService/ExampleVerb"
     *       body: "*"
     *     };
     *   }
     * }
     * ```
     */
    val autoMapping: Boolean = false,

    /**
     * Whether to ignore query parameters that cannot be mapped to a corresponding protobuf field.
     * Use this if you cannot control the query parameters and do not know them beforehand.
     * Otherwise use ignored_query_parameters.
     * Defaults to false.
     */
    val ignoreUnknownQueryParameters: Boolean = false,

    /**
     * Whether to convert gRPC status headers to JSON.
     * When trailer indicates a gRPC error and there was no HTTP body, take google.rpc.Status from the grpc-status-details-bin header and use it as JSON body.
     * If there was no such header, make google.rpc.Status out of the grpc-status and grpc-message headers.
     * The error details types must be present in the proto_descriptor.
     *
     * For example, if an upstream server replies with headers:
     *
     * ```
     * grpc-status: 5
     * grpc-status-details-bin: CAUaMwoqdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUucnBjLlJlcXVlc3RJbmZvEgUKA3ItMQ
     * ```
     *
     * The grpc-status-details-bin header contains a base64-encoded protobuf message google.rpc.Status.
     * It will be transcoded into:
     *
     * ```
     * HTTP/1.1 404 Not Found
     * content-type: application/json
     *
     * {"code":5,"details":[{"@type":"type.googleapis.com/google.rpc.RequestInfo","requestId":"r-1"}]}
     * ```
     *
     * In order to transcode the message, the google.rpc.RequestInfo type from the google/rpc/error_details.proto should be included in the configured proto descriptor set.
     */
    val convertGrpcStatus: Boolean = false,

    /**
     * URL unescaping policy.
     * This spec is only applied when extracting variable with multiple segments in the URL path.
     * For example, in case of /foo/{x=*}/bar/{y=prefix/\u002A}/{z=**} x variable is single segment and y and z are multiple segments.
     * For a path with /foo/first/bar/prefix/second/third/fourth, x=first, y=prefix/second, z=third/fourth.
     * If this setting is not specified, the value defaults to [UrlUnescapeSpec.ALL_CHARACTERS_EXCEPT_RESERVED].
     */
    val urlUnescapeSpec: String? = null,

    /**
     * If true, unescape ‘+’ to space when extracting variables in query parameters. This is to support []HTML 2.0](https://tools.ietf.org/html/rfc1866#section-8.2.1)
     */
    val queryParamUnescapePlus: Boolean = false,

    /**
     * If true, try to match the custom verb even if it is unregistered.
     * By default, only match when it is registered.
     *
     * According to the http template [syntax](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto#L226-L231), the custom verb is “:” **LITERAL** at the end of http template.
     *
     * For a request with `/foo/bar:baz` and `:baz` is not registered in any url_template, here is the behavior change - if the field is not set, `:baz` will not be treated as custom verb, so it will match `/foo/{x=*}`. - if the field is set, `:baz` is treated as custom verb, so it will NOT match `/foo/{x=*}` since the template doesn’t use any custom verb.
     */
    val matchUnregisteredCustomVerb: Boolean = false,

    /**
     * Configure the behavior when handling requests that cannot be transcoded.
     *
     * By default, the transcoder will silently pass through HTTP requests that are malformed.
     * This includes requests with unknown query parameters, unregister paths, etc.
     *
     * Set these options to enable strict HTTP request validation, resulting in the transcoder rejecting such requests with a `HTTP 4xx`.
     * See each individual option for more details on the validation.
     * gRPC requests will still silently pass through without transcoding.
     *
     * The benefit is a proper error message to the downstream.
     * If the upstream is a gRPC server, it cannot handle the passed-through HTTP requests and will reset the TCP connection.
     * The downstream will then receive a `HTTP 503 Service Unavailable` due to the upstream connection reset.
     * This incorrect error message may conflict with other Envoy components, such as retry policies.
     */
    val requestValidationOptions: RequestValidationOptions? = null,

    /**
     * Proto enum values are supposed to be in upper cases when used in JSON.
     * Set this to true if your JSON request uses non uppercase enum values.
     */
    val caseInsensitiveEnumParsing: Boolean = false,

    /**
     * The maximum size of a request body to be transcoded, in bytes.
     * A body exceeding this size will provoke a `HTTP 413 Request Entity Too Large` response.
     *
     * Large values may cause envoy to use a lot of memory if there are many concurrent requests.
     *
     * If unset, the current stream buffer size is used.
     */
    val maxRequestBodySize: UInt = 0u,

    /**
     * The maximum size of a response body to be transcoded, in bytes.
     * A body exceeding this size will provoke a `HTTP 500 Internal Server Error` response.
     *
     * Large values may cause envoy to use a lot of memory if there are many concurrent requests.
     *
     * If unset, the current stream buffer size is used.
     */
    val maxResponseBodySize: UInt = 0u,

    /**
     * If true, query parameters that cannot be mapped to a corresponding protobuf field are captured in an HttpBody extension of UnknownQueryParams.
     */
    val captureUnknownQueryParameters: Boolean = false,
) {
    /**
     * [Envoy Documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-printoptions)
     *
     * [proto](https://github.com/envoyproxy/envoy/blob/0533de0acca281110945e5726bbb306fbb12bde5/api/envoy/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#L50)
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class PrintOptions(
        /**
         * Whether to add spaces, line breaks and indentation to make the JSON output easy to read. Defaults to false.
         */
        val addWhiteSpace: Boolean = false,

        /**
         * Whether to always print primitive fields.
         * By default primitive fields with default values will be omitted in JSON output.
         * For example, an int32 field set to 0 will be omitted.
         * Setting this flag to true will override the default behavior and print primitive fields regardless of their values.
         * Defaults to false.
         */
        val alwaysPrintPrimitiveFields: Boolean = false,

        /**
         * Whether to always print enums as ints.
         * By default they are rendered as strings.
         * Defaults to false.
         */
        val alwaysPrintEnumsAsInts: Boolean = false,

        /**
         * Whether to preserve proto field names.
         * By default protobuf will generate JSON field names using the `json_name` option, or lower camel case, in that order.
         * Setting this flag will preserve the original field names.
         * Defaults to false.
         */
        val preserveProtoFieldNames: Boolean = false,

        /**
         * If true, return all streams as newline-delimited JSON messages instead of as a comma-separated array
         */
        val streamNewlineDelimited: Boolean = false,

        /**
         * If true, enforces Server-Sent Events (SSE) message framing (`data: <message>\n\n`) and, [streamNewlineDelimited] is ignored.
         * If false, message framing is determined by [streamNewlineDelimited].
         */
        val streamSseStyleDelimited: Boolean = false,
    )

    /**
     * [Envoy Documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-requestvalidationoptions)
     *
     * [proto](https://github.com/envoyproxy/envoy/blob/0533de0acca281110945e5726bbb306fbb12bde5/api/envoy/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#L83)
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class RequestValidationOptions(
        /**
         * By default, a request that cannot be mapped to any specified gRPC [services] will pass-through this filter.
         * When set to true, the request will be rejected with a `HTTP 404 Not Found`.
         */
        val rejectUnknownMethod: Boolean = false,

        /**
         * By default, a request with query parameters that cannot be mapped to the gRPC request message will pass-through this filter.
         * When set to true, the request will be rejected with a HTTP 400 Bad Request.
         *
         * The fields [ignoreUnknownQueryParameters], [captureUnknownQueryParameters], and [ignoredQueryParameters] have priority over this strict validation behavior.
         */
        val rejectUnknownQueryParameters: Boolean = false,

        /**
         * “id: 456” in the body will override “id=123” in the binding.
         *
         * If this field is set to true, the request will be rejected if the binding value is different from the body value.
         */
        val rejectBindingBodyFieldCollisions: Boolean = false,
    )

    /**
     * [Envoy Documentation](https://www.envoyproxy.io/docs/envoy/latest/api-v3/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#enum-extensions-filters-http-grpc-json-transcoder-v3-grpcjsontranscoder-urlunescapespec)
     *
     * [proto](https://github.com/envoyproxy/envoy/blob/0533de0acca281110945e5726bbb306fbb12bde5/api/envoy/extensions/filters/http/grpc_json_transcoder/v3/transcoder.proto#L33)
     */
    enum class UrlUnescapeSpec {
        /**
         * URL path parameters will not decode RFC 6570 reserved characters. For example, segment `%2f%23/%20%2523` is unescaped to `%2f%23/ %23`.
         */
        ALL_CHARACTERS_EXCEPT_RESERVED,

        /**
         * URL path parameters will be fully URI-decoded except in cases of single segment matches in reserved expansion, where `%2F` will be left encoded. For example, segment `%2f%23/%20%2523` is unescaped to `%2f#/ %23`.
         */
        ALL_CHARACTERS_EXCEPT_SLASH,

        /**
         * URL path parameters will be fully URI-decoded. For example, segment `%2f%23/%20%2523` is unescaped to `/#/ %23`.
         */
        ALL_CHARACTERS,
    }

    @get:JsonProperty("@type")
    val type = "type.googleapis.com/envoy.extensions.filters.http.grpc_json_transcoder.v3.GrpcJsonTranscoder"
}
