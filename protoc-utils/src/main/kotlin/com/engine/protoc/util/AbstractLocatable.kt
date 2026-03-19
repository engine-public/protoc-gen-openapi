package com.engine.protoc.util

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.file.LocationWrapper

/**
 * Base class for proto syntax elements that carry a [path] and can be looked up in the file's
 * source-code info to retrieve comments and span information.
 *
 * [location] is resolved lazily on first access so that wrapper construction remains cheap even
 * when source-code info is not needed.
 */
public abstract class AbstractLocatable(
    public override val path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : Locatable {
    /** The source location for this element, or null if the file has no source-code info. */
    public val location: LocationWrapper? by lazy {
        file.sourceCodeInfo?.findLocation(this)
    }
}
