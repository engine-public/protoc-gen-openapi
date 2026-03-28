package com.engine.protoc.openapi.compile.json

import com.engine.protoc.openapi.OpenAPI
import com.engine.protoc.openapi.Operation
import com.engine.protoc.openapi.model.APIKeySecurityScheme
import com.engine.protoc.openapi.model.Components
import com.engine.protoc.openapi.model.Contact
import com.engine.protoc.openapi.model.ExternalDocumentation
import com.engine.protoc.openapi.model.HTTPSecurityScheme
import com.engine.protoc.openapi.model.Info
import com.engine.protoc.openapi.model.License
import com.engine.protoc.openapi.model.MediaType
import com.engine.protoc.openapi.model.MutualTLSSecurityScheme
import com.engine.protoc.openapi.model.OAuthFlow
import com.engine.protoc.openapi.model.OAuthFlows
import com.engine.protoc.openapi.model.OAuthSecurityScheme
import com.engine.protoc.openapi.model.OpenIDConnectSecurityScheme
import com.engine.protoc.openapi.model.Parameter
import com.engine.protoc.openapi.model.ParameterOrReference
import com.engine.protoc.openapi.model.ParameterSchema
import com.engine.protoc.openapi.model.PathItem
import com.engine.protoc.openapi.model.PathItemOrReference
import com.engine.protoc.openapi.model.Reference
import com.engine.protoc.openapi.model.RequestBody
import com.engine.protoc.openapi.model.RequestBodyOrReference
import com.engine.protoc.openapi.model.ResponseObject
import com.engine.protoc.openapi.model.ResponseOrReference
import com.engine.protoc.openapi.model.Responses
import com.engine.protoc.openapi.model.SecurityRequirement
import com.engine.protoc.openapi.model.SecurityScheme
import com.engine.protoc.openapi.model.SecuritySchemeOrReference
import com.engine.protoc.openapi.model.Server
import com.engine.protoc.openapi.model.ServerVariable
import com.engine.protoc.openapi.model.Tag
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.Value

// ---------------------------------------------------------------------------
// Top-level OpenAPI document
// ---------------------------------------------------------------------------

/**
 * Serializes the fields of a file-level [OpenAPI] proto into [dest], deeply merging into any
 * values already present.  The `paths` section is built separately by
 * [com.engine.protoc.openapi.compile.PathsBuilder].
 */
internal fun OpenAPI.mergeInto(
    dest: ObjectNode,
    ctx: JsonContext,
) {
    if (openapi.isNotEmpty()) dest.put("openapi", openapi)
    if (hasInfo()) {
        val infoNode = dest.get("info") as? ObjectNode ?: ctx.obj().also {
            dest.set<JsonNode>("info", it)
        }
        with(ctx) { infoNode.deepMerge(info.toJson(ctx)) }
    }
    if (hasJsonSchemaDialect()) dest.put("jsonSchemaDialect", jsonSchemaDialect)
    if (serversList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in serversList) arr.add(s.toJson(ctx))
        dest.set<JsonNode>("servers", arr)
    }
    if (tagsList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (t in tagsList) arr.add(t.toJson(ctx))
        dest.set<JsonNode>("tags", arr)
    }
    if (hasExternalDocs()) dest.set<JsonNode>("externalDocs", externalDocs.toJson(ctx))
    if (hasComponents()) {
        val existing = dest.get("components") as? ObjectNode ?: ctx.obj().also {
            dest.set<JsonNode>("components", it)
        }
        with(ctx) { existing.deepMerge(components.toJson(ctx)) }
    }
    if (webhooksMap.isNotEmpty()) {
        val webhooksNode = ctx.obj()
        for ((k, v) in webhooksMap) webhooksNode.set<JsonNode>(k, v.toJson(ctx))
        val existing = dest.get("webhooks") as? ObjectNode ?: ctx.obj().also {
            dest.set<JsonNode>("webhooks", it)
        }
        with(ctx) { existing.deepMerge(webhooksNode) }
    }
    // Merge any explicit paths from the extension (rare but supported)
    if (hasPaths()) {
        val pathsNode = dest.get("paths") as? ObjectNode ?: ctx.obj().also {
            dest.set<JsonNode>("paths", it)
        }
        for ((k, v) in paths.pathsMap) {
            val pathItem = pathsNode.get(k) as? ObjectNode ?: ctx.obj().also {
                pathsNode.set<JsonNode>(k, it)
            }
            with(ctx) { pathItem.deepMerge(v.toJson(ctx)) }
        }
    }
    extensionsMap.putExtensionsInto(dest, ctx)
}

