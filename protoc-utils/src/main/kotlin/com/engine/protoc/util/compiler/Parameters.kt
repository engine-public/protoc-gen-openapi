package com.engine.protoc.util.compiler

import kotlin.reflect.typeOf

public class Parameters(public val raw: String?) {
    public val tokenized: Map<String, List<String>> by lazy {
        raw?.split(",")?.map { p -> p.split("=", limit = 2) }?.groupBy({it.first()}, {it.last()}) ?: emptyMap()
    }

    public inline operator fun <reified T : Any?> get(option: String): T {
        return when(typeOf<T>()) {
            typeOf<Int>() -> tokenized[option]?.last()?.toInt() as T
            typeOf<List<Int>>() -> tokenized[option]?.map { it.toInt() } as T
            typeOf<String>() -> tokenized[option]?.last() as T
            typeOf<List<String>>() -> tokenized[option] as T
            typeOf<Boolean>() -> tokenized[option]?.last()?.toBoolean() as T
            typeOf<List<Boolean>>() -> tokenized[option]?.map { it.toBoolean() } as T
            else -> throw UnsupportedOperationException("Unsupported option type ${typeOf<String>()} for $option")
        }
    }
}
