package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.ReferenceLinkTarget
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.message.DescriptorProtoWrapper
import com.google.api.AnnotationsProto
import org.commonmark.node.Code
import org.commonmark.node.Link
import org.commonmark.parser.beta.LinkInfo
import org.commonmark.parser.beta.LinkProcessor
import org.commonmark.parser.beta.LinkResult
import org.commonmark.parser.beta.Scanner
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ReferenceLinkResolver::class.java)

/**
 * Resolves CommonMark reference-link syntax (`[Widget]`, `[catalog.v1.Widget]`,
 * `[WidgetService.GetWidget]`) inside proto leading comments to same-document anchors in the
 * generated OpenAPI document.  Both the shortcut form (`[label]`) and the full form
 * (`[display text][label]`) are recognized; in the full form the bracketed label is the lookup
 * key and the display text passes through unchanged.  Escaped brackets (`\[foo\]`) and inline
 * links (`[text](url)`) are left to the core CommonMark processor.
 *
 * Constructed once per output document and hooked into the parser as a [LinkProcessor] (see
 * [linkProcessor]); the per-comment scope (the FQN of the descriptor the comment is attached to)
 * flows in through [setCurrentScope], which the caller sets around each `parser.parse(...)` call.
 *
 * Unlike the Markdown generator's resolver, OpenAPI has only three addressable element kinds, and
 * their anchor formats are renderer-specific (see [ReferenceLinkTarget]):
 *
 *  - **messages / enums → component schemas.**  Only addressable under [ReferenceLinkTarget.REDOC]
 *    (Swagger UI has no schema anchors), and only because the compiler emits a `<SchemaDefinition>`
 *    section per schema whose anchor is `#tag/{key}`.  Fields and enum values have no anchor of
 *    their own, so a reference to one resolves to its containing schema.
 *  - **services → tags.**  Addressable only when `autoTagServices` is enabled (otherwise no
 *    service-named tag is emitted).  `#tag/{name}` (Redoc) or `#/{name}` (Swagger UI).
 *  - **RPCs → operations.**  `#operation/{operationId}` (Redoc) or `#/{primaryTag}/{operationId}`
 *    (Swagger UI — which therefore needs the operation to carry at least one tag).
 *
 * Anchors that a target cannot express (e.g. a schema reference in Swagger UI mode, or an
 * operation with no tag) are simply never indexed, so the reference resolves as
 * [Outcome.Unresolved], the brackets are stripped and the label is emitted as an inline code span
 * (e.g. `[Property]` → `` `Property` ``) with a warning, rather than leaking a raw `[label]` that a
 * docs UI would render literally.  Schema anchors are
 * computed from [schemaKeyResolver]'s build-phase key, which is stable for the whole run; the
 * `SIMPLIFIED_PACKAGE` strategy normalizes them to their final form in a post-build pass.
 */
