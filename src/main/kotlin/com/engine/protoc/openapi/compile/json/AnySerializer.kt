package com.engine.protoc.openapi.compile.json

import com.google.protobuf.Any
import com.google.protobuf.BoolValue
import com.google.protobuf.BytesValue
import com.google.protobuf.DoubleValue
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.StringValue
import com.google.protobuf.UInt32Value
import com.google.protobuf.UInt64Value
import com.google.protobuf.Value
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode

/**
 * Converts a `google.protobuf.Any` value to a [JsonNode].
 *
 * Handles all well-known scalar wrapper types and `google.protobuf.Value`/`Struct`/`ListValue`
 * directly.  Unknown type URLs produce [NullNode].
 *
 * Note: `Any.is()` requires backtick escaping because `is` is a Kotlin keyword.
 */
@Suppress("ComplexMethod")
internal fun Any.toJson(ctx: JsonContext): JsonNode =
    when {
        `is`(StringValue::class.java) ->
            ctx.mapper.nodeFactory.stringNode(unpack(StringValue::class.java).value)

        `is`(Int32Value::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(Int32Value::class.java).value)

        `is`(Int64Value::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(Int64Value::class.java).value)

        `is`(UInt32Value::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(UInt32Value::class.java).value)

        `is`(UInt64Value::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(UInt64Value::class.java).value)

        `is`(FloatValue::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(FloatValue::class.java).value)

        `is`(DoubleValue::class.java) ->
            ctx.mapper.nodeFactory.numberNode(unpack(DoubleValue::class.java).value)

        `is`(BoolValue::class.java) ->
            ctx.mapper.nodeFactory.booleanNode(unpack(BoolValue::class.java).value)

        `is`(BytesValue::class.java) ->
            ctx.mapper.nodeFactory.stringNode(unpack(BytesValue::class.java).value.toStringUtf8())

        `is`(Value::class.java) -> unpack(Value::class.java).toJson(ctx)

        else -> NullNode.getInstance()
    }

internal fun Value.toJson(ctx: JsonContext): JsonNode =
    when (kindCase) {
        Value.KindCase.NULL_VALUE -> NullNode.getInstance()

        Value.KindCase.BOOL_VALUE -> ctx.mapper.nodeFactory.booleanNode(boolValue)

        Value.KindCase.NUMBER_VALUE -> numberValue.let { n ->
            if (n == kotlin.math.floor(n) && n.isFinite() && n >= Long.MIN_VALUE.toDouble() && n <= Long.MAX_VALUE.toDouble()) {
                ctx.mapper.nodeFactory.numberNode(n.toLong())
            } else {
                ctx.mapper.nodeFactory.numberNode(n)
            }
        }

        Value.KindCase.STRING_VALUE -> ctx.mapper.nodeFactory.stringNode(stringValue)

        Value.KindCase.STRUCT_VALUE -> {
            val obj = ctx.obj()
            for ((k, v) in structValue.fieldsMap) obj.set(k, v.toJson(ctx))
            obj
        }

        Value.KindCase.LIST_VALUE -> {
            val arr = ctx.mapper.createArrayNode()
            for (v in listValue.valuesList) arr.add(v.toJson(ctx))
            arr
        }

        else -> NullNode.getInstance()
    }
