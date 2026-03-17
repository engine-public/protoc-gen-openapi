package com.engine.protoc.util.comment.style

import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style

/**
 * A boxed-in comment with a border around the content
 */
public class Framed(
    width: Int,
    frameTop: String = Defaults.frameTop(width),
    frameBottom: String = Defaults.frameBottom(width),
    leftFrameEdge: String = Defaults.LEFT_FRAME_EDGE,
    rightFrameEdge: String = Defaults.RIGHT_FRAME_EDGE,
    topPadding: Int = Defaults.TOP_PADDING,
    bottomPadding: Int = Defaults.BOTTOM_PADDING,
    leftPadding: Int = Defaults.LEFT_PADDING,
    rightPadding: Int = Defaults.RIGHT_PADDING,
    newlineAfterFrame: Boolean = Defaults.NEWLINE_AFTER_FRAME,
) : Style(
    openingFence = frameTop + (1..topPadding).joinToString(prefix = "\n", separator = "\n") {
        Defaults.frameLine(
            commentLine = "",
            width = width,
            leftFrameEdge = leftFrameEdge,
            leftPadding = leftPadding,
            rightFrameEdge = rightFrameEdge,
            rightPadding = rightPadding,
        )
    },
    closingFence = (1..bottomPadding).joinToString(postfix = "\n", separator = "\n") {
        Defaults.frameLine(
            commentLine = "",
            width = width,
            leftFrameEdge = leftFrameEdge,
            leftPadding = leftPadding,
            rightFrameEdge = rightFrameEdge,
            rightPadding = rightPadding,
        )
    } + frameBottom,
    newlineAfterOpeningFence = true,
    newlineBeforeClosingFence = false, // the individual linemapper adds a new line before we render the bottom frame
    newlineAfterClosingFence = newlineAfterFrame,
    lineMapper = { _, line ->
        Defaults.frameLine(
            commentLine = line,
            width = width,
            leftFrameEdge = leftFrameEdge,
            leftPadding = leftPadding,
            rightFrameEdge = rightFrameEdge,
            rightPadding = rightPadding,
        )
    },
) {
    public companion object;

    public object Defaults {
        public const val BOTTOM_PADDING: Int = 0
        public const val FRAME_CLOSING_FENCE: String = "*/"
        public const val FRAME_CLOSING_FENCE_PREFIX: String = " "
        public const val FRAME_FILL_CHARACTER: Char = '*'
        public const val FRAME_OPENING_FENCE: String = "/*"
        public const val LEFT_FRAME_EDGE: String = " *"
        public const val LEFT_PADDING: Int = 1
        public const val NEWLINE_AFTER_FRAME: Boolean = true
        public const val RIGHT_FRAME_EDGE: String = "*"
        public const val RIGHT_PADDING: Int = 1
        public const val TOP_PADDING: Int = 0

        /**
         * Render the top of a framed comment
         */
        public fun frameTop(
            width: Int,
            openingFence: String = Defaults.FRAME_OPENING_FENCE,
            fillCharacter: Char = Defaults.FRAME_FILL_CHARACTER,
        ): String = openingFence.padEnd(width, fillCharacter) + "\n"

        /**
         * Render the bottom of a framed comment
         */
        public fun frameBottom(
            width: Int,
            frameClosingPrefix: String = Defaults.FRAME_CLOSING_FENCE_PREFIX,
            closingFence: String = Defaults.FRAME_CLOSING_FENCE,
            fillCharacter: Char = Defaults.FRAME_FILL_CHARACTER,
        ): String = frameClosingPrefix + closingFence.padStart(width - frameClosingPrefix.length, fillCharacter)

        /**
         * Render an individual comment line of a framed comment
         */
        public fun frameLine(
            commentLine: String,
            width: Int,
            leftFrameEdge: String = Defaults.LEFT_FRAME_EDGE,
            leftPadding: Int = Defaults.LEFT_PADDING,
            rightFrameEdge: String = Defaults.RIGHT_FRAME_EDGE,
            rightPadding: Int = Defaults.RIGHT_PADDING,
        ): String =
            (
                leftFrameEdge +
                    "".padEnd(leftPadding) +
                    commentLine.padEnd(width - leftFrameEdge.length - leftPadding - rightPadding - rightFrameEdge.length) +
                    "".padEnd(rightPadding) +
                    rightFrameEdge
                )
    }

    public object Parser : CommentParser<Framed>() {

        override fun tryParse(ctx: ParseContext): Result<Framed> {
            // must be >1 line of comment content including one header and one footer
            if (ctx.lastNonEmptyIndex < 2) return fail("A framed comment must be at least 3 lines.")

            if (ctx.hasCommentContent(0)) {
                return fail("The header of a framed comment should not contain comment content")
            }

            if (ctx.hasCommentContent(ctx.lastNonEmptyIndex)) {
                return fail("The footer of a framed comment should not contain comment content")
            }

            if (ctx.rawCommonEdges.second.isBlank()) {
                return fail("Frames should have a right edge, but no consistent right-edge could be identified.")
            }

            val rightFrameEdge = ctx.rawCommonEdges.second.trim()
            val rightPadding = ctx.rawCommonEdges.second.length - rightFrameEdge.length
            val width = ctx.rawCommentLines[0].length + Defaults.FRAME_OPENING_FENCE.length

            val longestCleanedLine = ctx.cleanedContentLines.maxBy { it.length }

            // figure out total width and see if we need to add leading whitespace or not
            // if left + content + right is less than top - 2, add leading whitespace, maybe add trailing whitespace?
            val leftEdgeSize =
                width - longestCleanedLine.length - rightPadding - rightFrameEdge.length

            val (leftFrameEdge, leftPadding) = rightFrameEdge.reversed()
                .let { rightFrameReversed ->

                    // frame probably looks like some variation of... `* comment *`
                    if (rightFrameEdge.endsWith("*")) {
                        // looks like a holub... `** comment **`
                        if (rightFrameEdge.endsWith("**") && ctx.rawCommonEdges.first.startsWith(
                                "*",
                            )
                        ) {
                            rightFrameReversed to leftEdgeSize - rightFrameReversed.length
                        }
                        // there is room to steal padding from the right for an indent
                        else if (leftEdgeSize > rightFrameReversed.length + rightPadding) {
                            " $rightFrameReversed".let { it to leftEdgeSize - it.length }
                        }
                        // there isn't room for consistent spacing on left and right
                        else if (leftEdgeSize <= rightFrameReversed.length + rightPadding) {
                            when (rightPadding) {
                                // there is insufficient room for spacing
                                0, 1 -> rightFrameReversed to 0

                                2 -> rightFrameReversed to leftEdgeSize - rightFrameReversed.length

                                else -> " $rightFrameReversed".let { it to leftEdgeSize - it.length }
                            }
                        } else {
                            rightFrameReversed to leftEdgeSize - rightFrameReversed.length
                        }
                    } else if (leftEdgeSize == rightFrameReversed.length + rightPadding) {
                        // the frame is "probably" left-aligned and "probably" has whitespace consistent with the right side
                        rightFrameReversed to rightPadding
                    } else {
                        rightFrameReversed to leftEdgeSize - rightFrameReversed.length
                    }
                }

            // find any padding after the frame top and before the first comment content line
            val firstTopPaddingLineIndex = (1 until ctx.contentLineIndices.first)
                .firstOrNull { i ->
                    ctx.cleanedLine(i).isBlank()
                }

            // find any padding after the comment content and before the frame bottom
            val lastBottomPaddingLineIndex = (ctx.contentLineIndices.last until ctx.lastNonEmptyIndex)
                .lastOrNull { i ->
                    ctx.cleanedLine(i).isBlank()
                }

            // figure out if this is a single-line frame or a c-style frame
            val (openingFence, closingFence) = if (leftFrameEdge.trimStart().startsWith("//")) "//" to "//" else Defaults.FRAME_OPENING_FENCE to Defaults.FRAME_CLOSING_FENCE

            val frameTop = ctx.rawCommentLines
                .slice(0 until (firstTopPaddingLineIndex ?: ctx.contentLineIndices.first))
                .joinToString(separator = "\n", prefix = openingFence)

            val frameBottom = (((lastBottomPaddingLineIndex ?: ctx.contentLineIndices.last) + 1)..ctx.lastNonEmptyIndex)
                .joinToString("\n") { i ->
                    when (i) {
                        // is the last line of the frame
                        ctx.lastNonEmptyIndex -> {
                            // the bare minimum last line is whatever content was preserved + the closing fence
                            val min =
                                ctx.rawCommentLines[ctx.lastNonEmptyIndex] + closingFence

                            // if we're bigger than the frame, just dump the output
                            if (min.length > width) {
                                min
                            }
                            // the left edge is indented, first char of frame is *, and we're <= width
                            // if the first character isn't whitespace and isn't *, we're probably ok to just output it
                            else if (min[0] == '*' && leftFrameEdge[0] == ' ') {
                                leftFrameEdge + min
                            }
                            // otherwise, we need to pad it
                            else {
                                min
                            }
                        }

                        else -> {
                            // we're on a frame line that isn't padding (it's part of the frame bottom).
                            // try to strip off any common prefix and reconstruct the left edge.
                            // then pad to the best of our ability with whatever content is in the frame line.
                            val trimmedAndCleaned =
                                ctx.rawCommentLines[i].removePrefix(ctx.rawCommonEdges.first.trimEnd())
                            val leftEdgeTrimmed = leftFrameEdge.trimEnd()
                            leftEdgeTrimmed + "".padStart(
                                width - leftEdgeTrimmed.length - trimmedAndCleaned.length,
                                trimmedAndCleaned[0],
                            ) + trimmedAndCleaned
                        }
                    }
                }

            val topPadding = firstTopPaddingLineIndex?.let { ctx.contentLineIndices.first - it } ?: 0
            val bottomPadding = lastBottomPaddingLineIndex?.let { it - ctx.contentLineIndices.last } ?: 0

            return Result
                .Success(
                    ctx.cleanedContentLines.joinToString(separator = "\n"),
                    Framed(
                        width = width,
                        frameTop = frameTop,
                        frameBottom = frameBottom,
                        leftFrameEdge = leftFrameEdge,
                        rightFrameEdge = rightFrameEdge,
                        topPadding = topPadding,
                        bottomPadding = bottomPadding,
                        leftPadding = leftPadding,
                        rightPadding = rightPadding,
                        newlineAfterFrame = ctx.hasNewlineAfterClosingFence,
                    ),
                )
        }
    }
}
