package com.engine.protoc.util.message

import com.engine.protoc.util.AbstractLocatable
import com.engine.protoc.util.GeneratedMessageWrapper
import com.engine.protoc.util.SyntaxElement
import com.engine.protoc.util.enums.EnumDescriptorProtoWrapper
import com.engine.protoc.util.file.FileDescriptorProtoWrapper
import com.google.protobuf.DescriptorProtos

/** Describes a message type. */
public class DescriptorProtoWrapper(
    override val proto: DescriptorProtos.DescriptorProto,
    path: List<Int>,
    file: FileDescriptorProtoWrapper,
) : AbstractLocatable(path, file),
    GeneratedMessageWrapper<DescriptorProtos.DescriptorProto> {

    /** The unqualified name of the message as written in the .proto source. */
    public val name: SyntaxElement<String>? = (if (proto.hasName()) proto.name else null)?.let {
        SyntaxElement(it, path + DescriptorProtos.DescriptorProto.NAME_FIELD_NUMBER, file)
    }

    /** The fields declared directly in this message (not inherited, not extensions). */
    public val fields: List<FieldDescriptorProtoWrapper> by lazy {
        proto.fieldList.mapIndexed { index, fieldProto ->
            FieldDescriptorProtoWrapper(
                fieldProto,
                path + DescriptorProtos.DescriptorProto.FIELD_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Extension fields defined inside this message block, extending other messages.
     * Each entry describes one extension field via an `extend OtherMessage { ... }` block
     * nested within this message declaration.  This is a proto2-only feature; in proto3,
     * extensions must be declared at the file level.
     */
    public val extensions: List<FieldDescriptorProtoWrapper> by lazy {
        proto.extensionList.mapIndexed { index, extensionProto ->
            FieldDescriptorProtoWrapper(
                extensionProto,
                path + DescriptorProtos.DescriptorProto.EXTENSION_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Message types nested directly inside this message declaration.
     * Each entry is a fully-described inner message (which may itself contain
     * further nested types, fields, etc.).
     */
    public val nestedTypes: List<DescriptorProtoWrapper> by lazy {
        proto.nestedTypeList.mapIndexed { index, nestedProto ->
            DescriptorProtoWrapper(
                nestedProto,
                path + DescriptorProtos.DescriptorProto.NESTED_TYPE_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /** Enum types declared directly inside this message. */
    public val enumTypes: List<EnumDescriptorProtoWrapper> by lazy {
        proto.enumTypeList.mapIndexed { index, enumProto ->
            EnumDescriptorProtoWrapper(
                enumProto,
                path + DescriptorProtos.DescriptorProto.ENUM_TYPE_FIELD_NUMBER + index,
                file,
            )
        }
    }

    public val options: MessageOptionsWrapper? by lazy {
        if (proto.hasOptions()) {
            MessageOptionsWrapper(
                proto.options,
                file,
                path + DescriptorProtos.DescriptorProto.OPTIONS_FIELD_NUMBER,
            )
        } else {
            null
        }
    }

    /**
     * The oneof groups declared in this message.  Each field that belongs to a
     * oneof has its [FieldDescriptorProtoWrapper.oneofIndex] set to the index of
     * the oneof in this list.
     */
    public val oneofDecls: List<OneofDescriptorProtoWrapper> by lazy {
        proto.oneofDeclList.mapIndexed { index, oneofProto ->
            OneofDescriptorProtoWrapper(
                oneofProto,
                path + DescriptorProtos.DescriptorProto.ONEOF_DECL_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Extension ranges declared in this message.  These ranges specify which
     * field numbers are reserved for extension fields (defined elsewhere via
     * `extend ThisMessage { ... }` blocks).  Extension ranges may not overlap
     * with each other or with any regular field numbers in this message.
     * This is a proto2-only feature; proto3 messages do not allow other files
     * to extend them.
     *
     * Each entry's [SyntaxElement.value] is an [DescriptorProtos.DescriptorProto.ExtensionRange]
     * whose `start` is inclusive and `end` is exclusive.
     */
    public val extensionRanges: List<SyntaxElement<DescriptorProtos.DescriptorProto.ExtensionRange>> by lazy {
        proto.extensionRangeList.mapIndexed { index, range ->
            SyntaxElement(
                range,
                path + DescriptorProtos.DescriptorProto.EXTENSION_RANGE_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Range of reserved tag numbers. Reserved tag numbers may not be used by
     * fields or extension ranges in the same message. Reserved ranges may
     * not overlap.
     *
     * Each entry's [SyntaxElement.value] is a [DescriptorProtos.DescriptorProto.ReservedRange]
     * whose `start` is inclusive and `end` is exclusive.
     */
    public val reservedRanges: List<SyntaxElement<DescriptorProtos.DescriptorProto.ReservedRange>> by lazy {
        proto.reservedRangeList.mapIndexed { index, range ->
            SyntaxElement(
                range,
                path + DescriptorProtos.DescriptorProto.RESERVED_RANGE_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Reserved field names, which may not be used by fields in the same message.
     * A given name may only be reserved once.
     */
    public val reservedNames: List<SyntaxElement<String>> by lazy {
        proto.reservedNameList.mapIndexed { index, reservedName ->
            SyntaxElement(
                reservedName,
                path + DescriptorProtos.DescriptorProto.RESERVED_NAME_FIELD_NUMBER + index,
                file,
            )
        }
    }

    /**
     * Describes the 'visibility' of a symbol with respect to the proto import
     * system.  Symbols can only be imported when the visibility rules do not
     * prevent it (ex: local symbols cannot be imported).  Visibility modifiers
     * can only be set on `message` and `enum` as they are the only types
     * available to be referenced from other files.
     *
     * This field is only present for proto editions (2023+) messages that carry
     * an explicit `export` or `local` modifier.  It is `null` for all proto2
     * and proto3 messages.
     */
    public val visibility: SyntaxElement<DescriptorProtos.SymbolVisibility>? =
        (if (proto.hasVisibility()) proto.visibility else null)?.let {
            SyntaxElement(it, path + DescriptorProtos.DescriptorProto.VISIBILITY_FIELD_NUMBER, file)
        }
}
