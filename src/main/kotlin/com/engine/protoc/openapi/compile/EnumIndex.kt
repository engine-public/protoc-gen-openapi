package com.engine.protoc.openapi.compile

import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper

/**
 * Registry of all proto enum types reachable in the compilation request, indexed by their
 * fully-qualified type name (e.g. `.example.Status` or `.example.Order.Status`).
 *
 * Top-level enums and enums nested inside messages (at any depth) are both included.
 */
internal class EnumIndex(protoFiles: List<FileDescriptorProtoWrapper>) {

    private val byTypeName: MutableMap<String, EnumDescriptorProtoWrapper> = LinkedHashMap()
    private val packageByTypeName: MutableMap<String, String> = LinkedHashMap()

    init {
        for (file in protoFiles) {
            val pkg = file.proto.`package`
            for (enumWrapper in file.enumTypes) {
                index(enumWrapper, pkg, pkg)
            }
            for (msgWrapper in file.messageTypes) {
                indexMessageEnums(msgWrapper, pkg, pkg)
            }
        }
    }

    private fun index(
        wrapper: EnumDescriptorProtoWrapper,
        qualifiedPrefix: String,
        protoPackage: String,
    ) {
        val qualifiedName =
            if (qualifiedPrefix.isEmpty()) wrapper.proto.name else "$qualifiedPrefix.${wrapper.proto.name}"
        byTypeName[".$qualifiedName"] = wrapper
        packageByTypeName[".$qualifiedName"] = protoPackage
    }

    private fun indexMessageEnums(
        msg: DescriptorProtoWrapper,
        qualifiedPrefix: String,
        protoPackage: String,
    ) {
        val msgQualified =
            if (qualifiedPrefix.isEmpty()) msg.proto.name else "$qualifiedPrefix.${msg.proto.name}"
        for (enumWrapper in msg.enumTypes) {
            index(enumWrapper, msgQualified, protoPackage)
        }
        for (nested in msg.nestedTypes) {
            indexMessageEnums(nested, msgQualified, protoPackage)
        }
    }

    fun find(typeName: String): EnumDescriptorProtoWrapper? = byTypeName[typeName]

    fun simpleNameOf(typeName: String): String = typeName.substringAfterLast('.')

    fun packageOf(typeName: String): String = packageByTypeName[typeName] ?: ""
}
