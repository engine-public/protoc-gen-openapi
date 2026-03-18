package com.engine.protoc.util.compiler

import kotlin.reflect.typeOf

/**
 * Parsed representation of the plugin parameter string passed to protoc via
 * `--openapi_out=key=value,key2=value2:outdir`.  Options are comma-separated `key=value` pairs;
 * a key may appear more than once to supply multiple values.
 */
public class Parameters(
    /** The raw, unparsed parameter string as received from protoc, or null if none was provided. */
    public val raw: String?,
) {
    /**
     * The tokenized options as a map from option name to the ordered list of values supplied for
     * that name.  If an option appears once, its list has one entry; if it appears multiple times
     * (e.g. `tag=a,tag=b`), all values are preserved in order.
     */
    public val tokenized: Map<String, List<String>> by lazy {
        raw?.split(",")?.map { p -> p.split("=", limit = 2) }?.groupBy({ it.first() }, { it.last() }) ?: emptyMap()
    }

    /** Returns the last value for [key] as a [String], or null if the option is absent. */
    public operator fun get(key: String): String? {
        return get<String>(key)
    }

    /**
     * Returns the option value(s) for [option] coerced to [T], or null if the option is absent.
     * Supported types: [String], [Int], [Boolean] and their [List] variants.
     * When a key appears multiple times, scalar types return the last value; list types return all
     * values in order.
     * @throws UnsupportedOperationException if [T] is not one of the supported types.
     */
    public inline operator fun <reified T> get(option: String): T? =
        when (typeOf<T>()) {
            typeOf<Int>() -> tokenized[option]?.last()?.toInt() as T?
            typeOf<List<Int>>() -> tokenized[option]?.map { it.toInt() } as T?
            typeOf<String>() -> tokenized[option]?.last() as T?
            typeOf<List<String>>() -> tokenized[option] as T?
            typeOf<Boolean>() -> tokenized[option]?.last()?.toBoolean() as T?
            typeOf<List<Boolean>>() -> tokenized[option]?.map { it.toBoolean() } as T?
            else -> throw UnsupportedOperationException("Unsupported option type ${typeOf<T>()} for $option")
        }
}
