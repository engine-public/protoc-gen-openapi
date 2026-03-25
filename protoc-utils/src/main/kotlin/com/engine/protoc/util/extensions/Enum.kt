package com.engine.protoc.util.extensions

public inline fun <reified T : Enum<T>> findEnumValue(
    name: String,
    matchCase: Boolean = false,
): T =
    enumValues<T>().firstOrNull {
        it.name.equals(name, !matchCase)
    } ?: throw NoSuchElementException("`$name` is not a valid value in ${enumValues<T>().joinToString(prefix = "[", postfix = "]", separator = ", ") { "`${it.name}`" }}")
