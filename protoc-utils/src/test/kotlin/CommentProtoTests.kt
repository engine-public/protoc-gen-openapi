import com.engine.protoc.util.comment.Comment
import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.utils.RegisteredExtensions
import engine.protoc.utils.UnregisteredExtensions
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class CommentProtoTests: FunSpec({

    val registry = ExtensionRegistry.newInstance().apply {
        RegisteredExtensions.registerAllExtensions(this)
        // do not add UnregisteredExtensions here!
    }

    val cgreq = this::class.java.getResourceAsStream("/code-generator-request.binpb")
        .shouldNotBeNull()
        .use { input ->
            PluginProtos.CodeGeneratorRequest.parseFrom(input, registry)
        }
        .wrap()

    val file = cgreq.sourceFileDescriptors.find { it.name == "engine/protoc/utils/comments.proto" }.shouldNotBeNull()

    /**
     * Some comments are just dropped by protoc, and we have no way to extract them.
     * This test looks for any of these comments and fails if they appear.
     * This would indicate an update to the protoc compiler made it possible to extract
     * them, and the code should be updated to allow it!
     */
    test("ensure no missed comments") {
        assertSoftly {
            file.proto.sourceCodeInfo.locationList
                .forEach {
                    withClue("span is ${it.wrap().span}") {
                        it.leadingComments shouldNotContain "**SENTINEL**"
                        it.trailingComments shouldNotContain "**SENTINEL**"
                        it.leadingDetachedCommentsList.forEach { comment ->
                            comment shouldNotContain "**SENTINEL**"
                        }
                    }
                }
        }
    }

    context("get syntax comments (including file header)") {
        val syntax = file
            .syntax.shouldNotBeNull()
            .location.shouldNotBeNull()

        var i = 0
        data class TestData(val expected: String, val index: Int = i++)

        val leadingDetachedComments = listOf(
            TestData("""
                        |header comment
                        |second line of header comment.
                    """.trimMargin()),
            TestData("""
                        |Multiline comment
                        | with varying whitespace indent
                        |  on a few different lines
                    """.trimMargin()),
            TestData("JavaDoc comment"),
            TestData("Allen Holub, Enough Rope, Rule 29"),
            TestData("""
                        |Multi-line for
                        |non-indenting
                        |editors
                        |Allen Holub, Enough Rope, Rule 31.
                    """.trimMargin()),
            TestData("""
                        |Block Comment Frame
                        |
                        |with multiple lines
                    """.trimMargin()),
            TestData("Block Comment Frame"),
            TestData("Block Comment Frame"),
            TestData("""
                        |multiple consecutive single line
                        |with top and bottom padding
                    """.trimMargin()),
            TestData("Framed Single Line"),
            TestData(""),
        )

        assertSoftly {
            withClue("leading comments") {
                syntax.leadingComments.shouldNotBeNull().cleaned shouldBe "comment attached to syntax"
            }
            withClue("trailing comments") {
                syntax.trailingComments.shouldNotBeNull().cleaned shouldBe "trailing comment attached to syntax"
            }
            withClue("mismatch between test set comment count and the proto file, did you update the proto and forget to update the test?") {
                i shouldBe syntax.leadingDetachedComments.size
            }
        }

        withData<TestData>(
            {
                val commentLines = it.expected.split("\n")
                val trailer = if (commentLines.size < 2) { "" } else { commentLines.first() + "[...]" }
                "leading detached comment ${it.index}: '${commentLines.first()}$trailer'"
            },
            leadingDetachedComments
        ) {
            syntax.leadingDetachedComments[it.index].shouldNotBeNull().cleaned shouldBe it.expected
        }
    }

    test("get package comments") {
        val pkg = file.`package`.shouldNotBeNull().location.shouldNotBeNull()
        assertSoftly {
            pkg.apply {
                leadingDetachedComments.shouldBeEmpty()

            }
        }
    }

    context("get dependency (import) comments") {
        val imports = file.dependenciesByName

        data class TestData(val file: String, val leadingComments: String? = null, val trailingComments: String? = null, val leadingDetachedComments: List<String> = emptyList())

        val expectedImports = listOf(
            TestData("google/protobuf/empty.proto", "comment on import of google/protobuf/empty.proto", "trailing comment on the next line", emptyList()),
            TestData("google/protobuf/any.proto", "comment on import of google/protobuf/any.proto"),
            TestData("google/protobuf/wrappers.proto", "comment on unused import of google/protobuf/wrappers.proto", leadingDetachedComments = listOf("random extra comment")),
            TestData("google/protobuf/duration.proto", "single line c-style comment on same line as import"),
            TestData("engine/protoc/utils/to_be_imported.proto", "here to test and evaluate how downstream dependencies appear"),
            TestData("engine/protoc/utils/registered_extensions.proto"),
            TestData("engine/protoc/utils/unregistered_extensions.proto"),
        )

        assertSoftly {
            withClue("mismatch between test set import count and the proto file, did you update the proto and forget to update the test?") {
                expectedImports.size shouldBe imports.size
            }
        }

        withData<TestData>(
            { it.file },
            expectedImports
        ) { test ->
            assertSoftly {
                withClue("comment exists") {
                    imports[test.file].shouldNotBeNull().location.shouldNotBeNull()
                }

                withClue("leading comments") {
                    imports[test.file].shouldNotBeNull().location.shouldNotBeNull().leadingComments?.cleaned shouldBe test.leadingComments
                }

                withClue("trailing comments") {
                    imports[test.file].shouldNotBeNull().location.shouldNotBeNull().trailingComments?.cleaned shouldBe test.trailingComments
                }

                withClue("leading detached comments") {
                    imports[test.file].shouldNotBeNull().location.shouldNotBeNull().leadingDetachedComments.map(Comment::cleaned) shouldBe test.leadingDetachedComments
                }
            }
        }
    }

    context("file option comments") {
        val options = file.options.shouldNotBeNull()

        assertSoftly {
            withClue("deprecated") {
                options.deprecated.shouldNotBeNull().location.shouldNotBeNull().leadingComments.shouldNotBeNull().cleaned shouldBe "comment on deprecated option"
            }
            withClue("java_multiple_files") {
                options.javaMultipleFiles.shouldNotBeNull().location.shouldNotBeNull().leadingComments.shouldNotBeNull().cleaned shouldBe "comment on java_multiple_files option"
            }

            withClue("unregistered_example") {
                options.findExtension(UnregisteredExtensions.unregisteredExample).shouldBeNull()
                file
                    .sourceCodeInfo.shouldNotBeNull()
                    .findLocationByPath(DescriptorProtos.FileDescriptorProto.OPTIONS_FIELD_NUMBER, UnregisteredExtensions.unregisteredExample.number).shouldNotBeNull()
                    .leadingComments.shouldNotBeNull()
                    .cleaned shouldBe "comment on custom extension option that is NOT registered at deser time of the cgrec"
            }

            withClue("registered_example") {
                options
                    .findExtension(RegisteredExtensions.registeredExample).shouldNotBeNull()
                    .location.shouldNotBeNull()
                    .leadingComments.shouldNotBeNull()
                    .cleaned shouldBe "comment on custom extension option that is registered at deser time of the cgrec"
            }
        }
    }
})
