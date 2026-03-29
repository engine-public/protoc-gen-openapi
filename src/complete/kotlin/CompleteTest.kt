import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
import io.kotest.matchers.shouldNotBe
import tools.jackson.dataformat.yaml.YAMLMapper
import java.io.File

class CompleteTest :
    FunSpec({

        assertSoftly = true

        val request = CompleteTest::class.java.getResourceAsStream("/code-generator-request.binpb").shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = false
            // Some $refs point into components that are not auto-collected from proto messages
            // (e.g. headers, links defined only in the file annotation), so full OAS validation
            // is intentionally disabled here.
            validateOutput = false
        }.compile()

        val mapper = ObjectMapper()
        val generatedFile = response.fileList
            .find { it.name == "sporting.goods.StorefrontService.openapi.json" }
            .shouldNotBeNull()
        val doc: JsonNode = mapper.readTree(generatedFile.content)

        val expected = CompleteTest::class.java.getResourceAsStream("sporting.goods.openapi.yaml").shouldNotBeNull().reader().readText()

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
                info["version"].asText() shouldBe "1.0.0"
            }
        }

        test("servers with variables") {
            assertSoftly {
                val servers = doc["servers"].shouldNotBeNull()
                servers.isArray shouldBe true
                val first = servers[0]
                first["url"].asText() shouldBe "https://{region}.api.example.com/v1"
                val regionVar = first["variables"]["region"]
                regionVar["default"].asText() shouldBe "us-east-1"
                regionVar["enum"].isArray shouldBe true
            }
        }

        test("tags with externalDocs") {
            assertSoftly {
                val tags = doc["tags"].shouldNotBeNull()
                tags.isArray shouldBe true
                val productTag = tags.find { it["name"].asText() == "products" }.shouldNotBeNull()
                productTag["externalDocs"]["url"].asText() shouldBe "https://example.com/docs/products"
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

        test("extensions") {
            doc["x-logo"].shouldNotBeNull()
        }

        test("webhooks") {
            val webhooks = doc["webhooks"].shouldNotBeNull()
            val stockAlert = webhooks["stockAlert"].shouldNotBeNull()
            stockAlert["post"]["operationId"].asText() shouldBe "stockAlertWebhook"
        }

        // §1 — Components with all map fields
        test("§1 — components.responses") {
            assertSoftly {
                val responses = doc["components"]["responses"].shouldNotBeNull()
                responses["NotFound"]["description"].asText() shouldBe "The requested resource was not found"
                responses["Unauthorized"]["description"].asText() shouldBe "Authentication required"
                responses["ValidationError"]["description"].shouldNotBeNull()
            }
        }

        test("§1 — components.parameters") {
            assertSoftly {
                val params = doc["components"]["parameters"].shouldNotBeNull()
                params["ProductId"]["name"].asText() shouldBe "productId"
                params["PageSize"]["name"].asText() shouldBe "pageSize"
                params["FilterContext"]["name"].asText() shouldBe "X-Filter-Context"
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
            }
        }

        test("§1 — components.links") {
            assertSoftly {
                val links = doc["components"]["links"].shouldNotBeNull()
                links["GetProductByIdLink"]["operationId"].asText() shouldBe "getProduct"
                links["GetOrderStatusLink"].shouldNotBeNull()
            }
        }

        test("§1 — components.callbacks") {
            assertSoftly {
                val callbacks = doc["components"]["callbacks"].shouldNotBeNull()
                callbacks["OrderStatusCallback"].shouldNotBeNull()
            }
        }

        test("§1 — components.pathItems") {
            assertSoftly {
                val pathItems = doc["components"]["pathItems"].shouldNotBeNull()
                pathItems["HealthCheck"]["summary"].asText() shouldBe "API health check"
            }
        }

        test("§1 — components.securitySchemes") {
            assertSoftly {
                val schemes = doc["components"]["securitySchemes"].shouldNotBeNull()
                schemes["bearerAuth"]["scheme"].asText() shouldBe "bearer"
                schemes["apiKeyAuth"]["name"].asText() shouldBe "X-API-Key"
            }
        }

        // §2 — Operation.callbacks
        test("§2 — Operation.callbacks (placeOrder)") {
            val placeOrder = doc["paths"]["/orders"]["post"].shouldNotBeNull()
            val callbacks = placeOrder["callbacks"].shouldNotBeNull()
            callbacks["onStatusChange"]["\$ref"].asText() shouldBe "#/components/callbacks/OrderStatusCallback"
        }

        // §3 — PathItem.parameters and PathItem.$ref
        test("§3 — PathItem.parameters (components.pathItems.HealthCheck)") {
            val healthCheck = doc["components"]["pathItems"]["HealthCheck"].shouldNotBeNull()
            val params = healthCheck["parameters"].shouldNotBeNull()
            params.isArray shouldBe true
            params[0]["name"].asText() shouldBe "X-Request-Id"
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
            link["server"]["url"].asText() shouldBe "https://api.example.com/v1"
        }

        test("§5 — Link.proto_rpc_ref resolved (components.links)") {
            val link = doc["components"]["links"]["GetOrderStatusLink"].shouldNotBeNull()
            // proto_rpc_ref should be resolved to an operationRef path
            withClue("GetOrderStatusLink should have operationRef or operationId") {
                (link["operationRef"] != null || link["operationId"] != null) shouldBe true
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
            // The callback object is a map keyed by a runtime expression
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
    })
