package com.engine.protoc.util.enums

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public class EnumValueOptionsWrapper(
    proto: DescriptorProtos.EnumValueOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.EnumValueOptions>(proto, file, path) {

    /**
     * Is this enum value deprecated?
     * Depending on the target platform, this can emit Deprecated annotations
     * for the enum value, or it will be completely ignored; in the very least,
     * this is a formalization for deprecating enum values.
     */
    public val deprecated: SyntaxElement<Boolean>? = (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumValueOptions.DEPRECATED_FIELD_NUMBER, file)
    }

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? = (if (proto.hasFeatures()) proto.features else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumValueOptions.FEATURES_FIELD_NUMBER, file)
    }

    /**
     * Indicate that fields annotated with this enum value should not be printed
     * out when using debug formats, e.g. when the field contains sensitive
     * credentials.
     */
    public val debugRedact: SyntaxElement<Boolean>? = (if (proto.hasDebugRedact()) proto.debugRedact else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.EnumValueOptions.DEBUG_REDACT_FIELD_NUMBER, file)
    }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.EnumValueOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
