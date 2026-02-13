package com.engine.protoc.util.compiler

import com.google.protobuf.compiler.PluginProtos

@JvmInline
public value class VersionWrapper(public val proto: PluginProtos.Version) {
    public override fun toString(): String = "${proto.major}.${proto.minor}.${proto.patch}${if (proto.hasSuffix()) "-${proto.suffix}" else ""}"
}
