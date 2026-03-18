package com.engine.protoc.util.extensions

import com.engine.protoc.util.file.SourceCodeInfoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Returns the [DescriptorProtos.SourceCodeInfo.Location] whose path exactly matches [path], or
 * null if none exists.
 *
 * Protoc emits one location entry per syntax element, keyed by a path of field-number/index pairs
 * (see [Locatable.path]).  Entries exist for both leaf nodes (e.g. a single field) and their
 * enclosing constructs (e.g. the message that contains them), so an exact-length match is required
 * to avoid returning a parent location instead of the intended element.
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
