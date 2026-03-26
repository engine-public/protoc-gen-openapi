package com.engine.protoc.openapi.compile

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper

/**
 * Registry of all proto message types reachable in the compilation request, indexed by their
 * fully-qualified type name (e.g. `.swagger.Pet`).  Nested message types are included.
 */
internal class MessageIndex(protoFiles: List<FileDescriptorProtoWrapper>) {

    private val byTypeName: MutableMap<String, DescriptorProtoWrapper> = LinkedHashMap()

    init {
        for (file in protoFiles) {
            val pkg = file.proto.`package`
            for (wrapper in file.messageTypes) {
                index(wrapper, pkg)
            }
        }
    }

    private fun index(
        wrapper: DescriptorProtoWrapper,
        packagePrefix: String,
    ) {
        val fqn = if (packagePrefix.isEmpty()) {
            ".${wrapper.proto.name}"
        } else {
            ".$packagePrefix.${wrapper.proto.name}"
        }
        byTypeName[fqn] = wrapper
        val nestedPrefix = if (packagePrefix.isEmpty()) {
            wrapper.proto.name
        } else {
            "$packagePrefix.${wrapper.proto.name}"
        }
        for (nested in wrapper.nestedTypes) {
            index(nested, nestedPrefix)
        }
    }

    fun find(typeName: String): DescriptorProtoWrapper? = byTypeName[typeName]

    /** Returns the simple (unqualified) message name for a fully-qualified type name. */
    fun simpleNameOf(typeName: String): String = typeName.substringAfterLast('.')
}
