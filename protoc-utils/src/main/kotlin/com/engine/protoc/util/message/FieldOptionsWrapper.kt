package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Wrapper for [com.google.protobuf.DescriptorProtos.FieldOptions], exposing each standard
 * field-level option as a [com.engine.protoc.util.SyntaxElement].  Each property is null when
 * the corresponding option was not set on the field.
 */
public class FieldOptionsWrapper(
    proto: DescriptorProtos.FieldOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.FieldOptions>(proto, file, path) {

    /**
     * NOTE: ctype is deprecated. Use `features.(pb.cpp).string_type` instead.
     * The ctype option instructs the C++ code generator to use a different
     * representation of the field than it normally would.  See the specific
     * options below.  This option is only implemented to support use of
     * [ctype=CORD] and [ctype=STRING] (the default) on non-repeated fields of
     * type "bytes" in the open source release.
     */
    @Deprecated("Use features.(pb.cpp).string_type instead.")
    public val ctype: SyntaxElement<DescriptorProtos.FieldOptions.CType>? =
        (if (proto.hasCtype()) proto.ctype else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.CTYPE_FIELD_NUMBER, file)
        }

    /**
     * The packed option can be enabled for repeated primitive fields to enable
     * a more efficient representation on the wire. Rather than repeatedly
     * writing the tag and type for each element, the entire array is encoded as
     * a single length-delimited blob. In proto3, only explicit setting it to
     * false will avoid using packed encoding.  This option is prohibited in
     * Editions, but the `repeated_field_encoding` feature can be used to control
     * the behavior.
     */
    public val packed: SyntaxElement<Boolean>? =
        (if (proto.hasPacked()) proto.packed else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.PACKED_FIELD_NUMBER, file)
        }

    /**
     * The jstype option determines the JavaScript type used for values of the
     * field.  The option is permitted only for 64 bit integral and fixed types
     * (int64, uint64, sint64, fixed64, sfixed64).  A field with jstype JS_STRING
     * is represented as JavaScript string, which avoids loss of precision that
     * can happen when a large value is converted to a floating point JavaScript.
     * Specifying JS_NUMBER for the jstype causes the generated JavaScript code to
     * use the JavaScript "number" type.  The behavior of the default option
     * JS_NORMAL is implementation dependent.
     *
     * This option is an enum to permit additional types to be added, e.g.
     * goog.math.Integer.
     */
    public val jstype: SyntaxElement<DescriptorProtos.FieldOptions.JSType>? =
        (if (proto.hasJstype()) proto.jstype else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.JSTYPE_FIELD_NUMBER, file)
        }

    /**
     * Should this field be parsed lazily?  Lazy applies only to message-type
     * fields.  It means that when the outer message is initially parsed, the
     * inner message's contents will not be parsed but instead stored in encoded
     * form.  The inner message will actually be parsed when it is first accessed.
     *
     * This is only a hint.  Implementations are free to choose whether to use
     * eager or lazy parsing regardless of the value of this option.  However,
     * setting this option true suggests that the protocol author believes that
     * using lazy parsing on this field is worth the additional bookkeeping
     * overhead typically needed to implement it.
     *
     * This option does not affect the public interface of any generated code;
     * all method signatures remain the same.  Furthermore, thread-safety of the
     * interface is not affected by this option; const methods remain safe to
     * call from multiple threads concurrently, while non-const methods continue
     * to require exclusive access.
     *
     * Note that lazy message fields are still eagerly verified to check
     * ill-formed wireformat or missing required fields. Calling IsInitialized()
     * on the outer message would fail if the inner message has missing required
     * fields. Failed verification would result in parsing failure (except when
     * uninitialized messages are acceptable).
     */
    public val isLazy: SyntaxElement<Boolean>? =
        (if (proto.hasLazy()) proto.lazy else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.LAZY_FIELD_NUMBER, file)
        }

    /**
     * unverified_lazy does no correctness checks on the byte stream. This should
     * only be used where lazy with verification is prohibitive for performance
     * reasons.
     */
    public val unverifiedLazy: SyntaxElement<Boolean>? =
        (if (proto.hasUnverifiedLazy()) proto.unverifiedLazy else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.UNVERIFIED_LAZY_FIELD_NUMBER, file)
        }

    /**
     * Is this field deprecated?
     * Depending on the target platform, this can emit Deprecated annotations
     * for accessors, or it will be completely ignored; in the very least, this
     * is a formalization for deprecating fields.
     */
    public val deprecated: SyntaxElement<Boolean>? =
        (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.DEPRECATED_FIELD_NUMBER, file)
        }

    /** For Google-internal migration only. Do not use. */
    @Deprecated("For Google-internal migration only. Do not use.")
    @Suppress("DEPRECATION") // proto.hasWeak / .weak deprecated upstream (proto editions migration)
    public val weak: SyntaxElement<Boolean>? =
        (if (proto.hasWeak()) proto.weak else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.WEAK_FIELD_NUMBER, file)
        }

    /**
     * Indicate that the field value should not be printed out when using debug
     * formats, e.g. when the field contains sensitive credentials.
     */
    public val debugRedact: SyntaxElement<Boolean>? =
        (if (proto.hasDebugRedact()) proto.debugRedact else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.DEBUG_REDACT_FIELD_NUMBER, file)
        }

    /** If set to RETENTION_SOURCE, the option will be omitted from the binary. */
    public val retention: SyntaxElement<DescriptorProtos.FieldOptions.OptionRetention>? =
        (if (proto.hasRetention()) proto.retention else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.RETENTION_FIELD_NUMBER, file)
        }

    /**
     * This indicates the types of entities that the field may apply to when used
     * as an option. If it is unset, then the field may be freely used as an
     * option on any kind of entity.
     */
    public val targets: List<SyntaxElement<DescriptorProtos.FieldOptions.OptionTargetType>> by lazy {
        proto.targetsList.mapIndexed { index, target ->
            SyntaxElement(
                target,
                path + DescriptorProtos.FieldOptions.TARGETS_FIELD_NUMBER + index,
                file,
            )
        }
    }

    public val editionDefaults: List<SyntaxElement<DescriptorProtos.FieldOptions.EditionDefault>> by lazy {
        proto.editionDefaultsList.mapIndexed { index, editionDefault ->
            SyntaxElement(
                editionDefault,
                path + DescriptorProtos.FieldOptions.EDITION_DEFAULTS_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? =
        (if (proto.hasFeatures()) proto.features else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.FieldOptions.FEATURES_FIELD_NUMBER, file)
        }

    /** Information about the support window of a feature. */
    public val featureSupport: SyntaxElement<DescriptorProtos.FieldOptions.FeatureSupport>? =
        (if (proto.hasFeatureSupport()) proto.featureSupport else null)?.let {
            SyntaxElement(
                it,
                path + DescriptorProtos.FieldOptions.FEATURE_SUPPORT_FIELD_NUMBER,
                file,
            )
        }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.FieldOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
