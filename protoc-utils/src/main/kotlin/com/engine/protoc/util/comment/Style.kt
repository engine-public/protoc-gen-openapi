package com.engine.protoc.util.comment

/**
 * A description of a comment style, used to reconstruct the original comment's form if necessary.
 */
public abstract class Style(
    /**
     * The opening of a comment block, for example /&#42;, /&#42;&#42;, or <!--
     */
    public val openingFence: String,

    /**
     * The closing fence of a comment block, for example &#42;/ or -->
     */
    public val closingFence: String,

    /**
     * If true, a newline will be inserted immediately after the opening fence in the result of [formatComment].
     */
    public val newlineAfterOpeningFence: Boolean,

    /**
     * If true, a newline will be inserted immediately before the closing fence in the result of [formatComment].
     */
    public val newlineBeforeClosingFence: Boolean,

    /**
     * If true, a newline will be inserted immediately before the closing fence in the result of [formatComment].
     */
    public val newlineAfterClosingFence: Boolean,

    /**
     * The function used to write individual lines of the input comment.
     */
    public val lineMapper: (lineIndex: Int, line: String) -> CharSequence,
) {

    public companion object;

    /**
     * Format a comment in this style.
     */
    public fun formatComment(comment: String): String =
        StringBuilder().apply {
            append(openingFence)
            if (newlineAfterOpeningFence) appendLine()
            val lines = comment.lines()
            lines
                .slice(0 until lines.lastIndex)
                .forEachIndexed { index, line ->
                    appendLine(lineMapper(index, line))
                }
            append(lineMapper(lines.lastIndex, lines.last()))
            if (newlineBeforeClosingFence) appendLine()
            append(closingFence)
            if (newlineAfterClosingFence) appendLine()
        }.toString()
}
