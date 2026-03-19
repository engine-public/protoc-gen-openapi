package com.engine.protoc.util.comment

import com.engine.protoc.util.comment.style.C
import com.engine.protoc.util.comment.style.Empty
import com.engine.protoc.util.comment.style.Framed
import com.engine.protoc.util.comment.style.Holub
import com.engine.protoc.util.comment.style.JavaDoc
import com.engine.protoc.util.comment.style.SingleLine
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Extracts comment content and its detected [Style] from the raw protoc output.
 */
public abstract class CommentParser<S : Style> {

    public companion object {
        /**
         * The list of built-in processes, pre-ordered to produce the most reliable parsing output.
         */
        public val DefaultParsers: List<CommentParser<out Style>> = listOf(
            Empty.Parser,
            Framed.Parser,
            Holub.Parser,
            JavaDoc.Parser,
            SingleLine.Parser,
            C.Parser,
        )
    }

    /**
     * The result of an attempt to parse a comment
     */
    public sealed interface Result<S : Style> {
        /**
         * A result indicated that comment parsing has failed.
         */
        public data class Failure<S : Style>(
            val type: KClass<out CommentParser<S>>,
            val reason: String,
        ) : Result<S>

        /**
         * A result indicating comment parsing succeeded, including the cleaned text and detected style.
         */
        public data class Success<S : Style>(
            public val cleaned: String,
            public val style: S,
        ) : Result<S>
    }

    /**
     * Attempt to parse the provided comment lines
     */
    public abstract fun tryParse(ctx: ParseContext): Result<S>

