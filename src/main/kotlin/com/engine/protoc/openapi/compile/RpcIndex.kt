package com.engine.protoc.openapi.compile

import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.api.AnnotationsProto

/**
 * Registry of all gRPC service methods that carry a `google.api.http` annotation, indexed by
 * their fully-qualified RPC reference string `"package.ServiceName#MethodName"`.
 *
 * Used to resolve `proto_rpc_ref` values in Link, PathItem, and Reference annotations to valid
 * RFC 3986 URI-references that point into the generated `#/paths` tree.
 */
internal class RpcIndex(protoFiles: List<FileDescriptorProtoWrapper>) {

    /** Maps "package.Service#Method" → [HttpBinding]. */
    private val byRef: Map<String, HttpBinding>

    init {
        val map = LinkedHashMap<String, HttpBinding>()
        for (file in protoFiles) {
            val pkg = file.proto.`package` ?: ""
            for (service in file.services) {
                val svcName = service.name?.value ?: continue
                for (method in service.methods) {
                    val methodName = method.name?.value ?: continue
                    val httpRule = method.options
                        ?.findExtension(AnnotationsProto.http)?.value
                        ?: continue
                    val binding = httpRule.primaryBinding() ?: continue
                    val ref = if (pkg.isEmpty()) "$svcName#$methodName" else "$pkg.$svcName#$methodName"
                    map[ref] = binding
                }
            }
        }
        byRef = map
    }

    fun findBinding(rpcRef: String): HttpBinding? = byRef[rpcRef]
}