internal class ReferenceLinkResolver(
    files: List<FileDescriptorProtoWrapper>,
    private val target: ReferenceLinkTarget,
    autoTagServices: Boolean,
    autoMapping: Boolean,
    private val schemaKeyResolver: SchemaKeyResolver,
) {
    /** Global qualified-name index — every dotted form of every linkable element. */
    private val qualified = AmbiguityAwareMap("qualified")

    /** Global short-name index for schemas (messages, enums) and services (tags). */
    private val globalShort = AmbiguityAwareMap("global short name")

    /** scopeFqn (message) → `fieldName → containing-schema href`. */
    private val fieldsByMessage = mutableMapOf<String, MutableMap<String, String>>()

    /** scopeFqn (enum) → `valueName → containing-schema href`. */
    private val valuesByEnum = mutableMapOf<String, MutableMap<String, String>>()

    /** scopeFqn (service) → `methodName → operation href`. */
    private val rpcsByService = mutableMapOf<String, MutableMap<String, String>>()

    /**
     * The descriptor FQN of the comment currently being parsed, set by the caller via
     * [setCurrentScope] / [clearCurrentScope] around each `parser.parse(...)` invocation.  Empty
     * string means "no scope", which disables bare sibling lookups but still allows qualified and
     * global short-name resolution.
     */
    private var currentScope: String = ""

    /**
     * Whether the most recent parse actually rewrote at least one reference link.  Callers use
     * this to leave comments that contain no resolvable references byte-for-byte unchanged, rather
     * than round-tripping every description through the CommonMark renderer.  Reset by
     * [setCurrentScope] and read via [consumeTouched].
     */
    private var touched: Boolean = false

    init {
        if (target != ReferenceLinkTarget.NONE) index(files, autoTagServices, autoMapping)
    }

    // -------------------------------------------------------------------------
    // Anchor formats (renderer-specific)
    // -------------------------------------------------------------------------

    /** Anchor for a component schema, or `null` when the target cannot address schemas. */
    private fun schemaHref(key: String): String? =
        when (target) {
            ReferenceLinkTarget.REDOC -> "#tag/$key"
            ReferenceLinkTarget.SWAGGER_UI -> null
            ReferenceLinkTarget.NONE -> null
        }

    /** Anchor for a service tag. */
    private fun tagHref(name: String): String? =
        when (target) {
            ReferenceLinkTarget.REDOC -> "#tag/$name"
            ReferenceLinkTarget.SWAGGER_UI -> "#/$name"
            ReferenceLinkTarget.NONE -> null
        }

    /** Anchor for an operation, or `null` when Swagger UI mode lacks a tag to nest under. */
    private fun operationHref(
        operationId: String,
        primaryTag: String?,
    ): String? =
        when (target) {
            ReferenceLinkTarget.REDOC -> "#operation/$operationId"
            ReferenceLinkTarget.SWAGGER_UI -> primaryTag?.let { "#/$it/$operationId" }
            ReferenceLinkTarget.NONE -> null
        }

    // -------------------------------------------------------------------------
    // Indexing
    // -------------------------------------------------------------------------

    private fun index(
        files: List<FileDescriptorProtoWrapper>,
        autoTagServices: Boolean,
        autoMapping: Boolean,
    ) {
        for (file in files) {
            val pkg = file.`package`?.value.orEmpty()
            val pkgPrefix = if (pkg.isEmpty()) "" else "$pkg."
            for (m in file.messageTypes) indexMessage(m, pkgPrefix, "")
            for (e in file.enumTypes) indexEnum(e, pkgPrefix, "")
            for (service in file.services) {
                indexService(service, pkgPrefix, autoTagServices, autoMapping)
            }
        }
    }

    private fun indexMessage(
        msg: DescriptorProtoWrapper,
        pkgPrefix: String,
        ancestorPrefix: String,
    ) {
        if (msg.options?.mapEntry?.value == true) return
        val short = msg.name?.value ?: return
        val dotted = if (ancestorPrefix.isEmpty()) short else "$ancestorPrefix.$short"
        val fqn = "$pkgPrefix$dotted"
        val href = schemaHref(schemaKeyResolver.buildPhaseKeyOf(".$fqn"))

        if (href != null) {
            globalShort.put(short, href, "message $fqn")
            qualified.put(dotted, href, "message $fqn")
            qualified.put(fqn, href, "message $fqn")

            // Fields and nested members have no anchor of their own; a reference to one points at
            // the containing schema.
            val fieldMap = fieldsByMessage.getOrPut(fqn) { mutableMapOf() }
            for (field in msg.fields) {
                val fname = field.name?.value ?: continue
                fieldMap[fname] = href
                qualified.put("$short.$fname", href, "field $fqn.$fname")
                if (ancestorPrefix.isNotEmpty()) qualified.put("$dotted.$fname", href, "field $fqn.$fname")
                qualified.put("$fqn.$fname", href, "field $fqn.$fname")
            }
        }

        for (n in msg.nestedTypes) indexMessage(n, pkgPrefix, dotted)
        for (e in msg.enumTypes) indexEnum(e, pkgPrefix, dotted)
    }

    private fun indexEnum(
        enum: EnumDescriptorProtoWrapper,
        pkgPrefix: String,
        ancestorPrefix: String,
    ) {
        val short = enum.name?.value ?: return
        val dotted = if (ancestorPrefix.isEmpty()) short else "$ancestorPrefix.$short"
        val fqn = "$pkgPrefix$dotted"
        val href = schemaHref(schemaKeyResolver.buildPhaseKeyOf(".$fqn")) ?: return

        globalShort.put(short, href, "enum $fqn")
        qualified.put(dotted, href, "enum $fqn")
        qualified.put(fqn, href, "enum $fqn")

        val valueMap = valuesByEnum.getOrPut(fqn) { mutableMapOf() }
        for (v in enum.values) {
            val vname = v.name?.value ?: continue
            valueMap[vname] = href
            qualified.put("$short.$vname", href, "enum value $fqn.$vname")
            if (ancestorPrefix.isNotEmpty()) qualified.put("$dotted.$vname", href, "enum value $fqn.$vname")
            qualified.put("$fqn.$vname", href, "enum value $fqn.$vname")
        }
    }

    private fun indexService(
        service: com.engine.protoc.util.service.ServiceDescriptorProtoWrapper,
        pkgPrefix: String,
        autoTagServices: Boolean,
        autoMapping: Boolean,
    ) {
        val sname = service.name?.value ?: return
        val sFqn = "$pkgPrefix$sname"
        val serviceTags = service.options?.findExtension(Annotations.service)?.value?.tagsList ?: emptyList()
        val autoTagName = if (autoTagServices) sname else null

        tagHrefForService(autoTagName)?.let { href ->
            globalShort.put(sname, href, "service $sFqn")
            qualified.put(sname, href, "service $sFqn")
            qualified.put(sFqn, href, "service $sFqn")
        }

        val rpcMap = rpcsByService.getOrPut(sFqn) { mutableMapOf() }
        for (method in service.methods) {
            val mname = method.proto.name ?: continue
            val hasHttp = method.options?.findExtension(AnnotationsProto.http)?.value != null
            // Only methods that become operations are linkable.
            if (!hasHttp && !autoMapping) continue
            val annotation = method.options?.findExtension(Annotations.method)?.value
                ?.takeIf { it.hasOperation() }?.operation
            val operationId = operationIdFor(annotation, sname, mname)
            val primaryTag = primaryTagFor(autoTagName, serviceTags, annotation)
            val href = operationHref(operationId, primaryTag) ?: continue
            rpcMap[mname] = href
            qualified.put("$sname.$mname", href, "rpc $sFqn.$mname")
            qualified.put("$sFqn.$mname", href, "rpc $sFqn.$mname")
        }
    }

    private fun tagHrefForService(autoTagName: String?): String? = autoTagName?.let { tagHref(it) }

    // -------------------------------------------------------------------------
    // Resolution
    // -------------------------------------------------------------------------

    private fun resolve(
        label: String,
        scopeFqn: String,
    ): Outcome {
        if (label.isEmpty()) return Outcome.Unresolved
        if ('.' in label) return qualified.lookup(label)

        var ambiguous: Outcome.Ambiguous? = null
        bareScopeOutcome(label, scopeFqn)?.let {
            if (it is Outcome.Resolved) return it
            if (it is Outcome.Ambiguous && ambiguous == null) ambiguous = it
        }
        globalShort.lookup(label).let {
            if (it is Outcome.Resolved) return it
            if (it is Outcome.Ambiguous && ambiguous == null) ambiguous = it
        }
        return ambiguous ?: Outcome.Unresolved
    }

    /** Bare sibling lookup against the comment's own scope (message fields, enum values, RPCs). */
    private fun bareScopeOutcome(
        label: String,
        scopeFqn: String,
    ): Outcome? {
        if (scopeFqn.isEmpty()) return null
        val href = fieldsByMessage[scopeFqn]?.get(label)
            ?: valuesByEnum[scopeFqn]?.get(label)
            ?: rpcsByService[scopeFqn]?.get(label)
            ?: return null
        return Outcome.Resolved(href)
    }

    /** Bind the FQN of the comment about to be parsed.  Pair with [clearCurrentScope]. */
    fun setCurrentScope(scopeFqn: String) {
        currentScope = scopeFqn
        touched = false
    }

    /** Reset to no scope.  See [setCurrentScope]. */
    fun clearCurrentScope() {
        currentScope = ""
    }

    /** `true` when the parse since the last [setCurrentScope] rewrote at least one reference link. */
    fun consumeTouched(): Boolean = touched

    // -------------------------------------------------------------------------
    // LinkProcessor hook
    // -------------------------------------------------------------------------

    val linkProcessor: LinkProcessor =
        LinkProcessor { linkInfo, scanner, _ -> processLink(linkInfo, scanner) }

    private fun processLink(
        linkInfo: LinkInfo,
        scanner: Scanner,
    ): LinkResult? {
        // Inline links (`[text](url)`) and images carry a destination/marker — leave to core.
        if (linkInfo.destination() != null) return LinkResult.none()
        if (linkInfo.marker() != null) return LinkResult.none()
        val rawLabel = linkInfo.label()
        val text = linkInfo.text()
        val key = if (rawLabel.isNullOrEmpty()) text else rawLabel
        if (key.isEmpty()) return LinkResult.none()

        return when (val outcome = resolve(key, currentScope)) {
            is Outcome.Resolved -> {
                touched = true
                LinkResult.wrapTextIn(Link(outcome.href, null), scanner.position())
            }

            is Outcome.Ambiguous -> {
                warn(key, "ambiguous; candidates: ${outcome.candidates.joinToString("; ")}")
                touched = true
                LinkResult.wrapTextIn(Link(outcome.href, null), scanner.position())
            }

            Outcome.Unresolved -> {
                warn(key, "no matching schema, tag, or operation anchor in compile scope")
                // Strip the brackets rather than leaving a raw `[label]` (which renders as literal
                // text in the docs UI).  The label is emitted as an inline code span so it still
                // reads as a referenced symbol — e.g. an unaddressable schema like `[Property]`
                // becomes `` `Property` `` instead of `[Property]`.
                touched = true
                LinkResult.replaceWith(Code(text), scanner.position())
            }
        }
    }

    private fun warn(
        label: String,
        reason: String,
    ) {
        log.warn(
            "reference-link [{}] in {} :: {} — {}; rendering as inline code",
            label,
            target,
            currentScope.ifEmpty { "(no scope)" },
            reason,
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Per-map `key → href` index that records every owner so collisions surface as ambiguous. */
    private inner class AmbiguityAwareMap(private val mapLabel: String) {
        private val hrefByKey = mutableMapOf<String, String>()
        private val candidatesByKey = mutableMapOf<String, MutableList<Pair<String, String>>>()

        fun put(
            key: String,
            href: String,
            owner: String,
        ) {
            val candidates = candidatesByKey.getOrPut(key) { mutableListOf() }
            if (candidates.any { it.first == owner && it.second == href }) return
            candidates += owner to href
            val prior = hrefByKey[key]
            if (prior != null && prior != href && log.isTraceEnabled) {
                log.trace(
                    "reference-link key '{}' in {} is ambiguous — '{}' collides with prior '{}'; keeping latest",
                    key,
                    mapLabel,
                    owner,
                    prior,
                )
            }
            hrefByKey[key] = href
        }

        fun lookup(key: String): Outcome {
            val href = hrefByKey[key] ?: return Outcome.Unresolved
            val distinct = candidatesByKey[key].orEmpty().map { it.second }.distinct()
            return if (distinct.size <= 1) {
                Outcome.Resolved(href)
            } else {
                Outcome.Ambiguous(href, candidatesByKey[key].orEmpty().map { "${it.first} → ${it.second}" })
            }
        }
    }

    private sealed class Outcome {
        data class Resolved(val href: String) : Outcome()
        data class Ambiguous(val href: String, val candidates: List<String>) : Outcome()
        object Unresolved : Outcome()
    }
}

/**
 * The OpenAPI `operationId` for an RPC: the explicit annotation value when set, otherwise a stable
 * `{ServiceName}_{MethodName}` derivation.  Shared by [PathsBuilder] (which emits it) and
 * [ReferenceLinkResolver] (which links to it) so the link and the rendered operation always agree.
 */
internal fun operationIdFor(
    annotation: com.engine.protoc.openapi.model.Operation?,
    serviceName: String,
    methodName: String,
): String = annotation?.takeIf { it.hasOperationId() }?.operationId ?: "${serviceName}_$methodName"

/**
 * The primary OAS tag for an operation — the first of (auto-tag service name, service-level tags,
 * annotation tags), or `null` when the operation carries no tag.  Mirrors the tag ordering in
 * [PathsBuilder.buildOperation].
 */
internal fun primaryTagFor(
    autoTagName: String?,
    serviceTags: List<String>,
    annotation: com.engine.protoc.openapi.model.Operation?,
): String? =
    buildList {
        autoTagName?.let { add(it) }
        addAll(serviceTags)
        annotation?.tagsList?.forEach { add(it) }
    }.distinct().firstOrNull()
