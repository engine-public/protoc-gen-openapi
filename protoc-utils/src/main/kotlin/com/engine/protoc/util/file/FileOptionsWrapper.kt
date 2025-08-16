package com.engine.protoc.util.file

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.Locatable
import com.engine.protoc.util.SyntaxElement
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors

/**
 * Wrapper for a FileOptions, providing convenient access to its properties and associated syntax elements.
 */
public class FileOptionsWrapper(proto: DescriptorProtos.FileOptions, sourceCodeInfo: SourceCodeInfoWrapper?): AbstractExtendableMessageWrapper<DescriptorProtos.FileOptions>(proto, sourceCodeInfo,
    Companion
) {

    public companion object: Locatable {
        public val fieldsByName: Map<String, Descriptors.FieldDescriptor> = DescriptorProtos.FileOptions.getDescriptor().fields.associateBy { it.name }

        override val path: List<Int>
            get() = listOf(DescriptorProtos.FileDescriptorProto.OPTIONS_FIELD_NUMBER)

        public fun fieldPath(vararg fieldId: Int): List<Int> {
            return listOf(*path.toTypedArray(), *fieldId.toTypedArray())
        }
    }

    public val javaPackage: SyntaxElement<String>? = (if (proto.hasJavaPackage()) proto.javaPackage else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_PACKAGE_FIELD_NUMBER))
        )
    }
    public val javaOuterClassname: SyntaxElement<String>? = (if (proto.hasJavaOuterClassname()) proto.javaOuterClassname else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_OUTER_CLASSNAME_FIELD_NUMBER))
        )
    }
    public val javaMultipleFiles: SyntaxElement<Boolean>? = (if (proto.hasJavaMultipleFiles()) proto.javaMultipleFiles else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_MULTIPLE_FILES_FIELD_NUMBER))
        )
    }
    @Deprecated("This option does nothing.") public val javaGenerateEqualsAndHash: SyntaxElement<Boolean>? = (if (proto.hasJavaGenerateEqualsAndHash()) proto.javaGenerateEqualsAndHash else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_GENERATE_EQUALS_AND_HASH_FIELD_NUMBER))
        )
    }
    public val javaStringCheckUtf8: SyntaxElement<Boolean>? = (if (proto.hasJavaStringCheckUtf8()) proto.javaStringCheckUtf8 else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_STRING_CHECK_UTF8_FIELD_NUMBER))
        )
    }
    public val optimizeFor: SyntaxElement<DescriptorProtos.FileOptions.OptimizeMode>? = (if (proto.hasOptimizeFor()) proto.optimizeFor else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.OPTIMIZE_FOR_FIELD_NUMBER))
        )
    }
    public val goPackage: SyntaxElement<String>? = (if (proto.hasGoPackage()) proto.goPackage else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.GO_PACKAGE_FIELD_NUMBER))
        )
    }
    public val ccGenericServices: SyntaxElement<Boolean>? = (if (proto.hasCcGenericServices()) proto.ccGenericServices else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.CC_GENERIC_SERVICES_FIELD_NUMBER))
        )
    }
    public val javaGenericServices: SyntaxElement<Boolean>? = (if (proto.hasJavaGenericServices()) proto.javaGenericServices else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.JAVA_GENERIC_SERVICES_FIELD_NUMBER))
        )
    }
    public val pyGenericServices: SyntaxElement<Boolean>? = (if (proto.hasPyGenericServices()) proto.pyGenericServices else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.PY_GENERIC_SERVICES_FIELD_NUMBER))
        )
    }
    public val deprecated: SyntaxElement<Boolean>? = (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.DEPRECATED_FIELD_NUMBER))
        )
    }
    public val ccEnableArenas: SyntaxElement<Boolean>? = (if (proto.hasCcEnableArenas()) proto.ccEnableArenas else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.CC_ENABLE_ARENAS_FIELD_NUMBER))
        )
    }
    public val objcClassPrefix: SyntaxElement<String>? = (if (proto.hasObjcClassPrefix()) proto.objcClassPrefix else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.OBJC_CLASS_PREFIX_FIELD_NUMBER))
        )
    }
    public val csharpNamespace: SyntaxElement<String>? = (if (proto.hasCsharpNamespace()) proto.csharpNamespace else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.CSHARP_NAMESPACE_FIELD_NUMBER))
        )
    }
    public val swiftPrefix: SyntaxElement<String>? = (if (proto.hasSwiftPrefix()) proto.swiftPrefix else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.SWIFT_PREFIX_FIELD_NUMBER))
        )
    }
    public val phpClassPrefix: SyntaxElement<String>? = (if (proto.hasPhpClassPrefix()) proto.phpClassPrefix else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.PHP_CLASS_PREFIX_FIELD_NUMBER))
        )
    }
    public val phpNamespace: SyntaxElement<String>? = (if (proto.hasPhpNamespace()) proto.phpNamespace else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.PHP_NAMESPACE_FIELD_NUMBER))
        )
    }
    public val phpMetadataNamespace: SyntaxElement<String>? = (if (proto.hasPhpMetadataNamespace()) proto.phpMetadataNamespace else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.PHP_METADATA_NAMESPACE_FIELD_NUMBER))
        )
    }
    public val rubyPackage: SyntaxElement<String>? = (if (proto.hasRubyPackage()) proto.rubyPackage else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.RUBY_PACKAGE_FIELD_NUMBER))
        )
    }
    public val features: SyntaxElement<DescriptorProtos.FeatureSet> = (if (proto.hasFeatures()) proto.features else null)?.let {
        SyntaxElement(
            it,
            sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.FEATURES_FIELD_NUMBER))
        )
    } ?: SyntaxElement(
        DescriptorProtos.FeatureSet.getDefaultInstance(),
        sourceCodeInfo?.findLocationByPath(fieldPath(DescriptorProtos.FileOptions.FEATURES_FIELD_NUMBER))
    )
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                sourceCodeInfo?.findLocationByPath(
                    fieldPath(
                        DescriptorProtos.FileOptions.UNINTERPRETED_OPTION_FIELD_NUMBER,
                        index
                    )
                )
            )
        }
    }
}