// ---------------------------------------------------------------------------
// Extensions
// ---------------------------------------------------------------------------

/** Writes each entry of an extensions map as a top-level key on [node], unwrapping values. */
private fun Map<String, Value>.putExtensionsInto(
    node: ObjectNode,
    ctx: JsonContext,
) {
    for ((key, value) in this) node.set<JsonNode>(key, value.toJson(ctx))
}

// ---------------------------------------------------------------------------
// Info / Contact / License
// ---------------------------------------------------------------------------

internal fun Info.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (title.isNotEmpty()) node.put("title", title)
    if (hasSummary()) node.put("summary", summary)
    if (hasDescription()) node.put("description", description)
    if (hasTermsOfService()) node.put("termsOfService", termsOfService)
    if (hasContact()) node.set<JsonNode>("contact", contact.toJson(ctx))
    if (hasLicense()) node.set<JsonNode>("license", license.toJson(ctx))
    if (version.isNotEmpty()) node.put("version", version)
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

internal fun Contact.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasName()) node.put("name", name)
    if (hasUrl()) node.put("url", url)
    if (hasEmail()) node.put("email", email)
    return node
}

internal fun License.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("name", name)
    if (hasIdentifier()) node.put("identifier", identifier)
    if (hasUrl()) node.put("url", url)
    return node
}

// ---------------------------------------------------------------------------
// Server / ServerVariable
// ---------------------------------------------------------------------------

internal fun Server.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("url", url)
    if (hasDescription()) node.put("description", description)
    if (variablesMap.isNotEmpty()) {
        val varNode = ctx.obj()
        for ((k, v) in variablesMap) varNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("variables", varNode)
    }
    return node
}

internal fun ServerVariable.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (enumList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (e in enumList) arr.add(e)
        node.set<JsonNode>("enum", arr)
    }
    node.put("default", default)
    if (hasDescription()) node.put("description", description)
    return node
}

// ---------------------------------------------------------------------------
// Tag
// ---------------------------------------------------------------------------

internal fun Tag.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("name", name)
    if (hasDescription()) node.put("description", description)
    if (hasExternalDocs()) node.set<JsonNode>("externalDocs", externalDocs.toJson(ctx))
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

// ---------------------------------------------------------------------------
// PathItem
// ---------------------------------------------------------------------------

internal fun PathItemOrReference.toJson(ctx: JsonContext): ObjectNode =
    when (typeCase) {
        PathItemOrReference.TypeCase.PATH_ITEM -> pathItem.toJson(ctx)
        PathItemOrReference.TypeCase.REFERENCE -> reference.toJson(ctx)
        else -> ctx.obj()
    }

internal fun PathItem.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasSummary()) node.put("summary", summary)
    if (hasDescription()) node.put("description", description)
    if (hasGet()) node.set<JsonNode>("get", get.toJson(ctx))
    if (hasPut()) node.set<JsonNode>("put", put.toJson(ctx))
    if (hasPost()) node.set<JsonNode>("post", post.toJson(ctx))
    if (hasDelete()) node.set<JsonNode>("delete", delete.toJson(ctx))
    if (hasOptions()) node.set<JsonNode>("options", options.toJson(ctx))
    if (hasHead()) node.set<JsonNode>("head", head.toJson(ctx))
    if (hasPatch()) node.set<JsonNode>("patch", patch.toJson(ctx))
    if (hasTrace()) node.set<JsonNode>("trace", trace.toJson(ctx))
    if (serversList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in serversList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("servers", arr)
    }
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

// ---------------------------------------------------------------------------
// Operation
// ---------------------------------------------------------------------------

