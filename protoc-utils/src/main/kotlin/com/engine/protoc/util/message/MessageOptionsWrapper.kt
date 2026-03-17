package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractExtendableMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

public class MessageOptionsWrapper(
    proto: DescriptorProtos.MessageOptions,
    file: FileDescriptorProtoWrapper,
    override val path: List<Int>,
) : AbstractExtendableMessageWrapper<DescriptorProtos.MessageOptions>(proto, file, path) {

    /**
     * Set true to use the old proto1 MessageSet wire format for extensions.
     * This is provided for backwards-compatibility with the MessageSet wire
     * format.  You should not use this for any other reason:  It's less
     * efficient, has fewer features, and is more complicated.
     *
     * The message must be defined exactly as follows:
     *   message Foo {
     *     option message_set_wire_format = true;
     *     extensions 4 to max;
     *   }
     * Note that the message cannot have any defined fields; MessageSets only
     * have extensions.
     *
     * All extensions of your type must be singular messages; e.g. they cannot
     * be int32s, enums, or repeated messages.
     *
     * Because this is an option, the above two restrictions are not enforced by
     * the protocol compiler.
     */
    public val messageSetWireFormat: SyntaxElement<Boolean>? =
        (if (proto.hasMessageSetWireFormat()) proto.messageSetWireFormat else null)?.let {
            SyntaxElement(
                it,
                path + DescriptorProtos.MessageOptions.MESSAGE_SET_WIRE_FORMAT_FIELD_NUMBER,
                file,
            )
        }

    /**
     * Disables the generation of the standard "descriptor()" accessor, which can
     * conflict with a field of the same name.  This is meant to make migration
     * from proto1 easier; new code should avoid fields named "descriptor".
     */
    public val noStandardDescriptorAccessor: SyntaxElement<Boolean>? =
        (if (proto.hasNoStandardDescriptorAccessor()) proto.noStandardDescriptorAccessor else null)?.let {
            SyntaxElement(
                it,
                path + DescriptorProtos.MessageOptions.NO_STANDARD_DESCRIPTOR_ACCESSOR_FIELD_NUMBER,
                file,
            )
        }

    /**
     * Is this message deprecated?
     * Depending on the target platform, this can emit Deprecated annotations
     * for the message, or it will be completely ignored; in the very least,
     * this is a formalization for deprecating messages.
     */
    public val deprecated: SyntaxElement<Boolean>? =
        (if (proto.hasDeprecated()) proto.deprecated else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MessageOptions.DEPRECATED_FIELD_NUMBER, file)
        }

    /**
     * Whether the message is an automatically generated map entry type for the
     * maps field.
     *
     * For maps fields:
     *     map<KeyType, ValueType> map_field = 1;
     * The parsed descriptor looks like:
     *     message MapFieldEntry {
     *         option map_entry = true;
     *         optional KeyType key = 1;
     *         optional ValueType value = 2;
     *     }
     *     repeated MapFieldEntry map_field = 1;
     *
     * Implementations may choose not to generate the map_entry=true message, but
     * use a native map in the target language to hold the keys and values.
     * The reflection APIs in such implementations still need to work as
     * if the field is a repeated message field.
     *
     * NOTE: Do not set the option in .proto files. Always use the maps syntax
     * instead. The option should only be implicitly set by the proto compiler
     * parser.
     */
    public val mapEntry: SyntaxElement<Boolean>? =
        (if (proto.hasMapEntry()) proto.mapEntry else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MessageOptions.MAP_ENTRY_FIELD_NUMBER, file)
        }

    /**
     * Enable the legacy handling of JSON field name conflicts.  This lowercases
     * and strips underscored from the fields before comparison in proto3 only.
     * The new behavior takes `json_name` into account and applies to proto2 as
     * well.
     *
     * This should only be used as a temporary measure against broken builds due
     * to the change in behavior for JSON field name conflicts.
     *
     * TODO This is legacy behavior we plan to remove once downstream
     * teams have had time to migrate.
     */
    @Deprecated("Legacy behavior planned for removal. Migrate off JSON field name conflict handling.")
    public val deprecatedLegacyJsonFieldConflicts: SyntaxElement<Boolean>? =
        (if (proto.hasDeprecatedLegacyJsonFieldConflicts()) proto.deprecatedLegacyJsonFieldConflicts else null)?.let {
            SyntaxElement(
                it,
                path + DescriptorProtos.MessageOptions.DEPRECATED_LEGACY_JSON_FIELD_CONFLICTS_FIELD_NUMBER,
                file,
            )
        }

    /**
     * Any features defined in the specific edition.
     * WARNING: This field should only be used by protobuf plugins or special
     * cases like the proto compiler. Other uses are discouraged and
     * developers should rely on the protoreflect APIs for their client language.
     */
    public val features: SyntaxElement<DescriptorProtos.FeatureSet>? =
        (if (proto.hasFeatures()) proto.features else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.MessageOptions.FEATURES_FIELD_NUMBER, file)
        }

    /** The parser stores options it doesn't recognize here. See [com.google.protobuf.DescriptorProtos.UninterpretedOption]. */
    public val uninterpretedOptions: List<SyntaxElement<DescriptorProtos.UninterpretedOption>> by lazy {
        proto.uninterpretedOptionList.mapIndexed { index, option ->
            SyntaxElement(
                option,
                path + DescriptorProtos.MessageOptions.UNINTERPRETED_OPTION_FIELD_NUMBER + index,
                file,
            )
        }
    }
}
