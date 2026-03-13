package com.engine.protoc.util

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.file.LocationWrapper

public abstract class AbstractLocatable(
    public override val path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : Locatable {
    public val location: LocationWrapper? by lazy {
        file.sourceCodeInfo?.findLocation(this)
    }
}
