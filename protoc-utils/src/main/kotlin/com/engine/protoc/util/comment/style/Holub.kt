package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style

/**
 * A C-style block comment style that uses a star prefix for each line to counteract old editors that didn't auto-indent block comments subsequent lines.
 */
public class Holub(
    newlineAfterOpeningFence: Boolean = false,
    newlineAfterClosingFence: Boolean = true,
): Style(
    openingFence = "/*",
    closingFence = "*/",
    newlineAfterOpeningFence = newlineAfterOpeningFence,
    newlineBeforeClosingFence = true,
    newlineAfterClosingFence = newlineAfterClosingFence,
    lineMapper = { _, line -> "** $line" }
) {
    public companion object;

    public object Parser : CommentParser<Holub>() {
        override fun tryParse(ctx: ParseContext): Result<Holub> {
            if (ctx.lastNonEmptyIndex < 1) return fail("Holub comments are at least 2 lines.")
            if (!ctx.hasCommentContent(ctx.lastNonEmptyIndex)) return fail("Holub comments have an empty footer.")
            if (ctx.rawCommonEdges.second.isNotBlank()) return fail("Holub comments aren't framed.")
            if (!ctx.rawCommonEdges.first.startsWith("*")) return fail("Holub comments have an unindented * on the left edge.")
            return Result.Success(ctx.cleanedContentLines.joinToString("\n"), Holub(newlineAfterOpeningFence = ctx.contentLineIndices.contains(0), newlineAfterClosingFence = ctx.contentLineIndices.contains(ctx.rawContentLines.lastIndex)))
        }
    }
}
