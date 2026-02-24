package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style

/**
 * A Single-line comment. (//)
 * This is the format suggested in the Protobuf style-guides.
 * @link https://protobuf.dev/programming-guides/proto3/#adding-comments
 */
public object SingleLine : Style(
    openingFence = "", // the fence is on every line, not on the comment block
    closingFence = "",
    newlineAfterOpeningFence = false,
    newlineBeforeClosingFence = false,
    newlineAfterClosingFence = true, // single line comments must, by definition, end with a newline
    lineMapper = { _, line -> "// $line" },
) {
    public object Parser : CommentParser<SingleLine>() {
        override fun tryParse(ctx: ParseContext): Result<SingleLine> =
            when {
                !ctx.hasNewlineAfterClosingFence -> fail(
                    "A single-line comment must end in a newline.",
                )

                ctx.rawCommentLines.last().isNotEmpty() -> fail(
                    "The last line of a single-line comment must be a single newline.",
                )

                else -> Result.Success(
                    ctx.cleanedContentLines.joinToString(separator = "\n"),
                    SingleLine,
                )
            }
    }
}
