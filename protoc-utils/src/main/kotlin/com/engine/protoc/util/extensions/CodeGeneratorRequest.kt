package com.engine.protoc.util.extensions

import com.engine.protoc.util.compiler.CodeGeneratorRequestWrapper
import com.google.protobuf.compiler.PluginProtos

public fun PluginProtos.CodeGeneratorRequest.wrap(): CodeGeneratorRequestWrapper = CodeGeneratorRequestWrapper(this)
