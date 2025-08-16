package com.engine.protoc.util.extensions

import com.engine.protoc.util.file.SourceCodeInfoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Utility function that finds Location instances that match the exact provided path.
 * Location information from the source file is stored in the [SourceCodeInfoWrapper] as a List<List<Int>>.
 * This locations list is somewhat recursive in that there can exist entries for location of a grouping construct that does not point at a leaf node in the syntax.
 */
public fun DescriptorProtos.SourceCodeInfo.findLocation(path: List<Int>): DescriptorProtos.SourceCodeInfo.Location? {
    locations@ for (location in this.locationList) {
        if (location.pathList.size != path.size) {
            continue
        }
        for (i in location.pathList.indices) {
            if (path[i] != location.pathList[i]) {
                continue@locations
            }
        }
        return location
    }
    return null
}

public fun DescriptorProtos.SourceCodeInfo.wrap(): SourceCodeInfoWrapper = SourceCodeInfoWrapper(this)
