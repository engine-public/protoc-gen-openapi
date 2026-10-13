package com.engine.protoc.openapi.compile

import com.engine.protoc.openapi.Annotations
import com.engine.protoc.openapi.ProtocGenOpenAPI
import com.engine.protoc.openapi.ProtocGenOpenAPI.Options.ReferenceLinkTarget
import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.engine.protoc.util.markdown.MarkdownText
import com.engine.protoc.util.markdown.ReferenceElement
import com.engine.protoc.util.markdown.ReferenceIndex
import com.engine.protoc.util.markdown.ReferenceLinkFailure
import com.engine.protoc.util.markdown.ReferenceLinkMode
import com.engine.protoc.util.markdown.ReferenceLinkProcessor
import com.engine.protoc.util.markdown.ReferenceRendering
import com.engine.protoc.util.markdown.UnresolvedReferenceRendering
import com.engine.protoc.util.service.MethodDescriptorProtoWrapper
import com.engine.protoc.util.service.ServiceDescriptorProtoWrapper
import com.google.api.AnnotationsProto
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.slf4j.LoggerFactory
import java.util.IdentityHashMap

private val log = LoggerFactory.getLogger(Descriptions::class.java)

/**
 * Produces every human-readable string the plugin emits, from proto comments and annotations.
 *
 * Strings fall into two kinds:
 *
 *  - **CommonMark** (`description` fields) — [markdown] resolves reference links (`[Widget]`,
 *    `[WidgetService.GetWidget]`, `[text][google.rpc.Status]`) to anchors in the generated
 *    document, to inline code spans for elements with no anchor, or to configured
 *    `referenceLink` URLs.
 *  - **Plain text** (`summary`, `title`, OAuth scope descriptions) — [plain] and [summary]
 *    resolve references the same way, so bad references are still reported, then flatten the
 *    Markdown to text.
 *
 * Failures are reported per `resolveReferenceLinksMode` and drained by the compiler with
 * [drainFailures].  Anchors are only emitted for elements present in the document being built,
 * which the compiler declares with [beginDocument].
 */