internal fun Operation.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (tagsList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (t in tagsList) arr.add(t)
        node.set<JsonNode>("tags", arr)
    }
    if (hasSummary()) node.put("summary", summary)
    if (hasDescription()) node.put("description", description)
    if (hasExternalDocs()) node.set<JsonNode>("externalDocs", externalDocs.toJson(ctx))
    if (hasOperationId()) node.put("operationId", operationId)
    if (parametersList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (p in parametersList) arr.add(p.toJson(ctx))
        node.set<JsonNode>("parameters", arr)
    }
    if (hasRequestBody()) node.set<JsonNode>("requestBody", requestBody.toJson(ctx))
    if (hasResponses()) node.set<JsonNode>("responses", responses.toJson(ctx))
    if (securityList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in securityList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("security", arr)
    }
    if (serversList.isNotEmpty()) {
        val arr = ctx.mapper.createArrayNode()
        for (s in serversList) arr.add(s.toJson(ctx))
        node.set<JsonNode>("servers", arr)
    }
    if (hasDeprecated()) node.put("deprecated", deprecated)
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

// ---------------------------------------------------------------------------
// Parameter
// ---------------------------------------------------------------------------

internal fun ParameterOrReference.toJson(ctx: JsonContext): ObjectNode =
    when (typeCase) {
        ParameterOrReference.TypeCase.PARAMETER -> parameter.toJson(ctx)
        ParameterOrReference.TypeCase.REFERENCE -> reference.toJson(ctx)
        else -> ctx.obj()
    }

internal fun Parameter.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("name", name)
    node.put("in", `in`)
    if (hasDescription()) node.put("description", description)
    if (hasRequired()) node.put("required", required)
    if (hasDeprecated()) node.put("deprecated", deprecated)
    if (hasAllowEmptyValue()) node.put("allowEmptyValue", allowEmptyValue)
    if (hasSchema()) node.set<JsonNode>("schema", schema.schema.toJson(ctx))
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

// Unused but kept for potential future use
@Suppress("UnusedParameter", "unused")
internal fun ParameterSchema.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasSchema()) node.set<JsonNode>("schema", schema.toJson(ctx))
    return node
}

// ---------------------------------------------------------------------------
// RequestBody
// ---------------------------------------------------------------------------

internal fun RequestBodyOrReference.toJson(ctx: JsonContext): ObjectNode =
    when (typeCase) {
        RequestBodyOrReference.TypeCase.REQUEST_BODY -> requestBody.toJson(ctx)
        RequestBodyOrReference.TypeCase.REFERENCE -> reference.toJson(ctx)
        else -> ctx.obj()
    }

internal fun RequestBody.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasDescription()) node.put("description", description)
    if (contentMap.isNotEmpty()) {
        val contentNode = ctx.obj()
        for ((k, v) in contentMap) contentNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("content", contentNode)
    }
    if (hasRequired()) node.put("required", required)
    return node
}

internal fun MediaType.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasSchema()) node.set<JsonNode>("schema", schema.toJson(ctx))
    return node
}

// ---------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------

internal fun Responses.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasDefault()) node.set<JsonNode>("default", default.toJson(ctx))
    for ((code, resp) in codesMap) node.set<JsonNode>(code, resp.toJson(ctx))
    return node
}

internal fun ResponseOrReference.toJson(ctx: JsonContext): ObjectNode =
    when (typeCase) {
        ResponseOrReference.TypeCase.RESPONSE_OBJECT -> responseObject.toJson(ctx)
        ResponseOrReference.TypeCase.REFERENCE -> reference.toJson(ctx)
        else -> ctx.obj()
    }

internal fun ResponseObject.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("description", description)
    if (contentMap.isNotEmpty()) {
        val contentNode = ctx.obj()
        for ((k, v) in contentMap) contentNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("content", contentNode)
    }
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

// ---------------------------------------------------------------------------
// Security
// ---------------------------------------------------------------------------

