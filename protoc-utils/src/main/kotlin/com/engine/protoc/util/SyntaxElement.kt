package com.engine.protoc.util

import com.engine.protoc.util.file.LocationWrapper

/*
 * An element of the protobuf syntax along with its source location information.
 * If location information is available, it can be used to any comments applied to the element in the original source.
 */
public open class SyntaxElement<T>(public val value: T, public val location: LocationWrapper?)
