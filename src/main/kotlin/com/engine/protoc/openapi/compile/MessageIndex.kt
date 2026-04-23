package com.engine.protoc.openapi.compile

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper

/**
 * Registry of all proto message types reachable in the compilation request, indexed by their
 * fully-qualified type name (e.g. `.swagger.Pet`).  Nested message types are included.
 */
internal class MessageIndex(protoFiles: List<FileDescriptorProtoWrapper>) {

    private val byTypeName: MutableMap<String, DescriptorProtoWrapper> = LinkedHashMap()

    // The file-level proto package for each type name, used for schema namespace key computation.
    // Nested types share the package of the file they are declared in.
    private val packageByTypeName: MutableMap<String, String> = LinkedHashMap()

    init {
        for (file in protoFiles) {
            val pkg = file.proto.`package`
            for (wrapper in file.messageTypes) {
                index(wrapper, pkg, pkg)
            }
        }
    }

    private fun index(
        wrapper: DescriptorProtoWrapper,
        packagePrefix: String,
        protoPackage: String,
    ) {
        val qualifiedName =
            if (packagePrefix.isEmpty()) wrapper.proto.name else "$packagePrefix.${wrapper.proto.name}"
        byTypeName[".$qualifiedName"] = wrapper
        packageByTypeName[".$qualifiedName"] = protoPackage
        for (nested in wrapper.nestedTypes) {
            index(nested, qualifiedName, protoPackage)
        }
    }

    fun find(typeName: String): DescriptorProtoWrapper? = byTypeName[typeName]

    /** Returns the simple (unqualified) message name for a fully-qualified type name. */
    fun simpleNameOf(typeName: String): String = typeName.substringAfterLast('.')

    /** Returns the file-level proto package for a fully-qualified type name, or empty string. */
    fun packageOf(typeName: String): String = packageByTypeName[typeName] ?: ""
}
