import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PetStoreTest :
    FunSpec({

        val request = PetStoreTest::class.java.getResourceAsStream("/code-generator-request.binpb")
            ?: error("code-generator-request.binpb not found in test resources")

        val response = ProtocGenOpenAPI.from(request).compile()
        val generatedFile = response.fileList.find { it.name == "swagger.PetService.openapi.json" }
        val mapper = ObjectMapper()
        val json = mapper.readTree(generatedFile!!.content)

        test("has no validation errors") {
            response.hasError() shouldBe false
        }

        test("generates a swagger.PetService.openapi.json file") {
            generatedFile shouldNotBe null
        }

        test("openapi version") {
            json.get("openapi").asText() shouldBe "3.1.0"
        }

        test("info") {
            val info = json.get("info")
            info.get("title").asText() shouldBe "Swagger Petstore - OpenAPI 3.1"
            info.get("version").asText() shouldBe "1.0.10"
            info.get("summary").asText() shouldBe "Pet Store 3.1"
        }

        test("servers") {
            val servers = json.get("servers")
            servers.size() shouldBe 1
            servers[0].get("url").asText() shouldBe "/api/v31"
        }

        test("paths contains PUT /pet") {
            val pet = json.get("paths")?.get("/pet")
            pet shouldNotBe null
            val put = pet!!.get("put")
            put shouldNotBe null
            put.get("operationId").asText() shouldBe "updatePet"
            put.get("tags")[0].asText() shouldBe "pet"
        }

        test("paths contains POST /pet") {
            val post = json.get("paths")?.get("/pet")?.get("post")
            post shouldNotBe null
            post!!.get("operationId").asText() shouldBe "addPet"
        }

        test("paths contains GET /pet/{value} with path parameter") {
            val paths = json.get("paths")
            // GetPetById uses Int64Value, path is /pet/{value}
            val getPetPath = paths.get("/pet/{value}")
            getPetPath shouldNotBe null
            val get = getPetPath!!.get("get")
            get shouldNotBe null
            val params = get.get("parameters")
            params shouldNotBe null
            params[0].get("name").asText() shouldBe "value"
            params[0].get("in").asText() shouldBe "path"
            params[0].get("required").asBoolean() shouldBe true
            params[0].get("schema").get("type").asText() shouldBe "integer"
            params[0].get("schema").get("format").asText() shouldBe "int64"
        }

        test("components/schemas contains Pet") {
            val schemas = json.get("components")?.get("schemas")
            schemas shouldNotBe null
            val pet = schemas!!.get("Pet")
            pet shouldNotBe null
            // Pet has engine.protoc.openapi.message annotation with required: ["name", "photo_urls"]
            // deep-merged on top of auto-generated schema
            val required = pet!!.get("required")
            required shouldNotBe null
        }

        test("components/schemas contains Category, Tag, PetDetails") {
            val schemas = json.get("components")?.get("schemas")
            schemas shouldNotBe null
            schemas!!.get("Category") shouldNotBe null
            schemas.get("Tag") shouldNotBe null
            schemas.get("PetDetails") shouldNotBe null
        }

        test("components/securitySchemes from file annotation") {
            val ss = json.get("components")?.get("securitySchemes")
            ss shouldNotBe null
            ss!!.get("petstore_auth").get("type").asText() shouldBe "oauth2"
            ss.get("mutual_tls").get("type").asText() shouldBe "mutualTLS"
            ss.get("api_key").get("type").asText() shouldBe "apiKey"
        }

        test("webhooks from file annotation") {
            val webhooks = json.get("webhooks")
            webhooks shouldNotBe null
            webhooks!!.get("newPet") shouldNotBe null
        }

        test("security on updatePet") {
            val security = json.get("paths")?.get("/pet")?.get("put")?.get("security")
            security shouldNotBe null
            security!![0].get("petstore_auth") shouldNotBe null
        }
    })
