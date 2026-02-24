package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style

/**
 * There is no comment, or the comment contains only whitespace
 */
public object Empty : Style(
    openingFence = "",
    closingFence = "",
    newlineAfterOpeningFence = false,
    newlineBeforeClosingFence = false,
    newlineAfterClosingFence = false,
    lineMapper = { _, _ -> "" },
) {

    public object Parser : CommentParser<Empty>() {
        override fun tryParse(ctx: ParseContext): Result<Empty> = if (ctx.cleanedContentLines.all(String::isEmpty)) Result.Success("", Empty) else fail("One or more lines have content.")
    }
}