    /**
     * A caching abstraction over operations done against a raw comment.
     * ParseContexts can (and should) be reused across Parser invocations but are not guaranteed to be thread-safe.
     */
    public class ParseContext(
        rawComment: String,
    ) {
        public companion object {
            private val commentContentCharsPattern = Regex("""[a-zA-Z0-9]""")
            private val rightFrameEdgePattern = Regex(""".*?([^a-zA-Z0-9]+)$""")
            private val leftRawEdgePattern = Regex("""^([^a-zA-Z0-9]+).*$""")
            public fun of(rawComment: String): ParseContext = ParseContext(rawComment)
        }

        /**
         * The raw content string, broken into individual lines in a platform-independent way.
         * @see String.lines
         */
        public val rawCommentLines: List<String> by lazy { rawComment.lines() }

        /**
         * True if the closing fence is immediately followed by a newline.
         * In protoc, this ends up being represented as an empty line
         */
        public val hasNewlineAfterClosingFence: Boolean by lazy { if (rawCommentLines.isEmpty()) false else rawCommentLines.last().isEmpty() }

        /**
         * Identifies the last line index that has some sort of content, including frames
         */
        public val lastNonEmptyIndex: Int by lazy { rawCommentLines.size - rawCommentLines.takeLastWhile { it.isEmpty() }.size - 1 }

        /**
         * The range of lines in [rawCommentLines] that include comment content.
         * This range may include internal blank lines, but will not include blank lines at the beginning or end.
         * This may be an empty range if no content exists for the content.
         */
        public val contentLineIndices: IntRange by lazy {
            var firstContentLineIndex: Int? = null
            var lastContentLineIndex: Int? = null
            rawCommentLines.indices.forEach { i ->
                if (hasCommentContent(i)) {
                    if (firstContentLineIndex == null) {
                        firstContentLineIndex = i
                    }
                    lastContentLineIndex = i
                }
            }
            firstContentLineIndex?.let { it..lastContentLineIndex!! } ?: IntRange.EMPTY
        }

        /**
         * The raw, unprocessed output of protoc for the region of lines that contain comment content.
         * This section does not include any top or bottom framing or padding, but will include internal "empty" lines.
         */
        public val rawContentLines: List<String> by lazy {
            rawCommentLines.slice(contentLineIndices)
        }

        /**
         * The sanitized region of lines that include comment content.
         * May include internal blank lines.
         */
        public val cleanedContentLines: List<String> by lazy {
            contentLineIndices.map(::cleanedLine)
        }

        /**
         * Returns the substrings that are in common on the left and right sides of the comment content.
         * This is useful for identification of fences, padding, and other comment chrome.
         */
        public val rawCommonEdges: Pair<String, String> by lazy {
            when (rawContentLines.size) {
                // it's empty
                0 -> "" to ""

                // there are no lines to compare, use the regex to try to identify
                1 -> {
                    val l = leftRawEdgePattern.matchEntire(rawContentLines[0])
                        ?.let { it.groupValues[1] } ?: ""
                    val r = rightFrameEdgePattern.matchEntire(rawContentLines[0])
                        ?.let { it.groupValues[1] } ?: ""
                    l to r
                }

                // only two lines, compare them directly
                2 -> {
                    val l = rawContentLines.first().commonPrefixWith(rawContentLines.last())
                    val r = rawContentLines.first().commonSuffixWith(rawContentLines.last())
                    l to r
                }

                // 3+ lines, compare each line to the prior line
                else -> {
                    /*
                     * how do we determine where to start comparing prefixes?
                     * if it's a single line comment style, the first line is relevant if it includes content
                     * if it's a block style comment, the first line may not be relevant, but it's hard to know, since the fencing is handled differently by proto
                     * unfortunately... if we assume the second line is the real start, we can mess up intentional formatting.
                     * for example. consider a comment like the following...
                     */

                    // Comment #1
                    /*
                     * This is my first line of the comment:
                     * * and this is a bulleted list
                     * * of points i'd like to be preserved
                     */

                    // comes through as:
                    /*
                     | This is my first line of the comment:
                     | * and this is a bulleted list
                     | * of points i'd like to be preserved
                     */

                    /*
                     * In this case, we want to preserve the first line because we need it to know the second line's "* " isn't the prefix, it's " ".
                     */

                    // Comment #2:
                    /* first line
                     ** second line
                     ** third line
                     */

                    // comes through as:
                    /*
                     | first line
                     |* second line
                     |* third line
                     */

                    /*
                     * In this case, the intended behavior would be to evaluate the 2nd
                     * and 3rd lines for a common prefix, ignoring the first.  the common prefix is "* ".
                     */

                    /*
                     * Best we can do?
                     * if the first line and second share a prefix, start at the first line.
                     * if they don't, does the second
                     */

                    val firstContentIndexToBeCompared = if (rawContentLines.first().commonPrefixWith(rawContentLines[1]).isNotEmpty()) {
                        0
                    } else if (rawContentLines.first().startsWith(" ") && !rawContentLines[1].startsWith(" ")) {
                        1
                    } else {
                        0
                    }

                    rawContentLines
                        .slice(firstContentIndexToBeCompared..rawContentLines.lastIndex)
                        .fold(rawContentLines[firstContentIndexToBeCompared] to rawContentLines[firstContentIndexToBeCompared]) { acc, it ->
                            acc.first.commonPrefixWith(it) to acc.second.commonSuffixWith(
                                it,
                            )
                        }
                        .let {
                            val r = rightFrameEdgePattern.matchEntire(it.second)
                                ?.let { it.groupValues[1] } ?: ""
                            it.first to r
                        }
                }
            }
        }

        /**
         * Return the scrubbed and normalized version of the [rawCommentLines] represented by the provided index.
         */
        public fun cleanedLine(index: Int): String {
            return cleanedLineCache.computeIfAbsent(index) { i ->
                /*
                 * special casing for first line and fencing messing up left edge detection
                 */
                if (i == 0 && !rawContentLines[i].startsWith(rawCommonEdges.first) && rawContentLines[i].startsWith(" ")) {
                    return@computeIfAbsent rawContentLines[i].removeSuffix(rawCommonEdges.second).drop(1)
                }

                rawCommentLines[i]
                    .removePrefix(rawCommonEdges.first)
                    .removeSuffix(rawCommonEdges.second)
                    .let { if (hasCommentContent(i)) it else "" }
                    .trimEnd()
            }
        }
        private val cleanedLineCache = ConcurrentHashMap<Int, String>()

        /**
         * True if the provided index in [rawCommentLines] has at least one alpha-numeric character.
         */
        public fun hasCommentContent(index: Int): Boolean = commentContentCache.computeIfAbsent(index) { i -> rawCommentLines[i].contains(commentContentCharsPattern) }
        private val commentContentCache = ConcurrentHashMap<Int, Boolean>()
    }

    /**
     * Returns a Pre-formatted failure response including the Style
     */
    protected fun fail(reason: String): Result.Failure<S> = Result.Failure(this::class, reason)
}
