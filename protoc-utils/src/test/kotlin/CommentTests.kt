import com.engine.protoc.util.comment.Comment
import com.engine.protoc.util.comment.CommentParser
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class CommentTests: FunSpec({
    /**
     * A test description that wraps original intent, the output of protoc, and our attempts to make meaning of the mess.
     */
    data class TestCase(
        /**
         * A description of the test, used as the name of the test in KoTest
         */
        val description: String,

        /**
         * What the original comment looked like before protoc obliterated it
         */
        val source: String,

        /**
         * The value protoc would give the plugin
         */
        val protoc: String,

        /**
         * The value as it should be after we've cleaned up the mess
         */
        val expectedCleanedResult: String,
    ) {
        val comment by lazy { Comment.fromRaw(protoc, CommentParser.DefaultParsers) }
    }

    val testCases = listOf(
        TestCase(
            description = "single-line comment",
            source = """
                |// header comment
                |
            """.trimMargin(),
            protoc = """
                | header comment
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |header comment
            """.trimMargin(),
        ),
        TestCase(
            description = "multiple single-line comments",
            source = """
                |// header comment
                |// second line of header comment.
                |
            """.trimMargin(),
            protoc = """
                | header comment
                | second line of header comment.
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |header comment
                |second line of header comment.
            """.trimMargin(),
        ),
        TestCase(
            description = "multi-line c-style with varying whitespace",
            source = """
                |/*
                | * Multiline comment
                | *  with varying whitespace indent
                | *   on a few different lines
                | */
                |
            """.trimMargin(),
            protoc = """
                |
                | Multiline comment
                |  with varying whitespace indent
                |   on a few different lines
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Multiline comment
                | with varying whitespace indent
                |  on a few different lines
            """.trimMargin(),
        ),
        TestCase(
            description = "single-line JavaDoc comments",
            source = """
                |/**
                | * JavaDoc comment
                | */
            """.trimMargin(),
            protoc = """
                |*
                | JavaDoc comment
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |JavaDoc comment
            """.trimMargin(),
        ),
        TestCase(
            description = "c-style comment with the comment content on the first line",
            source = """
                |/* Allen Holub, Enough Rope, Rule 29
                | */
            """.trimMargin(),
            protoc = """
                | Allen Holub, Enough Rope, Rule 29
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Allen Holub, Enough Rope, Rule 29
            """.trimMargin(),
        ),
        TestCase(
            description = "multi-line c-style comment with left fencing",
            source = """
                |/* Multi-line for
                |** non-indenting
                |** editors
                |** Allen Holub, Enough Rope, Rule 31.
                |*/
            """.trimMargin(),
            protoc = """
                | Multi-line for
                |* non-indenting
                |* editors
                |* Allen Holub, Enough Rope, Rule 31.
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Multi-line for
                |non-indenting
                |editors
                |Allen Holub, Enough Rope, Rule 31.
            """.trimMargin(),
        ),
        TestCase(
            description = "c-style block comment, asterisk edges, padding and internal whitespace",
            source = """
                |/***********************
                | *                     *
                | * Block Comment Frame *
                | *                     *
                | * with multiple lines *
                | *                     *
                | ***********************/
                |
            """.trimMargin(),
            protoc = """
                |**********************
                |                     *
                | Block Comment Frame *
                |                     *
                | with multiple lines *
                |                     *
                |*********************
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Block Comment Frame
                |
                |with multiple lines
            """.trimMargin(),
        ),
        TestCase(
            description = "c-style block comment frame, pipe edges, padding",
            source = """
                |/*====================\
                ||                     |
                || Block Comment Frame |
                ||                     |
                |\====================*/
                |
            """.trimMargin(),
            protoc = """
                |====================\
                |                     |
                | Block Comment Frame |
                |                     |
                |\====================
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Block Comment Frame
            """.trimMargin(),
        ),
        TestCase(
            description = "c-style block comment frame, double top and bottom, irregular left, right top and bottom padding",
            source = """
                |/************************\
                ||************************|
                ||*                      *|
                ||*                      *|
                ||* Block Comment Frame  *|
                ||*                      *|
                ||*                      *|
                ||*                      *|
                ||************************|
                |\************************/
                |
            """.trimMargin(),
            protoc = """
                |***********************\
                ||************************|
                ||*                      *|
                ||*                      *|
                ||* Block Comment Frame  *|
                ||*                      *|
                ||*                      *|
                ||*                      *|
                ||************************|
                |\***********************
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |Block Comment Frame
            """.trimMargin(),
        ),
        TestCase(
            description = "c-style block comment with both fences on the same line",
            source = """/* single line c-style comment on same line as import */""",
            protoc = " single line c-style comment on same line as import ",
            expectedCleanedResult = "single line c-style comment on same line as import",
        ),
        TestCase(
            description = "single-line, multiple consecutive, padding on top and bottom top",
            source = """
                |//
                |// multiple consecutive single line
                |// with top and bottom padding
                |//
            """.trimMargin(),
            protoc = """
                |
                | multiple consecutive single line
                | with top and bottom padding
                |
                |
            """.trimMargin(),
            expectedCleanedResult = """
                |multiple consecutive single line
                |with top and bottom padding
            """.trimMargin(),
        ),
        TestCase(
            description = "framed single-line",
            source = """
                |//////////////////////////
                |//                      //
                |//  Framed Single Line  //
                |//                      //
                |//////////////////////////
                |
            """.trimMargin(),
            protoc = """
                |////////////////////////
                |                      //
                |  Framed Single Line  //
                |                      //
                |////////////////////////
                |
            """.trimMargin(),
            expectedCleanedResult = "Framed Single Line",
        )
    )

    context("Comment parsing tests") {
        withData<TestCase>(
            { it.description },
            testCases
        ) { testData ->
            println(testData)
            testData.comment.cleaned shouldBe testData.expectedCleanedResult
        }
    }
})
