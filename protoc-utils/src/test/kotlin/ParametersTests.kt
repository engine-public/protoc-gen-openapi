import com.engine.protoc.util.compiler.Parameters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ParametersTests :
    FunSpec({

        // ---------------------------------------------------------------------------
        // tokenized
        // ---------------------------------------------------------------------------

        context("tokenized") {
            test("null raw produces empty map") {
                Parameters(null).tokenized.shouldBeEmpty()
            }

            test("single key=value pair") {
                Parameters("key=value").tokenized["key"] shouldBe listOf("value")
            }

            test("multiple distinct options") {
                val params = Parameters("key1=value1,key2=value2")
                params.tokenized["key1"] shouldBe listOf("value1")
                params.tokenized["key2"] shouldBe listOf("value2")
            }

            test("repeated key accumulates values in order") {
                val params = Parameters("tag=a,tag=b,tag=c")
                params.tokenized["tag"] shouldBe listOf("a", "b", "c")
            }

            test("option without equals sign maps key to itself") {
                // split("=", limit=2) on "flag" gives ["flag"]; first==last=="flag"
                Parameters("flag").tokenized["flag"] shouldBe listOf("flag")
            }

            test("option with empty value produces empty string entry") {
                Parameters("key=").tokenized["key"] shouldBe listOf("")
            }

            test("missing key returns null") {
                Parameters("key=value").tokenized["other"] shouldBe null
            }

            test("value containing equals sign is preserved after first equals") {
                // split("=", limit=2) stops after first delimiter
                Parameters("key=a=b").tokenized["key"] shouldBe listOf("a=b")
            }
        }

        // ---------------------------------------------------------------------------
        // typed get<T> — happy path for each supported type
        // ---------------------------------------------------------------------------

        context("typed access") {
            test("get<String> returns last value for key, operator access") {
                Parameters("name=hello")["name"] shouldBe "hello"
            }

            test("get<String> returns last value for key, typed access") {
                Parameters("name=hello").get<String>("name") shouldBe "hello"
            }

            test("get<String> with repeated key returns last value") {
                Parameters("name=first,name=last").get<String>("name") shouldBe "last"
            }

            test("get<List<String>> returns all values for key") {
                Parameters("tag=a,tag=b,tag=c").get<List<String>>("tag") shouldBe listOf("a", "b", "c")
            }

            test("get<List<String>> with single value returns single-element list") {
                Parameters("tag=only").get<List<String>>("tag") shouldBe listOf("only")
            }

            test("get<Int> parses integer value") {
                Parameters("count=42").get<Int>("count") shouldBe 42
            }

            test("get<List<Int>> parses all integer values") {
                Parameters("nums=1,nums=2,nums=3").get<List<Int>>("nums") shouldBe listOf(1, 2, 3)
            }

            test("get<Boolean> true") {
                Parameters("flag=true").get<Boolean>("flag") shouldBe true
            }

            test("get<Boolean> false") {
                Parameters("flag=false").get<Boolean>("flag") shouldBe false
            }

            test("get<List<Boolean>> parses all boolean values") {
                Parameters("flags=true,flags=false,flags=true").get<List<Boolean>>("flags") shouldBe
                    listOf(true, false, true)
            }

            test("unsupported type throws UnsupportedOperationException") {
                shouldThrow<UnsupportedOperationException> {
                    Parameters("x=3.14").get<Double>("x")
                }
            }
        }

        // ---------------------------------------------------------------------------
        // Missing key — each type returns null when the option is absent.
        // ---------------------------------------------------------------------------

        context("missing key returns null for each supported type") {
            val params = Parameters("x=y")

            test("get<String> for absent key returns null") {
                params.get<String>("missing").shouldBeNull()
            }

            test("get<Int> for absent key returns null") {
                params.get<Int>("missing").shouldBeNull()
            }

            test("get<Boolean> for absent key returns null") {
                params.get<Boolean>("missing").shouldBeNull()
            }

            test("get<List<String>> for absent key returns null") {
                params.get<List<String>>("missing").shouldBeNull()
            }

            test("get<List<Int>> for absent key returns null") {
                params.get<List<Int>>("missing").shouldBeNull()
            }

            test("get<List<Boolean>> for absent key returns null") {
                params.get<List<Boolean>>("missing").shouldBeNull()
            }
        }
    })
