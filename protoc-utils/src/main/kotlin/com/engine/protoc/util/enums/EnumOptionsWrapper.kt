package com.engine.protoc.util.enums

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/**
 * Wrapper for [com.google.protobuf.DescriptorProtos.EnumOptions], exposing each standard
 * enum-level option as a [com.engine.protoc.util.SyntaxElement].  Each property is null when
 * the corresponding option was not set on the enum.
 */
public class EnumOptionsWrapper(
    proto: DescriptorProtos.EnumOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.EnumOptions>(proto, file, path) {

    /** Set this option to true to allow mapping different tag names to the same value. */
    public val allowAlias: SyntaxElement<Boolean>? = (if (proto.hasAllowAlias()) proto.allowAlias else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumOptions.ALLOW_ALIAS_FIELD_NUMBER, file)
    }

    /**
     * Is this enum deprecated?
     * Depending on the target platform, this can emit Deprecated annotations
     * for the enum, or it will be completely ignored; in the very least, this
     * is a formalization for deprecating enums.
     */
    public val deprecated: SyntaxElement<Boolean>? = (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumOptions.DEPRECATED_FIELD_NUMBER, file)
    }

    /**
     * Enable the legacy handling of JSON field name conflicts.  This lowercases
     * and strips underscored from the fields before comparison in proto3 only.
     * The new behavior takes `json_name` into account and applies to proto2 as
     * well.
     * TODO Remove this legacy behavior once downstream teams have
     * had time to migrate.
     */
    @Deprecated("Legacy behavior planned for removal. Migrate off JSON field name conflict handling.")
    @Suppress("DEPRECATION") // proto.hasDeprecatedLegacyJsonFieldConflicts / .deprecatedLegacyJsonFieldConflicts deprecated upstream (proto editions migration)
    public val deprecatedLegacyJsonFieldConflicts: SyntaxElement<Boolean>? =
        (if (proto.hasDeprecatedLegacyJsonFieldConflicts()) proto.deprecatedLegacyJsonFieldConflicts else null)?.let {
            SyntaxElement(
                it,
                path + DescriptorProtos.EnumOptions.DEPRECATED_LEGACY_JSON_FIELD_CONFLICTS_FIELD_NUMBER,
                file,
            )
        }

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? = (if (proto.hasFeatures()) proto.features else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumOptions.FEATURES_FIELD_NUMBER, file)
    }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.EnumOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