internal fun SecurityRequirement.toJson(ctx: JsonContext): ObjectNode {
    // Flatten: map<string, SecurityRequirementValues>.namesMap → {"schemeName": ["scope1"]}
    val node = ctx.obj()
    for ((schemeName, values) in namesMap) {
        val arr = ctx.mapper.createArrayNode()
        for (v in values.valuesList) arr.add(v)
        node.set<JsonNode>(schemeName, arr)
    }
    return node
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

internal fun Components.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (securitySchemesMap.isNotEmpty()) {
        val ssNode = ctx.obj()
        for ((k, v) in securitySchemesMap) ssNode.set<JsonNode>(k, v.toJson(ctx))
        node.set<JsonNode>("securitySchemes", ssNode)
    }
    // schemas are merged in separately by SchemaBuilder
    return node
}

internal fun SecuritySchemeOrReference.toJson(ctx: JsonContext): ObjectNode =
    when (typeCase) {
        SecuritySchemeOrReference.TypeCase.SECURITY_SCHEME -> securityScheme.toJson(ctx)
        SecuritySchemeOrReference.TypeCase.REFERENCE -> reference.toJson(ctx)
        else -> ctx.obj()
    }

internal fun SecurityScheme.toJson(ctx: JsonContext): ObjectNode {
    val node = when (typeCase) {
        SecurityScheme.TypeCase.API_KEY -> apiKey.toJson(ctx)
        SecurityScheme.TypeCase.HTTP -> http.toJson(ctx)
        SecurityScheme.TypeCase.MUTUAL_TLS -> mutualTls.toJson(ctx)
        SecurityScheme.TypeCase.OAUTH2 -> oauth2.toJson(ctx)
        SecurityScheme.TypeCase.OPEN_ID_CONNECT -> openIdConnect.toJson(ctx)
        else -> ctx.obj()
    }
    extensionsMap.putExtensionsInto(node, ctx)
    return node
}

internal fun APIKeySecurityScheme.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("type", "apiKey")
    node.put("name", name)
    node.put("in", `in`)
    return node
}

internal fun HTTPSecurityScheme.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("type", "http")
    node.put("scheme", scheme)
    if (hasBearerFormat()) node.put("bearerFormat", bearerFormat)
    return node
}

@Suppress("UnusedParameter")
internal fun MutualTLSSecurityScheme.toJson(ctx: JsonContext): ObjectNode = ctx.obj().also { it.put("type", "mutualTLS") }

internal fun OAuthSecurityScheme.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("type", "oauth2")
    if (hasFlows()) node.set<JsonNode>("flows", flows.toJson(ctx))
    return node
}

internal fun OAuthFlows.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasImplicit()) node.set<JsonNode>("implicit", implicit.toJson(ctx))
    if (hasPassword()) node.set<JsonNode>("password", password.toJson(ctx))
    if (hasClientCredentials()) node.set<JsonNode>("clientCredentials", clientCredentials.toJson(ctx))
    if (hasAuthorizationCode()) node.set<JsonNode>("authorizationCode", authorizationCode.toJson(ctx))
    return node
}

internal fun OAuthFlow.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    if (hasAuthorizationUrl()) node.put("authorizationUrl", authorizationUrl)
    if (hasTokenUrl()) node.put("tokenUrl", tokenUrl)
    if (hasRefreshUrl()) node.put("refreshUrl", refreshUrl)
    if (scopesMap.isNotEmpty()) {
        val scopeNode = ctx.obj()
        for ((k, v) in scopesMap) scopeNode.put(k, v)
        node.set<JsonNode>("scopes", scopeNode)
    }
    return node
}

internal fun OpenIDConnectSecurityScheme.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    node.put("type", "openIdConnect")
    node.put("openIdConnectUrl", openidConnectUrl)
    return node
}

// ---------------------------------------------------------------------------
// Reference
// ---------------------------------------------------------------------------

internal fun Reference.toJson(ctx: JsonContext): ObjectNode {
    val node = ctx.obj()
    when (refTypeCase) {
        Reference.RefTypeCase.URI_REF -> node.put("\$ref", uriRef)
        Reference.RefTypeCase.PROTO_REF -> node.put("\$ref", ctx.resolveProtoRef(protoRef))
        else -> {}
    }
    if (hasSummary()) node.put("summary", summary)
    if (hasDescription()) node.put("description", description)
    return node
}
