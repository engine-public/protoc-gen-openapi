package com.engine.protoc.util.file

import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.comment.Comment
import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style
import com.google.protobuf.DescriptorProtos

/**
 * A wrapper for [com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location] that provides high-level access to comments and span information.
 */
public class LocationWrapper(
    override val proto: DescriptorProtos.SourceCodeInfo.Location,
    commentParsers: List<CommentParser<out Style>> = CommentParser.DefaultParsers,
) : GeneratedMessageWrapper<DescriptorProtos.SourceCodeInfo.Location> {
    /** The path that identifies this location within the file descriptor tree. See [Locatable.path]. */
    public val path: List<Int> get() = proto.pathList

    /** The line/column range of this element in the original .proto source. */
    public val span: Span by lazy { Span.of(proto.spanList) }

    /**
     * The comment immediately preceding this element on its own line(s), or null if none.
     * This is what most documentation generators treat as the element's doc comment.
     */
    public val leadingComments: Comment? by lazy { if (proto.hasLeadingComments()) Comment.Companion.fromRaw(proto.getLeadingComments(), commentParsers) else null }

    /** The comment on the same line after this element (e.g. `string name = 1; // trailing`), or null. */
    public val trailingComments: Comment? by lazy { if (proto.hasTrailingComments()) Comment.Companion.fromRaw(proto.getTrailingComments(), commentParsers) else null }

    /**
     * Comments that appear between the previous element and this one but are not directly attached
     * to either.  Protoc emits these as "leading detached" comments; they are commonly used for
     * section headings or explanatory blocks that precede a group of declarations.
     */
    public val leadingDetachedComments: List<Comment> by lazy { proto.leadingDetachedCommentsList.map { Comment.Companion.fromRaw(it, commentParsers) } }
}
