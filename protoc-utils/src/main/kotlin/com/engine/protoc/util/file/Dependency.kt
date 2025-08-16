package com.engine.protoc.util.file

import com.engine.protoc.util.Locatable
import com.engine.protoc.util.SyntaxElement
import com.google.protobuf.DescriptorProtos

/**
 * Describes a dependency on another file.
 */
public class Dependency(name: String, location: LocationWrapper?): SyntaxElement<String>(name, location) {
    public companion object: Locatable {
        override val path: List<Int>
            get() = listOf(DescriptorProtos.FileDescriptorProto.DEPENDENCY_FIELD_NUMBER)

        public fun indexedPath(index: Int): List<Int> {
            return listOf(*path.toTypedArray(), index)
        }
    }
    // TODO add the file here
}
