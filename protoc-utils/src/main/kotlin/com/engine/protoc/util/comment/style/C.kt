package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style
import com.sun.net.httpserver.Authenticator

/**
 * A C-Style multi-line comment. (/* */)
 */
public class C(
    newlineAfterOpeningFence: Boolean = false,
    newlineBeforeClosingFence: Boolean = true,
    newlineAfterClosingFence: Boolean = true,
) : Style(
    openingFence = "/*",
    closingFence = " */",
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

    public object Parser : CommentParser<C>() {
        override fun tryParse(ctx: ParseContext): Result<C> {
            if (ctx.rawCommonEdges.first.isNotBlank()) {
                return fail("A standard c-style comment should have no common left-edge.")
            }
            if (ctx.rawCommonEdges.second.isNotBlank()) {
                return fail("A standard c-style comment should have no common right-edge.")
            }
            return Result.Success(
                ctx.cleanedContentLines.joinToString("\n"),
                C(
                    newlineAfterOpeningFence = ctx.contentLineIndices.first > 0,
                    newlineBeforeClosingFence = ctx.contentLineIndices.last < ctx.rawCommentLines.lastIndex,
                    newlineAfterClosingFence = ctx.hasNewlineAfterClosingFence,
                ),
            )
        }
    }
}