internal class Descriptions(
    files: List<FileDescriptorProtoWrapper>,
    private val options: ProtocGenOpenAPI.Options,
    private val schemaKeyResolver: SchemaKeyResolver,
) {
    /** Where a reference to an element points in the OpenAPI document; equal targets are not ambiguous. */
    sealed interface Target {
        /** A message or enum, or a member of one — fields and enum values have no anchor of their own. */
        data class Schema(val typeName: String) : Target

        /** A service, addressable as its auto-generated tag. */
        data class Tag(val serviceFqn: String, val name: String) : Target

        /** An RPC, addressable as its operation when it has an HTTP binding (or `autoMapping` is on). */
        data class Operation(val serviceFqn: String, val operationId: String, val primaryTag: String?, val mapped: Boolean) : Target
    }

    // Descriptor proto → FQN of the element, recorded while indexing, so callers can scope a
    // comment by the descriptor that owns it.
    private val fqnByDescriptor = IdentityHashMap<Any, String>()

    private var isSchemaEmitted: (String) -> Boolean = { false }
    private var isServiceEmitted: (String) -> Boolean = { false }
    private var defaultScope: String = ""

    private val processor: ReferenceLinkProcessor<Target> =
        ReferenceLinkProcessor(
            index = ReferenceIndex(files, targetFor = ::targetFor),
            mode = options.resolveReferenceLinksMode,
            overrides = options.referenceLink,
            unresolvedRendering = UnresolvedReferenceRendering.CODE,
            render = ::render,
        )

    private val parser: Parser = Parser.builder().linkProcessor(processor).build()

    /**
     * Declare the document about to be built: [isSchemaEmitted] tells whether a type (`.pkg.Msg`)
     * is a `components/schemas` entry, and [isServiceEmitted] whether a service (`pkg.Svc`)
     * contributes operations to it.
     */
    fun beginDocument(
        isSchemaEmitted: (String) -> Boolean,
        isServiceEmitted: (String) -> Boolean,
    ) {
        this.isSchemaEmitted = isSchemaEmitted
        this.isServiceEmitted = isServiceEmitted
    }

    /**
     * Run [block] with [scopeFqn] as the scope for strings that don't name their own, so
     * annotation-supplied text resolves bare names against the annotated descriptor.
     */
    fun <R> withDefaultScope(
        scopeFqn: String,
        block: () -> R,
    ): R {
        val prior = defaultScope
        defaultScope = scopeFqn
        return try {
            block()
        } finally {
            defaultScope = prior
        }
    }

    /** The cleaned leading comment of [element], or `null` when it has none. */
    fun comment(element: AbstractLocatable?): String? = element?.location?.leadingComments?.cleaned?.takeIf { it.isNotBlank() }

    /**
     * [text] as a CommonMark `description` with reference links resolved under [scopeFqn].  Text
     * without references is returned unchanged; otherwise the document is re-rendered.
     */
    fun markdown(
        text: String,
        scopeFqn: String = defaultScope,
    ): String {
        if (options.resolveReferenceLinksMode == ReferenceLinkMode.NONE) return text
        val document = parse(text, scopeFqn)
        return if (processor.touched) MarkdownText.render(document) else text
    }

    /** [text] flattened to plain text for a field that can't hold Markdown, with references reported. */
    fun plain(
        text: String,
        scopeFqn: String = defaultScope,
    ): String = MarkdownText.toPlainText(parse(text, scopeFqn))

    /** The first sentence of [text] as plain text, with references reported. */
    fun summary(
        text: String,
        scopeFqn: String = defaultScope,
    ): String = MarkdownText.firstSentence(parse(text, scopeFqn))

    /** Parse [text] with reference links resolved under [scopeFqn]. */
    fun parse(
        text: String,
        scopeFqn: String = defaultScope,
    ): Node = processor.withScope(scopeFqn) { parser.parse(text) }

    /** Return and clear the reference-link failures recorded under `FAIL_ON_INVALID`. */
    fun drainFailures(): List<ReferenceLinkFailure> = processor.drainFailures()

    /** The FQN (without a leading dot) of the element [descriptor] wraps, or `""` when it isn't indexed. */
    fun scopeOf(descriptor: GeneratedMessageWrapper<*>?): String = descriptor?.let { fqnByDescriptor[it.proto] }.orEmpty()

    /** True when resolved RPC references link to operations, which then need a stable `operationId`. */
    val linksOperations: Boolean get() = options.referenceLinkTarget != ReferenceLinkTarget.NONE

    private fun targetFor(element: ReferenceElement): Target {
        fqnByDescriptor[element.descriptor.proto] = element.fqn
        return when (element.kind) {
            ReferenceElement.Kind.MESSAGE, ReferenceElement.Kind.ENUM -> Target.Schema(".${element.fqn}")
            ReferenceElement.Kind.FIELD, ReferenceElement.Kind.ENUM_VALUE -> Target.Schema(".${element.parent!!.fqn}")
            ReferenceElement.Kind.SERVICE -> Target.Tag(element.fqn, element.simpleName)
            ReferenceElement.Kind.RPC -> operationTarget(element)
        }
    }

    private fun operationTarget(rpc: ReferenceElement): Target.Operation {
        val parent = rpc.parent!!
        val service = parent.descriptor as ServiceDescriptorProtoWrapper
        val method = rpc.descriptor as MethodDescriptorProtoWrapper
        val serviceName = parent.simpleName
        val annotation = method.options?.findExtension(Annotations.method)?.value
            ?.takeIf { it.hasOperation() }?.operation
        val serviceTags = service.options?.findExtension(Annotations.service)?.value?.tagsList ?: emptyList()
        val autoTagName = if (options.autoTagServices) serviceName else null
        return Target.Operation(
            serviceFqn = parent.fqn,
            operationId = operationIdFor(annotation, serviceName, rpc.simpleName),
            primaryTag = primaryTagFor(autoTagName, serviceTags, annotation),
            mapped = options.autoMapping || method.options?.findExtension(AnnotationsProto.http)?.value != null,
        )
    }

    private fun render(target: Target): ReferenceRendering {
        val href = when (options.referenceLinkTarget) {
            ReferenceLinkTarget.NONE -> null
            ReferenceLinkTarget.REDOC -> redocHref(target)
            ReferenceLinkTarget.SWAGGER_UI -> swaggerHref(target)
        }
        if (href == null) log.debug("reference to {} has no anchor in this document; rendering as inline code", target)
        return href?.let { ReferenceRendering.Link(it) } ?: ReferenceRendering.Code
    }

    private fun redocHref(target: Target): String? =
        when (target) {
            is Target.Schema -> if (isSchemaEmitted(target.typeName)) "#tag/${schemaKeyResolver.buildPhaseKeyOf(target.typeName)}" else null
            is Target.Tag -> if (tagEmitted(target)) "#tag/${target.name}" else null
            is Target.Operation -> if (operationEmitted(target)) "#operation/${target.operationId}" else null
        }

    private fun swaggerHref(target: Target): String? =
        when (target) {
            // Swagger UI has no stable anchor for a component schema.
            is Target.Schema -> null

            is Target.Tag -> if (tagEmitted(target)) "#/${target.name}" else null

            is Target.Operation -> if (operationEmitted(target)) target.primaryTag?.let { "#/$it/${target.operationId}" } else null
        }

    private fun tagEmitted(target: Target.Tag): Boolean = options.autoTagServices && isServiceEmitted(target.serviceFqn)

    private fun operationEmitted(target: Target.Operation): Boolean = target.mapped && isServiceEmitted(target.serviceFqn)
}

/**
 * The OpenAPI `operationId` for an RPC: the explicit annotation value when set, otherwise a stable
 * `{ServiceName}_{MethodName}` derivation.  Shared by [PathsBuilder] (which emits it) and
 * [Descriptions] (which links to it) so the link and the rendered operation always agree.
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
