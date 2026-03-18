package com.engine.protoc.util.file

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.google.protobuf.DescriptorProtos

/**
 * Wrapper for [com.google.protobuf.DescriptorProtos.FileOptions], exposing each standard file-level
 * option as a [com.engine.protoc.util.SyntaxElement] so callers can also access the option's source
 * location and comments.  Each property is null when the corresponding option was not set in the
 * .proto file.
 */
public class FileOptionsWrapper(
    proto: DescriptorProtos.FileOptions,
    file: FileDescriptorProtoWrapper,
    public override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.FileOptions>(proto, file, path) {

    /**
     * Sets the Java package where classes generated from this .proto will be placed.  By default,
     * the proto package is used, but this is often inappropriate because proto packages do not
     * normally start with backwards domain names.
     */
    public val javaPackage: SyntaxElement<String>? = (if (proto.hasJavaPackage()) proto.javaPackage else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_PACKAGE_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Controls the name of the wrapper Java class generated for the .proto file.  That class will
     * always contain the .proto file's getDescriptor() method as well as any top-level extensions
     * defined in the .proto file.  If java_multiple_files is disabled, then all the other classes
     * from the .proto file will be nested inside the single wrapper outer class.
     */
    public val javaOuterClassname: SyntaxElement<String>? = (if (proto.hasJavaOuterClassname()) proto.javaOuterClassname else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_OUTER_CLASSNAME_FIELD_NUMBER,
            file,
        )
    }

    /**
     * If enabled, then the Java code generator will generate a separate .java file for each
     * top-level message, enum, and service defined in the .proto file.  Thus, these types will
     * *not* be nested inside the wrapper class named by java_outer_classname.  However, the wrapper
     * class will still be generated to contain the file's getDescriptor() method as well as any
     * top-level extensions defined in the file.
     */
    public val javaMultipleFiles: SyntaxElement<Boolean>? = (if (proto.hasJavaMultipleFiles()) proto.javaMultipleFiles else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_MULTIPLE_FILES_FIELD_NUMBER,
            file,
        )
    }

    @Deprecated("This option does nothing.")
    public val javaGenerateEqualsAndHash: SyntaxElement<Boolean>? = (if (proto.hasJavaGenerateEqualsAndHash()) proto.javaGenerateEqualsAndHash else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_GENERATE_EQUALS_AND_HASH_FIELD_NUMBER,
            file,
        )
    }

    /**
     * A proto2 file can set this to true to opt in to UTF-8 checking for Java, which will throw an
     * exception if invalid UTF-8 is parsed from the wire or assigned to a string field.  Proto3
     * files already perform these checks; setting this option explicitly to false has no effect on
     * proto3 files.
     */
    public val javaStringCheckUtf8: SyntaxElement<Boolean>? = (if (proto.hasJavaStringCheckUtf8()) proto.javaStringCheckUtf8 else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_STRING_CHECK_UTF8_FIELD_NUMBER,
            file,
        )
    }

    /** Controls whether generated code is optimized for speed, binary size, or the lite runtime. */
    public val optimizeFor: SyntaxElement<DescriptorProtos.FileOptions.OptimizeMode>? = (if (proto.hasOptimizeFor()) proto.optimizeFor else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.OPTIMIZE_FOR_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Sets the Go package where structs generated from this .proto will be placed.  If omitted, the
     * Go package will be derived from: the basename of the package import path, if provided;
     * otherwise the package statement in the .proto file; otherwise the basename of the .proto file.
     */
    public val goPackage: SyntaxElement<String>? = (if (proto.hasGoPackage()) proto.goPackage else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.GO_PACKAGE_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Should generic services be generated for C++?  Generic services are not specific to any
     * particular RPC system and are now deprecated in favour of RPC-specific plugins.  Defaults to
     * false; old code that depends on generic services should set this explicitly to true.
     */
    public val ccGenericServices: SyntaxElement<Boolean>? = (if (proto.hasCcGenericServices()) proto.ccGenericServices else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.CC_GENERIC_SERVICES_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Should generic services be generated for Java?  See [ccGenericServices] for the full
     * explanation; this is the Java counterpart.
     */
    public val javaGenericServices: SyntaxElement<Boolean>? = (if (proto.hasJavaGenericServices()) proto.javaGenericServices else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.JAVA_GENERIC_SERVICES_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Should generic services be generated for Python?  See [ccGenericServices] for the full
     * explanation; this is the Python counterpart.
     */
    public val pyGenericServices: SyntaxElement<Boolean>? = (if (proto.hasPyGenericServices()) proto.pyGenericServices else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.PY_GENERIC_SERVICES_FIELD_NUMBER,
            file,
        )
    }
    public val deprecated: SyntaxElement<Boolean>? = (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.DEPRECATED_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Enables the use of arenas for the proto messages in this file.  Applies only to generated
     * classes for C++.
     */
    public val ccEnableArenas: SyntaxElement<Boolean>? = (if (proto.hasCcEnableArenas()) proto.ccEnableArenas else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.CC_ENABLE_ARENAS_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Sets the Objective-C class prefix prepended to all Objective-C generated classes from this
     * .proto.  There is no default.
     */
    public val objcClassPrefix: SyntaxElement<String>? = (if (proto.hasObjcClassPrefix()) proto.objcClassPrefix else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.OBJC_CLASS_PREFIX_FIELD_NUMBER,
            file,
        )
    }

    /** Namespace for generated C# classes; defaults to the package. */
    public val csharpNamespace: SyntaxElement<String>? = (if (proto.hasCsharpNamespace()) proto.csharpNamespace else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.CSHARP_NAMESPACE_FIELD_NUMBER,
            file,
        )
    }

    /**
     * By default Swift generators will take the proto package, CamelCase it replacing '.' with
     * underscore, and use that to prefix the types/symbols defined.  When this option is provided,
     * they will use this value instead.
     */
    public val swiftPrefix: SyntaxElement<String>? = (if (proto.hasSwiftPrefix()) proto.swiftPrefix else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.SWIFT_PREFIX_FIELD_NUMBER,
            file,
        )
    }

    /** Sets the PHP class prefix prepended to all PHP generated classes from this .proto.  Default is empty. */
    public val phpClassPrefix: SyntaxElement<String>? = (if (proto.hasPhpClassPrefix()) proto.phpClassPrefix else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.PHP_CLASS_PREFIX_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Use this option to change the namespace of PHP generated classes.  Default is empty.  When
     * empty, the package name will be used for determining the namespace.
     */
    public val phpNamespace: SyntaxElement<String>? = (if (proto.hasPhpNamespace()) proto.phpNamespace else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.PHP_NAMESPACE_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Use this option to change the namespace of PHP generated metadata classes.  Default is
     * empty.  When empty, the proto file name will be used for determining the namespace.
     */
    public val phpMetadataNamespace: SyntaxElement<String>? = (if (proto.hasPhpMetadataNamespace()) proto.phpMetadataNamespace else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.PHP_METADATA_NAMESPACE_FIELD_NUMBER,
            file,
        )
    }

    /**
     * Use this option to change the package of Ruby generated classes.  Default is empty.  When
     * not set, the package name will be used for determining the Ruby package.
     */
    public val rubyPackage: SyntaxElement<String>? = (if (proto.hasRubyPackage()) proto.rubyPackage else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.RUBY_PACKAGE_FIELD_NUMBER,
            file,
        )
    }
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? = (if (proto.hasFeatures()) proto.features else null)?.let {
        SyntaxElement(
            it,
            path + DescriptorProtos.FileOptions.FEATURES_FIELD_NUMBER,
            file,
        )
    }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.FileOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
