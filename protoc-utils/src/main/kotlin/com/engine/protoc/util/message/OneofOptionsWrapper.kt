package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public class OneofOptionsWrapper(
    proto: DescriptorProtos.OneofOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.OneofOptions>(proto, file, path) {

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? =
        (if (proto.hasFeatures()) proto.features else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.OneofOptions.FEATURES_FIELD_NUMBER, file)
        }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.OneofOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
