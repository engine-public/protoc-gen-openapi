package com.engine.protoc.util.comment

/**
 * A code comment in it's "raw" form as mangled by protoc and a "cleaned" form that has attempted to remove additional comment fencing and other noise.
 */
public data class Comment(
    /**
     * The raw comment as it was received in the [com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location].
     */
    public val raw: String,

    /**
     * The comment content, with internal whitespace retained, but all other extra noise removed.
     */
    public val cleaned: String,
) {
    public companion object {

        /**
         * Attempt to parse and clean a [Comment] using the provided list of parsers,
         * or fail if we cannot parse it.
         */
        public fun fromRaw(
            /**
             * The raw string from protoc to parse into a cleaned comment and style
             */
            rawComment: String,
            /**
             * The list of parsers to attempt.
             * @see [CommentParser.Companion.DefaultParsers]
             */
            parsers: List<CommentParser<out Style>>,
        ): Comment {
            val ctx = CommentParser.ParseContext.of(rawComment)
            var lastFailure: CommentParser.Result<out Style>? = null
            for (parser in parsers) {
                when (val result = parser.tryParse(ctx)) {
                    is CommentParser.Result.Success -> return Comment(
                        raw = rawComment,
                        cleaned = result.cleaned,
                    )

                    is CommentParser.Result.Failure -> lastFailure = result
                }
            }
            throw IllegalArgumentException(lastFailure.toString())
        }
    }

    /**
     * Re-encodes the cleaned comment content in the given style.
     */
    public fun reconstruct(style: Style): String = style.formatComment(cleaned)
}
