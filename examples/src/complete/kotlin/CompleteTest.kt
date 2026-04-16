import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class CompleteTest :
    FunSpec({

        assertSoftly = true

        val request = CompleteTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = false
            validateOutput = true
            outputFormat = ProtocGenOpenAPI.Options.OutputFormat.YAML
        }.compile()

        val mapper = YAMLMapper()
        val generatedFile = response.fileList
            .find { it.name == "engine.protoc.openapi.example.complete.StorefrontService.openapi.yaml" }
            .shouldNotBeNull()
        val doc: JsonNode = mapper.readTree(generatedFile.content)
        val expected = CompleteTest::class.java.getResourceAsStream("engine.protoc.openapi.example.complete.StorefrontService.openapi.yaml").shouldNotBeNull().reader().readText()

        test("validate reference file") {
            val oasSchema by lazy {
                SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12) {
                    it.schemaIdResolvers {
                        it.mapPrefix(
                            "https://spec.openapis.org/oas/3.1",
                            "classpath:schemas/spec.openapis.org/oas/3.1",
                        )
                    }
                }
                    .getSchema(SchemaLocation.of("https://spec.openapis.org/oas/3.1/schema-base/2022-10-07"))
            }

            oasSchema.validate(expected, InputFormat.YAML) { ctx ->
                ctx.executionConfig { cfg -> cfg.formatAssertionsEnabled(true) }
            }.shouldBeEmpty()
        }

        test("no errors") {
            response.hasError() shouldBe false
            response.error shouldBe ""
        }

        test("openapi version") {
            doc["openapi"].asText() shouldBe "3.1.0"
        }

        // covers OpenAPI.json_schema_dialect
        test("json_schema_dialect") {
            doc["jsonSchemaDialect"].asText() shouldBe "https://spec.openapis.org/oas/3.1/dialect/base"
        }

        test("info — all fields") {
            assertSoftly {
                val info = doc["info"].shouldNotBeNull()
                info["title"].asText() shouldBe "Sporting Goods Storefront API"
                info["summary"].asText() shouldBe "Fictitious sporting goods catalog and ordering API"
                info["description"].shouldNotBeNull()
                info["termsOfService"].asText() shouldBe "https://example.com/terms"
                info["contact"]["email"].asText() shouldBe "support@example.com"
                info["contact"]["name"].asText() shouldBe "Sporting Goods Support"
                info["license"]["name"].asText() shouldBe "Apache 2.0"
                // covers License.identifier (SPDX)
                info["license"]["identifier"].asText() shouldBe "Apache-2.0"
                info["version"].asText() shouldBe "1.0.0"
                // covers Info.extensions
                info["x-info-contact-policy"].asText() shouldBe "verified"
            }
        }

        test("servers with variables") {
            assertSoftly {
                val servers = doc["servers"].shouldNotBeNull()
                servers.isArray shouldBe true
                val first = servers[0]
                first["url"].asText() shouldBe "https://api.example.com/v1"
                val regionVar = first["variables"]["region"]
                regionVar["default"].asText() shouldBe "us-east-1"
                regionVar["enum"].isArray shouldBe true
            }
        }

        // covers OpenAPI.paths and Paths.paths (explicit annotation)
        test("explicit paths from annotation") {
            val healthPath = doc["paths"]["/health"].shouldNotBeNull()
            healthPath["get"]["operationId"].asText() shouldBe "healthStatus"
        }

        test("tags with externalDocs") {
            assertSoftly {
                val tags = doc["tags"].shouldNotBeNull()
                tags.isArray shouldBe true
                val productTag = tags.find { it["name"].asText() == "products" }.shouldNotBeNull()
                productTag["externalDocs"]["url"].asText() shouldBe "https://example.com/docs/products"
                // covers Tag.extensions
                productTag["x-tag-version"].asText() shouldBe "v1"
            }
        }

        test("externalDocs") {
            doc["externalDocs"]["url"].asText() shouldBe "https://example.com/docs/api"
        }

        test("root security") {
            val sec = doc["security"].shouldNotBeNull()
            sec.isArray shouldBe true
            sec[0]["bearerAuth"].shouldNotBeNull()
        }

        // covers SecurityRequirementValues.values (non-empty scope list)
        test("security requirement with scopes (getProduct)") {
            val security = doc["paths"]["/products/{id}"]["get"]["security"].shouldNotBeNull()
            security.isArray shouldBe true
            val oauth2Req = security.find { it["oauth2Auth"] != null }.shouldNotBeNull()
            val scopes = oauth2Req["oauth2Auth"].shouldNotBeNull()
            scopes.isArray shouldBe true
            scopes[0].asText() shouldBe "read:products"
        }

        test("extensions") {
            doc["x-logo"].shouldNotBeNull()
        }

        test("webhooks") {
            val webhooks = doc["webhooks"].shouldNotBeNull()
            val stockAlert = webhooks["stockAlert"].shouldNotBeNull()
            stockAlert["post"]["operationId"].asText() shouldBe "stockAlertWebhook"
        }

        // covers PathItemOrReference.reference oneof, Reference.proto_rpc_ref
        test("webhooks — PathItemOrReference.reference") {
            val ref = doc["webhooks"]["healthCheckWebhook"].shouldNotBeNull()
            ref["\$ref"].asText() shouldBe "#/paths/~1products~1%7Bid%7D"
        }

        // covers PathItem.uri_ref ($ref on PathItem itself)
        test("webhooks — PathItem.uri_ref") {
            val item = doc["webhooks"]["externalPathItem"].shouldNotBeNull()
            item["\$ref"].asText() shouldBe "#/components/pathItems/HealthCheck"
        }

        // §1 — Components with all map fields
        test("§1 — components.responses") {
            assertSoftly {
                val responses = doc["components"]["responses"].shouldNotBeNull()
                responses["NotFound"]["description"].asText() shouldBe "The requested resource was not found"
                responses["Unauthorized"]["description"].asText() shouldBe "Authentication required"
                responses["ValidationError"]["description"].shouldNotBeNull()
                // covers ResponseObject.extensions
                responses["NotFound"]["x-error-code"].asText() shouldBe "RESOURCE_NOT_FOUND"
            }
        }

        test("§1 — components.parameters") {
            assertSoftly {
                val params = doc["components"]["parameters"].shouldNotBeNull()
                params["ProductId"]["name"].asText() shouldBe "productId"
                params["PageSize"]["name"].asText() shouldBe "pageSize"
                params["FilterContext"]["name"].asText() shouldBe "X-Filter-Context"
                // covers Parameter.deprecated
                params["ProductId"]["deprecated"].asBoolean() shouldBe true
                // covers Parameter.allow_empty_value (only valid on query params per OAS 3.1)
                params["PageSize"]["allowEmptyValue"].asBoolean() shouldBe false
                // covers Parameter.extensions
                params["ProductId"]["x-param-group"].asText() shouldBe "product-identifiers"
            }
        }

        test("§1 — components.examples") {
            assertSoftly {
                val examples = doc["components"]["examples"].shouldNotBeNull()
                examples["ProductExample"]["summary"].asText() shouldBe "A typical running shoe"
                examples["ExternalProductExample"]["externalValue"].asText() shouldBe "https://example.com/samples/product.json"
            }
        }

        test("§1 — components.requestBodies") {
            assertSoftly {
                val bodies = doc["components"]["requestBodies"].shouldNotBeNull()
                bodies["CreateProductBody"]["description"].shouldNotBeNull()
            }
        }

        test("§1 — components.headers") {
            assertSoftly {
                val headers = doc["components"]["headers"].shouldNotBeNull()
                headers["RateLimitRemaining"].shouldNotBeNull()
                headers["RateLimitReset"].shouldNotBeNull()
                headers["UploadToken"].shouldNotBeNull()
                headers["X-Structured-Meta"].shouldNotBeNull()
                // covers Header.deprecated
                headers["RateLimitReset"]["deprecated"].asBoolean() shouldBe true
            }
        }

        test("§1 — components.links") {
            assertSoftly {
                val links = doc["components"]["links"].shouldNotBeNull()
                links["GetProductByIdLink"]["operationId"].asText() shouldBe "getProduct"
                links["GetOrderStatusLink"].shouldNotBeNull()
                // covers Link.operation_ref
                links["OperationRefLink"]["operationRef"].asText() shouldBe "#/paths/~1orders/post"
                // covers Link.request_body
                links["OperationRefLink"]["requestBody"].shouldNotBeNull()
            }
        }

        test("§1 — components.callbacks") {
            assertSoftly {
                val callbacks = doc["components"]["callbacks"].shouldNotBeNull()
                callbacks["OrderStatusCallback"].shouldNotBeNull()
                // covers CallbackOrReference.reference
                callbacks["OrderStatusRef"]["\$ref"].asText() shouldBe "#/components/callbacks/OrderStatusCallback"
            }
        }

        test("§1 — components.pathItems") {
            assertSoftly {
                val pathItems = doc["components"]["pathItems"].shouldNotBeNull()
                pathItems["HealthCheck"]["summary"].asText() shouldBe "API health check"
                // covers PathItem.proto_rpc_ref ($ref resolved from RPC binding)
                pathItems["RpcRefPathItem"]["\$ref"].asText() shouldBe "#/paths/~1products~1%7Bid%7D"
            }
        }

        test("§1 — components.securitySchemes") {
            assertSoftly {
                val schemes = doc["components"]["securitySchemes"].shouldNotBeNull()
                schemes["bearerAuth"]["scheme"].asText() shouldBe "bearer"
                schemes["apiKeyAuth"]["name"].asText() shouldBe "X-API-Key"
                // covers SecurityScheme.extensions
                schemes["bearerAuth"]["x-bearer-docs"].shouldNotBeNull()
                // covers SecurityScheme.oauth2
                schemes["oauth2Auth"]["type"].asText() shouldBe "oauth2"
                // covers SecurityScheme.open_id_connect
                schemes["oidcAuth"]["type"].asText() shouldBe "openIdConnect"
                // covers MutualTLSSecurityScheme
                schemes["mtlsAuth"]["type"].asText() shouldBe "mutualTLS"
                // covers SecuritySchemeOrReference.reference
                schemes["bearerRef"]["\$ref"].asText() shouldBe "#/components/securitySchemes/bearerAuth"
            }
        }

        // covers OAuthFlows (all 4 flows) and OAuthFlow (all fields)
        test("oauth2 flows — all four variants") {
            assertSoftly {
                val flows = doc["components"]["securitySchemes"]["oauth2Auth"]["flows"].shouldNotBeNull()
                // covers OAuthFlows.implicit, OAuthFlow.authorization_url, refresh_url, scopes
                val implicit = flows["implicit"].shouldNotBeNull()
                implicit["authorizationUrl"].asText() shouldBe "https://auth.example.com/authorize"
                implicit["refreshUrl"].asText() shouldBe "https://auth.example.com/token/refresh"
                implicit["scopes"]["read:products"].asText() shouldBe "Read product catalog"
                // covers OAuthFlows.password, OAuthFlow.token_url
                val password = flows["password"].shouldNotBeNull()
                password["tokenUrl"].asText() shouldBe "https://auth.example.com/token"
                password["scopes"]["write:products"].asText() shouldBe "Modify products"
                // covers OAuthFlows.client_credentials
                val cc = flows["clientCredentials"].shouldNotBeNull()
                cc["tokenUrl"].asText() shouldBe "https://auth.example.com/token"
                cc["scopes"]["admin"].asText() shouldBe "Admin access"
                // covers OAuthFlows.authorization_code
                val ac = flows["authorizationCode"].shouldNotBeNull()
                ac["authorizationUrl"].asText() shouldBe "https://auth.example.com/authorize"
                ac["tokenUrl"].asText() shouldBe "https://auth.example.com/token"
                ac["refreshUrl"].asText() shouldBe "https://auth.example.com/token/refresh"
                ac["scopes"]["write:orders"].asText() shouldBe "Place orders"
            }
        }

        // covers OpenIDConnectSecurityScheme.openid_connect_url
        test("oidc security scheme") {
            doc["components"]["securitySchemes"]["oidcAuth"]["openIdConnectUrl"]
                .asText() shouldBe "https://auth.example.com/.well-known/openid-configuration"
        }

        // §2 — Operation.callbacks
        test("§2 — Operation.callbacks (placeOrder)") {
            val placeOrder = doc["paths"]["/orders"]["post"].shouldNotBeNull()
            val callbacks = placeOrder["callbacks"].shouldNotBeNull()
            callbacks["onStatusChange"]["\$ref"].asText() shouldBe "#/components/callbacks/OrderStatusCallback"
        }

        // §3 — PathItem.parameters and PathItem operations
        test("§3 — PathItem.parameters (components.pathItems.HealthCheck)") {
            val healthCheck = doc["components"]["pathItems"]["HealthCheck"].shouldNotBeNull()
            val params = healthCheck["parameters"].shouldNotBeNull()
            params.isArray shouldBe true
            params[0]["name"].asText() shouldBe "X-Request-Id"
        }

        // covers PathItem.options, PathItem.head, PathItem.trace, PathItem.servers, PathItem.extensions
        test("HealthCheck pathItem — options, head, trace, servers, extensions") {
            assertSoftly {
                val hc = doc["components"]["pathItems"]["HealthCheck"].shouldNotBeNull()
                // covers PathItem.options
                hc["options"]["operationId"].asText() shouldBe "healthCheckOptions"
                // covers PathItem.head
                hc["head"]["operationId"].asText() shouldBe "healthCheckHead"
                // covers PathItem.trace
                hc["trace"]["operationId"].asText() shouldBe "healthCheckTrace"
                // covers PathItem.servers
                val servers = hc["servers"].shouldNotBeNull()
                servers.isArray shouldBe true
                servers[0]["url"].asText() shouldBe "https://health.example.com/v1"
                // covers PathItem.extensions
                hc["x-health-priority"].shouldNotBeNull()
            }
        }

        // covers PathItem.put (UpdateProduct)
        test("PathItem.put — updateProduct") {
            val putOp = doc["paths"]["/products/{id}"]["put"].shouldNotBeNull()
            putOp["operationId"].asText() shouldBe "updateProduct"
        }

        // covers PathItem.patch (PatchProduct)
        test("PathItem.patch — patchProduct") {
            val patchOp = doc["paths"]["/products/{id}"]["patch"].shouldNotBeNull()
            patchOp["operationId"].asText() shouldBe "patchProduct"
        }

        // covers Parameter.deprecated, extensions (inline parameter)
        test("inline parameter — deprecated, extensions") {
            assertSoftly {
                val params = doc["paths"]["/products/{id}"]["put"]["parameters"].shouldNotBeNull()
                params.isArray shouldBe true
                val idParam = params[0].shouldNotBeNull()
                idParam["deprecated"].asBoolean() shouldBe true
                idParam["x-param-position"].shouldNotBeNull()
            }
        }

        // covers ResponseObject.extensions (inline response)
        test("inline response — ResponseObject.extensions") {
            doc["paths"]["/products/{id}"]["put"]["responses"]["200"]["x-rate-limit-cost"].shouldNotBeNull()
        }

        // covers Reference.uri_ref, Reference.summary, Reference.description
        test("Reference.uri_ref, summary, description (patchProduct parameter)") {
            assertSoftly {
                val params = doc["paths"]["/products/{id}"]["patch"]["parameters"].shouldNotBeNull()
                params.isArray shouldBe true
                // The inferred {id} path param is at index 0; the annotation reference is at index 1
                val ref = params[1].shouldNotBeNull()
                ref["\$ref"].asText() shouldBe "#/components/parameters/ProductId"
                // covers Reference.summary
                ref["summary"].asText() shouldBe "Product reference"
                // covers Reference.description
                ref["description"].asText() shouldBe "Resolved from component ref"
            }
        }

        // §4 — Example with value and externalValue
        test("§4 — Example.value (inline literal)") {
            val example = doc["components"]["examples"]["ProductExample"].shouldNotBeNull()
            example["summary"].asText() shouldBe "A typical running shoe"
            example["value"].shouldNotBeNull()
        }

        test("§4 — Example.externalValue") {
            val example = doc["components"]["examples"]["ExternalProductExample"].shouldNotBeNull()
            example["externalValue"].asText() shouldBe "https://example.com/samples/product.json"
        }

        // §5 — Link and LinkOrReference
        test("§5 — Link.operationId in components.links") {
            val link = doc["components"]["links"]["GetProductByIdLink"].shouldNotBeNull()
            link["operationId"].asText() shouldBe "getProduct"
            link["parameters"]["productId"].shouldNotBeNull()
        }

        // covers Link.proto_rpc_ref (emitted as operationRef), Link.server
        test("§5 — Link.proto_rpc_ref in components.links (GetOrderStatusLink)") {
            assertSoftly {
                val link = doc["components"]["links"]["GetOrderStatusLink"].shouldNotBeNull()
                link["operationRef"].asText() shouldBe "#/paths/~1orders~1%7Border_id%7D/get"
                // covers Link.server
                link["server"]["url"].asText() shouldBe "https://orders.example.com/v1"
                link["server"]["description"].asText() shouldBe "Dedicated order service"
            }
        }

        test("§5 — ResponseObject.links (getProduct)") {
            val response200 = doc["paths"]["/products/{id}"]["get"]["responses"]["200"].shouldNotBeNull()
            val links = response200["links"].shouldNotBeNull()
            links["PlaceOrder"]["operationId"].asText() shouldBe "placeOrder"
            links["GetProduct"]["\$ref"].asText() shouldBe "#/components/links/GetProductByIdLink"
        }

        // §6 — Callback
        test("§6 — Callback with expression key in components.callbacks") {
            val callback = doc["components"]["callbacks"]["OrderStatusCallback"].shouldNotBeNull()
            val expressionKey = "{${'$'}request.body#/callbackUrl}"
            callback[expressionKey].shouldNotBeNull()
        }

        // §7 — MediaType.example / examples / encoding
        test("§7 — MediaType.example (singular, getProduct)") {
            val content = doc["paths"]["/products/{id}"]["get"]["responses"]["200"]["content"]["application/json"].shouldNotBeNull()
            content["example"].shouldNotBeNull()
        }

        test("§7 — MediaType.examples (plural, listProducts)") {
            val content = doc["paths"]["/products"]["get"]["responses"]["200"]["content"]["application/json"].shouldNotBeNull()
            val examples = content["examples"].shouldNotBeNull()
            examples["typical"]["\$ref"].asText() shouldBe "#/components/examples/ProductExample"
            examples["external"]["\$ref"].asText() shouldBe "#/components/examples/ExternalProductExample"
        }

        test("§7 — MediaType.encoding (uploadProductImage)") {
            val requestBody = doc["paths"]["/products/{product_id}/image"]["post"]["requestBody"].shouldNotBeNull()
            val encoding = requestBody["content"]["multipart/form-data"]["encoding"].shouldNotBeNull()
            encoding["image"]["contentType"].asText() shouldBe "image/png, image/jpeg"
        }

        // §8 — Encoding all fields
        test("§8 — Encoding fields (style, explode, allowReserved, headers)") {
            val encoding = doc["paths"]["/products/{product_id}/image"]["post"]["requestBody"]["content"]["multipart/form-data"]["encoding"]["image"].shouldNotBeNull()
            assertSoftly {
                encoding["style"].asText() shouldBe "form"
                encoding["explode"].asBoolean() shouldBe false
                encoding["allowReserved"].asBoolean() shouldBe false
                encoding["headers"]["X-Upload-Token"]["\$ref"].asText() shouldBe "#/components/headers/UploadToken"
            }
        }

        // §9 — ParameterSchema.example / examples
        test("§9 — ParameterSchema.example (components.parameters.ProductId)") {
            val param = doc["components"]["parameters"]["ProductId"].shouldNotBeNull()
            param["example"].shouldNotBeNull()
        }

        test("§9 — ParameterSchema.examples (components.parameters.PageSize)") {
            val param = doc["components"]["parameters"]["PageSize"].shouldNotBeNull()
            val examples = param["examples"].shouldNotBeNull()
            examples["small"]["summary"].asText() shouldBe "Small page"
            examples["large"]["summary"].asText() shouldBe "Large page"
        }

        // §10 — HeaderSchema.example / examples
        test("§10 — HeaderSchema.example (components.headers.RateLimitRemaining)") {
            val header = doc["components"]["headers"]["RateLimitRemaining"].shouldNotBeNull()
            header["example"].shouldNotBeNull()
        }

        test("§10 — HeaderSchema.examples (components.headers.RateLimitReset)") {
            val header = doc["components"]["headers"]["RateLimitReset"].shouldNotBeNull()
            val examples = header["examples"].shouldNotBeNull()
            examples["future"]["summary"].asText() shouldBe "One minute from now"
        }

        // §11 — Parameter.content variant
        test("§11 — Parameter.content variant (components.parameters.FilterContext)") {
            val param = doc["components"]["parameters"]["FilterContext"].shouldNotBeNull()
            val content = param["content"].shouldNotBeNull()
            content["application/json"].shouldNotBeNull()
        }

        test("§11 — Parameter.content variant (inline on listProducts)") {
            val params = doc["paths"]["/products"]["get"]["parameters"].shouldNotBeNull()
            val contentParam = params.find { it["name"]?.asText() == "X-Filter-Context" }.shouldNotBeNull()
            contentParam["content"]["application/json"].shouldNotBeNull()
        }

        // §12 — Header.content variant
        test("§12 — Header.content variant (components.headers.X-Structured-Meta)") {
            val header = doc["components"]["headers"]["X-Structured-Meta"].shouldNotBeNull()
            val content = header["content"].shouldNotBeNull()
            content["application/json"].shouldNotBeNull()
        }

        test("§12 — Header.content variant (inline response header uploadProductImage)") {
            val headers = doc["paths"]["/products/{product_id}/image"]["post"]["responses"]["204"]["headers"].shouldNotBeNull()
            val imageMeta = headers["X-Image-Meta"].shouldNotBeNull()
            val content = imageMeta["content"].shouldNotBeNull()
            content["application/json"].shouldNotBeNull()
        }

        // -----------------------------------------------------------------------
        // SchemaObject coverage via message and field annotations
        // -----------------------------------------------------------------------

        // Product message annotation: title, description, deprecated, external_docs,
        // xml (all fields), discriminator (all fields), required, min/max_properties,
        // additional_properties_allowed, pattern_properties, property_names, extensions
        test("SchemaObject — Product message annotation") {
            assertSoftly {
                val product = doc["components"]["schemas"]["Product"].shouldNotBeNull()
                // covers SchemaObject.title
                product["title"].asText() shouldBe "Sporting Goods Product"
                // covers SchemaObject.description
                product["description"].shouldNotBeNull()
                // covers SchemaObject.deprecated
                product["deprecated"].asBoolean() shouldBe true
                // covers SchemaObject.external_docs
                product["externalDocs"]["url"].asText() shouldBe "https://example.com/docs/products"
                // covers SchemaObject.xml (all 5 fields)
                val xml = product["xml"].shouldNotBeNull()
                xml["name"].asText() shouldBe "product"
                xml["namespace"].asText() shouldBe "https://example.com/schemas"
                xml["prefix"].asText() shouldBe "sg"
                xml["attribute"].shouldNotBeNull()
                xml["wrapped"].shouldNotBeNull()
                // covers SchemaObject.discriminator (property_name + mapping)
                val disc = product["discriminator"].shouldNotBeNull()
                disc["propertyName"].asText() shouldBe "category"
                disc["mapping"]["footwear"].asText() shouldBe "#/components/schemas/Footwear"
                // covers SchemaObject.required
                val required = product["required"].shouldNotBeNull()
                required.isArray shouldBe true
                // covers SchemaObject.min_properties, max_properties
                product["minProperties"].asInt() shouldBe 1
                product["maxProperties"].asInt() shouldBe 20
                // covers SchemaObject.additional_properties_allowed
                product["additionalProperties"].asBoolean() shouldBe false
                // covers SchemaObject.pattern_properties
                product["patternProperties"]["^x-"].shouldNotBeNull()
                // covers SchemaObject.property_names
                product["propertyNames"].shouldNotBeNull()
                // covers SchemaObject.extensions
                product["x-product-type"].asText() shouldBe "physical"
            }
        }

        // Product.id field annotation: read_only, format
        test("SchemaObject — Product.id field annotation (readOnly, format)") {
            assertSoftly {
                val idSchema = doc["components"]["schemas"]["Product"]["properties"]["id"].shouldNotBeNull()
                // covers SchemaObject.read_only
                idSchema["readOnly"].asBoolean() shouldBe true
                // covers SchemaObject.format
                idSchema["format"].asText() shouldBe "uuid"
            }
        }

        // Product.name field annotation: write_only, min_length, max_length, pattern, multi-type
        test("SchemaObject — Product.name field annotation (writeOnly, lengths, pattern, multi-type)") {
            assertSoftly {
                val nameSchema = doc["components"]["schemas"]["Product"]["properties"]["name"].shouldNotBeNull()
                // covers SchemaObject.write_only
                nameSchema["writeOnly"].asBoolean() shouldBe true
                // covers SchemaObject.min_length, max_length
                nameSchema["minLength"].asInt() shouldBe 1
                nameSchema["maxLength"].asInt() shouldBe 200
                // covers SchemaObject.pattern
                nameSchema["pattern"].asText() shouldBe "^[A-Za-z0-9 .,'-]+$"
                // covers SchemaObject.type (repeated / multi-type)
                nameSchema["type"].isArray shouldBe true
            }
        }

        // Product.price field annotation: minimum, exclusiveMinimum, maximum, exclusiveMaximum,
        // multipleOf, default, example (SchemaObject deprecated field)
        test("SchemaObject — Product.price field annotation (numeric bounds, default, example)") {
            assertSoftly {
                val priceSchema = doc["components"]["schemas"]["Product"]["properties"]["price"].shouldNotBeNull()
                // covers SchemaObject.minimum
                priceSchema["minimum"].shouldNotBeNull()
                // covers SchemaObject.exclusive_minimum
                priceSchema["exclusiveMinimum"].shouldNotBeNull()
                // covers SchemaObject.maximum
                priceSchema["maximum"].asDouble() shouldBe 99999.99
                // covers SchemaObject.exclusive_maximum
                priceSchema["exclusiveMaximum"].asDouble() shouldBe 100000.0
                // covers SchemaObject.multiple_of
                priceSchema["multipleOf"].shouldNotBeNull()
                // covers SchemaObject.default
                priceSchema["default"].shouldNotBeNull()
                // covers SchemaObject.example (deprecated field)
                priceSchema["example"].shouldNotBeNull()
            }
        }

        // Product.category field annotation: enum, const
        test("SchemaObject — Product.category field annotation (enum, const)") {
            assertSoftly {
                val catSchema = doc["components"]["schemas"]["Product"]["properties"]["category"].shouldNotBeNull()
                // covers SchemaObject.enum (repeated Any)
                val enumArr = catSchema["enum"].shouldNotBeNull()
                enumArr.isArray shouldBe true
                // covers SchemaObject.const (Any)
                catSchema["const"].shouldNotBeNull()
            }
        }

        // Product.tags field annotation: items, prefixItems, contains, min/maxContains,
        // min/maxItems, uniqueItems, unevaluatedItems (Schema.boolean=false)
        test("SchemaObject — Product.tags field annotation (array keywords)") {
            assertSoftly {
                val tagsSchema = doc["components"]["schemas"]["Product"]["properties"]["tags"].shouldNotBeNull()
                // covers SchemaObject.items
                tagsSchema["items"].shouldNotBeNull()
                // covers SchemaObject.prefix_items
                val prefixItems = tagsSchema["prefixItems"].shouldNotBeNull()
                prefixItems.isArray shouldBe true
                // covers SchemaObject.contains
                tagsSchema["contains"].shouldNotBeNull()
                // covers SchemaObject.min_contains
                tagsSchema["minContains"].shouldNotBeNull()
                // covers SchemaObject.max_contains
                tagsSchema["maxContains"].asInt() shouldBe 10
                // covers SchemaObject.min_items
                tagsSchema["minItems"].shouldNotBeNull()
                // covers SchemaObject.max_items
                tagsSchema["maxItems"].asInt() shouldBe 20
                // covers SchemaObject.unique_items
                tagsSchema["uniqueItems"].asBoolean() shouldBe true
                // covers SchemaObject.unevaluated_items (Schema.boolean=false)
                tagsSchema["unevaluatedItems"].asBoolean() shouldBe false
            }
        }

        // Product.stock_quantity field annotation: deprecated, title, examples (repeated Any)
        test("SchemaObject — Product.stockQuantity field annotation (deprecated, title, examples)") {
            assertSoftly {
                val sqSchema = doc["components"]["schemas"]["Product"]["properties"]["stockQuantity"].shouldNotBeNull()
                // covers SchemaObject.deprecated (on field)
                sqSchema["deprecated"].asBoolean() shouldBe true
                // covers SchemaObject.title (on field)
                sqSchema["title"].asText() shouldBe "Stock Quantity"
                // covers SchemaObject.examples (repeated Any)
                val exs = sqSchema["examples"].shouldNotBeNull()
                exs.isArray shouldBe true
            }
        }

        // ProductList message annotation: all_of (Schema.boolean=true), any_of, one_of,
        // not (Schema.boolean=false), if, then, else, unevaluatedProperties, additionalProperties
        test("SchemaObject — ProductList message annotation (composition keywords)") {
            assertSoftly {
                val pl = doc["components"]["schemas"]["ProductList"].shouldNotBeNull()
                // covers SchemaObject.all_of (repeated Schema, first element is Schema.boolean=true)
                val allOf = pl["allOf"].shouldNotBeNull()
                allOf.isArray shouldBe true
                allOf[0].asBoolean() shouldBe true
                // covers SchemaObject.any_of
                pl["anyOf"].isArray shouldBe true
                // covers SchemaObject.one_of
                pl["oneOf"].isArray shouldBe true
                // covers SchemaObject.not (Schema.boolean=false)
                pl["not"].asBoolean() shouldBe false
                // covers SchemaObject.if_schema
                pl["if"].shouldNotBeNull()
                // covers SchemaObject.then_schema
                pl["then"].shouldNotBeNull()
                // covers SchemaObject.else_schema
                pl["else"].shouldNotBeNull()
                // covers SchemaObject.unevaluated_properties (Schema.boolean=false)
                pl["unevaluatedProperties"].asBoolean() shouldBe false
                // covers SchemaObject.additional_properties_schema (Schema.boolean=true)
                pl["additionalProperties"].asBoolean() shouldBe true
            }
        }

        // Order message annotation: $id, $schema, $anchor, $dynamicAnchor, $dynamicRef, $defs, $ref
        test("SchemaObject — Order message annotation (referencing keywords)") {
            assertSoftly {
                val order = doc["components"]["schemas"]["Order"].shouldNotBeNull()
                // covers SchemaObject.id
                order["\$id"].asText() shouldBe "https://example.com/schemas/Order"
                // covers SchemaObject.schema
                order["\$schema"].asText() shouldBe "https://spec.openapis.org/oas/3.1/dialect/base"
                // covers SchemaObject.anchor
                order["\$anchor"].asText() shouldBe "OrderSchema"
                // covers SchemaObject.dynamic_anchor
                order["\$dynamicAnchor"].asText() shouldBe "DynOrder"
                // covers SchemaObject.dynamic_ref
                order["\$dynamicRef"].asText() shouldBe "#DynOrder"
                // covers SchemaObject.defs
                order["\$defs"]["OrderStatus"].shouldNotBeNull()
                // covers SchemaObject.uri_ref ($ref at SchemaObject level)
                order["\$ref"].asText() shouldBe "#/components/schemas/Order"
            }
        }

        // Order.status field annotation: contentEncoding, contentMediaType, contentSchema
        test("SchemaObject — Order.status field annotation (content keywords)") {
            assertSoftly {
                val statusSchema = doc["components"]["schemas"]["Order"]["properties"]["status"].shouldNotBeNull()
                // covers SchemaObject.content_encoding
                statusSchema["contentEncoding"].asText() shouldBe "utf-8"
                // covers SchemaObject.content_media_type
                statusSchema["contentMediaType"].asText() shouldBe "text/plain"
                // covers SchemaObject.content_schema
                statusSchema["contentSchema"].shouldNotBeNull()
            }
        }

        test("matches expected output") {
            val expected = YAMLMapper().readTree(expected)
            assertSoftly {
                collectJsonDiffs(
                    expected,
                    doc,
                ).forEach { (path, exp, act) ->
                    withClue("at $path — expected: $exp, actual: $act") {
                        act shouldBe exp
                    }
                }
            }
        }
    })
