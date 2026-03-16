package com.engine.protoc.util.service

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public class MethodOptionsWrapper(
    proto: DescriptorProtos.MethodOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.MethodOptions>(proto, file, path) {

    /**
     * Is this method deprecated?
     * Depending on the target platform, this can emit Deprecated annotations
     * for the method, or it will be completely ignored; in the very least,
     * this is a formalization for deprecating methods.
     */
    public val deprecated: SyntaxElement<Boolean>? = (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.MethodOptions.DEPRECATED_FIELD_NUMBER, file)
    }

    /**
     * Is this method side-effect-free (or safe in HTTP parlance), or idempotent,
     * or neither? HTTP based RPC implementation may choose GET verb for safe
     * methods, and PUT verb for idempotent methods instead of the default POST.
     */
    public val idempotencyLevel: SyntaxElement<DescriptorProtos.MethodOptions.IdempotencyLevel>? =
        (if (proto.hasIdempotencyLevel()) proto.idempotencyLevel else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MethodOptions.IDEMPOTENCY_LEVEL_FIELD_NUMBER, file)
        }

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? = (if (proto.hasFeatures()) proto.features else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.MethodOptions.FEATURES_FIELD_NUMBER, file)
    }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.MethodOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
