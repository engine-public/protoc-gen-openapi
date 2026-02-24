package com.engine.protoc.util.file

import com.engine.protoc.util.AbstractGeneratedMessageWrapper
import com.engine.protoc.util.comment.Comment
import com.engine.protoc.util.comment.CommentParser
import com.engine.protoc.util.comment.Style
import com.google.protobuf.DescriptorProtos

/**
 * A wrapper for [com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location] that provides high-level access to comments and span information.
 */
public class LocationWrapper(
    proto: DescriptorProtos.SourceCodeInfo.Location,
    commentParsers: List<CommentParser<out Style>> = CommentParser.DefaultParsers,
) : AbstractGeneratedMessageWrapper<DescriptorProtos.SourceCodeInfo.Location>(proto) {
    public val path: List<Int> get() = proto.pathList
    public val span: Span by lazy { Span.of(proto.spanList) }
    public val leadingComments: Comment? by lazy { if (proto.hasLeadingComments()) Comment.Companion.fromRaw(proto.getLeadingComments(), commentParsers) else null }
    public val trailingComments: Comment? by lazy { if (proto.hasTrailingComments()) Comment.Companion.fromRaw(proto.getTrailingComments(), commentParsers) else null }
    public val leadingDetachedComments: List<Comment> by lazy { proto.leadingDetachedCommentsList.map { Comment.Companion.fromRaw(it, commentParsers) } }
}
