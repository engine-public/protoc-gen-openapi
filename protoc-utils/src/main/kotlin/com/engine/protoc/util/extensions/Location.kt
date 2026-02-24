package com.engine.protoc.util.extensions

import com.engine.protoc.util.file.LocationWrapper
import com.google.protobuf.DescriptorProtos

public fun DescriptorProtos.SourceCodeInfo.Location.wrap(): LocationWrapper = LocationWrapper(this)
