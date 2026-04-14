package com.engine.protoc.openapi.compile.json

import com.engine.protoc.openapi.Schema
import com.engine.protoc.openapi.SchemaObject
import com.engine.protoc.openapi.model.Discriminator
import com.engine.protoc.openapi.model.ExternalDocumentation
import com.engine.protoc.openapi.model.XML
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Value

// ---------------------------------------------------------------------------
// Schema (oneof bool | SchemaObject)
// ---------------------------------------------------------------------------

internal fun Schema.toJson(ctx: JsonContext): JsonNode =
    when (schemaValueCase) {
        Schema.SchemaValueCase.BOOLEAN -> ctx.mapper.nodeFactory.booleanNode(boolean)
        Schema.SchemaValueCase.OBJECT -> `object`.toJson(ctx)
        else -> ctx.obj()
    }

// ---------------------------------------------------------------------------
// SchemaObject — serializes all JSON Schema 2020-12 + OpenAPI 3.1 keywords
// ---------------------------------------------------------------------------

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun SchemaObject.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()

    // ---- $ref (oneof ref_type) -------------------------------------------
    when (refTypeCase) {
        SchemaObject.RefTypeCase.URI_REF -> node.put("\$ref", uriRef)
        SchemaObject.RefTypeCase.PROTO_MESSAGE_REF -> node.put("\$ref", ctx.resolveProtoRef(protoMessageRef.typeUrl))
        else -> {}
    }

    // ---- JSON Schema $-prefixed keywords ---------------------------------
    if (hasId()) node.put("\$id", id)
    if (hasSchema()) node.put("\$schema", schema)
    if (hasAnchor()) node.put("\$anchor", anchor)
    if (hasDynamicRef()) node.put("\$dynamicRef", dynamicRef)
    if (hasDynamicAnchor()) node.put("\$dynamicAnchor", dynamicAnchor)
    if (defsMap.isNotEmpty()) {
        val defsNode = ctx.obj()
        for ((k, v) in defsMap) defsNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("\$defs", defsNode)
    }

    // ---- Metadata --------------------------------------------------------
    if (hasTitle()) node.put("title", title)
    if (hasDescription()) node.put("description", description)
    if (hasDeprecated()) node.put("deprecated", deprecated)
    if (hasReadOnly()) node.put("readOnly", readOnly)
    if (hasWriteOnly()) node.put("writeOnly", writeOnly)
    if (hasDefault()) node.set<JsonNode>("default", default.toJson(ctx))
    if (hasExample()) node.set<JsonNode>("example", example.toJson(ctx))
    if (examplesList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (ex in examplesList) arr.add(ex.toJson(ctx))
        node.set<JsonNode>("examples", arr)
    }

    // ---- Type ------------------------------------------------------------
    when (typeList.size) {
        0 -> {}

        1 -> node.put("type", typeList[0])

        else -> {
            val arr = ctx.mapper.createArrayNode()
            for (t in typeList) arr.add(t)
            node.set<JsonNode>("type", arr)
        }
    }
    if (hasFormat()) node.put("format", format)

    // ---- Enum / Const ---------------------------------------------------
    if (enumList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (v in enumList) arr.add(v.toJson(ctx))
        node.set<JsonNode>("enum", arr)
    }
    if (hasConst()) node.set<JsonNode>("const", const.toJson(ctx))

    // ---- Numbers --------------------------------------------------------
    if (hasMultipleOf()) node.put("multipleOf", multipleOf)
    if (hasMaximum()) node.put("maximum", maximum)
    if (hasExclusiveMaximum()) node.put("exclusiveMaximum", exclusiveMaximum)
    if (hasMinimum()) node.put("minimum", minimum)
    if (hasExclusiveMinimum()) node.put("exclusiveMinimum", exclusiveMinimum)

    // ---- Strings --------------------------------------------------------
    if (hasMaxLength()) node.put("maxLength", maxLength)
    if (hasMinLength()) node.put("minLength", minLength)
    if (hasPattern()) node.put("pattern", pattern)
    if (hasContentEncoding()) node.put("contentEncoding", contentEncoding)
    if (hasContentMediaType()) node.put("contentMediaType", contentMediaType)
    if (hasContentSchema()) node.set<JsonNode>("contentSchema", contentSchema.toJson(ctx))

    // ---- Arrays ---------------------------------------------------------
    if (hasItems()) node.set<JsonNode>("items", items.toJson(ctx))
    if (prefixItemsList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in prefixItemsList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("prefixItems", arr)
    }
    if (hasContains()) node.set<JsonNode>("contains", contains.toJson(ctx))
    if (hasMinContains()) node.put("minContains", minContains)
    if (hasMaxContains()) node.put("maxContains", maxContains)
    if (hasMaxItems()) node.put("maxItems", maxItems)
    if (hasMinItems()) node.put("minItems", minItems)
    if (hasUniqueItems()) node.put("uniqueItems", uniqueItems)
    if (hasUnevaluatedItems()) node.set<JsonNode>("unevaluatedItems", unevaluatedItems.toJson(ctx))

    // ---- Objects --------------------------------------------------------
    if (propertiesMap.isNotEmpty()) {
        val propsNode = ctx.obj()
        for ((k, v) in propertiesMap) propsNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("properties", propsNode)
    }
    if (patternPropertiesMap.isNotEmpty()) {
        val ppNode = ctx.obj()
        for ((k, v) in patternPropertiesMap) ppNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("patternProperties", ppNode)
    }
    when (additionalPropertiesTypeCase) {
        SchemaObject.AdditionalPropertiesTypeCase.ADDITIONAL_PROPERTIES_ALLOWED ->
            node.put("additionalProperties", additionalPropertiesAllowed)

        SchemaObject.AdditionalPropertiesTypeCase.ADDITIONAL_PROPERTIES_SCHEMA ->
            node.set<JsonNode>("additionalProperties", additionalPropertiesSchema.toJson(ctx))

        else -> {}
    }
    if (hasPropertyNames()) node.set<JsonNode>("propertyNames", propertyNames.toJson(ctx))
    if (requiredList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (r in requiredList) arr.add(r)
        node.set<JsonNode>("required", arr)
    }
    if (hasMaxProperties()) node.put("maxProperties", maxProperties)
    if (hasMinProperties()) node.put("minProperties", minProperties)
    if (hasUnevaluatedProperties()) {
        node.set<JsonNode>("unevaluatedProperties", unevaluatedProperties.toJson(ctx))
    }

    // ---- Composition / Application ---------------------------------------
    if (allOfList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in allOfList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("allOf", arr)
    }
    if (anyOfList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in anyOfList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("anyOf", arr)
    }
    if (oneOfList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in oneOfList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("oneOf", arr)
    }
    if (hasNot()) node.set<JsonNode>("not", not.toJson(ctx))
    if (hasIfSchema()) node.set<JsonNode>("if", ifSchema.toJson(ctx))
    if (hasThenSchema()) node.set<JsonNode>("then", thenSchema.toJson(ctx))
    if (hasElseSchema()) node.set<JsonNode>("else", elseSchema.toJson(ctx))

    // ---- OpenAPI extensions to JSON Schema ------------------------------
    if (hasDiscriminator()) node.set<JsonNode>("discriminator", discriminator.toJson(ctx))
    if (hasXml()) node.set<JsonNode>("xml", xml.toJson(ctx))
    if (hasExternalDocs()) node.set<JsonNode>("externalDocs", externalDocs.toJson(ctx))
    extensionsMap.putExtensionsInto(node, ctx)

    return node
}

// ---------------------------------------------------------------------------
// Supporting model types used in schemas
// ---------------------------------------------------------------------------

internal fun Discriminator.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (propertyName.isNotEmpty()) node.put("propertyName", propertyName)
    if (mappingMap.isNotEmpty()) {
        val mapNode = ctx.obj()
        for ((k, v) in mappingMap) mapNode.put(k, v)
        node.set<JsonNode>("mapping", mapNode)
    }
    return node
}

internal fun XML.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasName()) node.put("name", name)
    if (hasNamespace()) node.put("namespace", namespace)
    if (hasPrefix()) node.put("prefix", prefix)
    if (hasAttribute()) node.put("attribute", attribute)
    if (hasWrapped()) node.put("wrapped", wrapped)
    return node
}

internal fun ExternalDocumentation.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasDescription()) node.put("description", description)
    node.put("url", url)
    return node
}
