package com.engine.protoc.util.compiler

import com.google.protobuf.compiler.PluginProtos

/**
 * Wraps the protoc compiler version sent in a [com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest].
 * [toString] renders it as `major.minor.patch` or `major.minor.patch-suffix` when a suffix is present.
 */
@JvmInline
public value class VersionWrapper(public val proto: PluginProtos.Version) {
    public override fun toString(): String = "${proto.major}.${proto.minor}.${proto.patch}${if (proto.hasSuffix()) "-${proto.suffix}" else ""}"
}
