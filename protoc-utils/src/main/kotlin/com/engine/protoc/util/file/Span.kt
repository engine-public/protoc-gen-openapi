package com.engine.protoc.util.file

/**
 * The columns and lines of the original source proto file for a location.
 */
public data class Span(
    public val startingLine: Int,
    public val startingColumn: Int,
    public val endingLine: Int,
    public val endingColumn: Int,
) {
    public companion object Companion {
        /**
         * Constructs a [Span] from protoc's compact integer encoding.
         *
         * Protoc emits spans as either 3 or 4 integers:
         * - **3 integers** `[startLine, startCol, endCol]` — start and end are on the same line.
         * - **4 integers** `[startLine, startCol, endLine, endCol]` — start and end are on different lines.
         *
         * All values are zero-based.
         */
        public fun of(span: List<Int>): Span =
            when (span.size) {
                4 -> Span(span[0], span[1], span[2], span[3])
                3 -> Span(span[0], span[1], span[0], span[2])
                else -> throw IllegalStateException("Could not create a span from an array of size=`${span.size}`.  span=`${span.toList()}.  This is an error in the protoc-plugin.")
            }
    }
}
