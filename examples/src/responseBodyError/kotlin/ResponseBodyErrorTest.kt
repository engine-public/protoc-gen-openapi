import com.engine.protoc.openapi.ProtocGenOpenAPI
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Verifies that `response_body` or `body` annotations whose field name does not exist on the
 * corresponding message produce a compile error instead of a silently invalid spec.
 *
 * Before the fix, both cases could produce dangling ${'$'}refs (or silent empty schemas) because
 * PathsBuilder would silently skip collection when the field was not found, while the schema-
 * emission phase still tried to emit a ${'$'}ref to the uncollected type.
 */
class ResponseBodyErrorTest :
    FunSpec({

        val request = ResponseBodyErrorTest::class.java
            .getResourceAsStream("/code-generator-request.binpb")
            .shouldNotBeNull()
        val response = ProtocGenOpenAPI.from(request) {
            merge = false
            validateOutput = false
        }.compile()

        // ---- response_body -----------------------------------------------

        test("response_body with a nonexistent field name is a compile error") {
            response.hasError() shouldBe true
        }

        test("response_body error names the bad field") {
            response.error shouldContain "this_field_does_not_exist"
        }

        // ---- body --------------------------------------------------------

        test("body with a nonexistent field name is a compile error") {
            response.hasError() shouldBe true
        }

        test("body error names the bad field") {
            response.error shouldContain "this_body_field_does_not_exist"
        }

        // ---- shared ------------------------------------------------------

        test("no output files are generated when there are errors") {
            response.fileList.shouldBeEmpty()
        }
    })
