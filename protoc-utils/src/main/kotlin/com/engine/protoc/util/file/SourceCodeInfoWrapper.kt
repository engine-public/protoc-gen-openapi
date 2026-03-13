package com.engine.protoc.util.file

import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.Locatable
import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.DescriptorProtos

/**
 * A Wrapper for [DescriptorProtos.SourceCodeInfo]:
 * * Gives wrapped access to the [DescriptorProtos.SourceCodeInfo.Location]s within the [DescriptorProtos.SourceCodeInfo]
 * * Provides a means to pull a given [LocationWrapper] by its path
 */
public class SourceCodeInfoWrapper(override val proto: DescriptorProtos.SourceCodeInfo) : GeneratedMessageWrapper<DescriptorProtos.SourceCodeInfo> {
    public val locations: List<LocationWrapper> by lazy { proto.locationList.map { it.wrap() } }
    private val locationsByPath: Map<List<Int>, LocationWrapper> by lazy { locations.associateBy { it.path } }

    public fun findLocationByPath(vararg id: Int): LocationWrapper? = locationsByPath[id.toList()]
    public fun findLocationByPath(path: List<Int>): LocationWrapper? = locationsByPath[path]
    public fun findLocation(locatable: Locatable): LocationWrapper? = locationsByPath[locatable.path]
}
