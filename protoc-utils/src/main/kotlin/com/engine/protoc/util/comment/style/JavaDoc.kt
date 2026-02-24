package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style

/**
 * A JavaDoc-style multi-line comment. (/** */)
 */
public class JavaDoc(
    newlineAfterOpeningFence: Boolean = true,
    newlineBeforeClosingFence: Boolean = true,
    newlineAfterClosingFence: Boolean = true,
) : Style(
    openingFence = "/**",
    closingFence = (if (newlineBeforeClosingFence) " " else "") + "*/",
    newlineAfterOpeningFence = newlineAfterOpeningFence,
    newlineBeforeClosingFence = newlineBeforeClosingFence,
    newlineAfterClosingFence = newlineAfterClosingFence,
    lineMapper = { index, line ->
        when {
            index == 0 && !newlineAfterOpeningFence -> " $line"
            else -> " * $line"
        }
    },
) {
    public companion object;

    public object Parser : CommentParser<JavaDoc>() {
        override fun tryParse(ctx: ParseContext): Result<JavaDoc> = fail("TODO")
    }
}
