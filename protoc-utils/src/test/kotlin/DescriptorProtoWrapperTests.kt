import com.engine.protoc.util.extensions.wrap
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.compiler.PluginProtos
import engine.protoc.utils.RegisteredExtensions
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class DescriptorProtoWrapperTests :
    FunSpec({

        val registry = ExtensionRegistry.newInstance().apply {
            RegisteredExtensions.registerAllExtensions(this)
        }

        val cgreq = this::class.java.getResourceAsStream("/code-generator-request.binpb")
            .shouldNotBeNull()
            .use { input ->
                PluginProtos.CodeGeneratorRequest.parseFrom(input, registry)
            }
            .wrap()

        // ---------------------------------------------------------------------------
        // comments.proto — provides MessageWithComment (nestedTypes, fields) and
        // MessageWithReserved (reservedRanges, reservedNames).
        // ---------------------------------------------------------------------------

        val file = cgreq.sourceFileDescriptors.find {
            it.name == "engine/protoc/utils/comments.proto"
        }.shouldNotBeNull()

        // ---------------------------------------------------------------------------
        // nestedTypes
        // ---------------------------------------------------------------------------

        context("nestedTypes") {
            test("MessageWithComment has exactly one nested type") {
                file.messageTypes[0].nestedTypes shouldHaveSize 1
            }

            test("nested type name is NestedMessageWithComment") {
                file.messageTypes[0].nestedTypes[0].name?.value shouldBe "NestedMessageWithComment"
            }

            test("nested type has its own fields") {
                // NestedMessageWithComment has a single string field
                file.messageTypes[0].nestedTypes[0].fields shouldHaveSize 1
                file.messageTypes[0].nestedTypes[0].fields[0].name?.value shouldBe "field_with_comment"
            }

            test("MessageWithoutComment has no nested types") {
                file.messageTypes[1].nestedTypes.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // fields
        // ---------------------------------------------------------------------------

        context("fields") {
            test("MessageWithComment has two fields") {
                file.messageTypes[0].fields shouldHaveSize 2
            }

            test("first field name and number are correct") {
                assertSoftly {
                    withClue("field name") {
                        file.messageTypes[0].fields[0].name?.value shouldBe "field_with_comment"
                    }
                    withClue("field number") {
                        file.messageTypes[0].fields[0].number?.value shouldBe 1
                    }
                }
            }

            test("second field name and number are correct") {
                assertSoftly {
                    withClue("field name") {
                        file.messageTypes[0].fields[1].name?.value shouldBe "field_without_comment"
                    }
                    withClue("field number") {
                        file.messageTypes[0].fields[1].number?.value shouldBe 2
                    }
                }
            }

            test("field with comment has the expected leading comment") {
                file.messageTypes[0].fields[0]
                    .location.shouldNotBeNull()
                    .leadingComments.shouldNotBeNull()
                    .cleaned shouldBe "comment on field in a message MessageWithComment.field_with_comment"
            }

            test("field without comment has no leading comment") {
                file.messageTypes[0].fields[1]
                    .location.shouldNotBeNull()
                    .leadingComments.shouldBeNull()
            }

            test("MessageWithoutComment has no fields") {
                file.messageTypes[1].fields.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // reservedRanges
        // ---------------------------------------------------------------------------

        context("reservedRanges") {
            val messageWithReserved = file.messageTypes
                .find { it.name?.value == "MessageWithReserved" }
                .shouldNotBeNull()

            test("MessageWithReserved has exactly one reserved range") {
                messageWithReserved.reservedRanges shouldHaveSize 1
            }

            test("reserved range start is 10 (inclusive)") {
                messageWithReserved.reservedRanges[0].value.start shouldBe 10
            }

            test("reserved range end is 21 (exclusive — proto stores end+1 for 'reserved 10 to 20')") {
                // The proto source `reserved 10 to 20;` means field numbers 10–20 inclusive.
                // DescriptorProto.ReservedRange.end is exclusive, so it is stored as 21.
                messageWithReserved.reservedRanges[0].value.end shouldBe 21
            }

            test("reserved range location exists but has no leading comment — protoc drops comments on reserved statements") {
                // protoc emits a SourceCodeInfo.Location for the reserved range, but
                // it does not preserve any leading or trailing comment text from the source.
                messageWithReserved.reservedRanges[0].location.shouldNotBeNull()
                    .leadingComments.shouldBeNull()
            }

            test("MessageWithComment has no reserved ranges") {
                file.messageTypes[0].reservedRanges.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // reservedNames
        // ---------------------------------------------------------------------------

        context("reservedNames") {
            val messageWithReserved = file.messageTypes
                .find { it.name?.value == "MessageWithReserved" }
                .shouldNotBeNull()

            test("MessageWithReserved has exactly one reserved name") {
                messageWithReserved.reservedNames shouldHaveSize 1
            }

            test("reserved name value is correct") {
                messageWithReserved.reservedNames[0].value shouldBe "old_field_name"
            }

            test("reserved name location exists but has no leading comment — protoc drops comments on reserved statements") {
                // protoc emits a SourceCodeInfo.Location for the reserved name, but
                // it does not preserve any leading or trailing comment text from the source.
                messageWithReserved.reservedNames[0].location.shouldNotBeNull()
                    .leadingComments.shouldBeNull()
            }

            test("MessageWithComment has no reserved names") {
                file.messageTypes[0].reservedNames.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // visibility
        // proto3 messages never carry an explicit visibility modifier; the field
        // should always be null for files using proto3 syntax.
        // ---------------------------------------------------------------------------

        context("visibility") {
            test("proto3 message has null visibility") {
                file.messageTypes[0].visibility.shouldBeNull()
            }

            test("proto3 message without comment also has null visibility") {
                file.messageTypes[1].visibility.shouldBeNull()
            }
        }

        // ---------------------------------------------------------------------------
        // proto2_descriptor_features.proto — exercises extension ranges and
        // message-level extension fields (both proto2-only features).
        // ---------------------------------------------------------------------------

        val proto2File = cgreq.sourceFileDescriptors.find {
            it.name == "engine/protoc/utils/proto2_descriptor_features.proto"
        }.shouldNotBeNull()

        // ---------------------------------------------------------------------------
        // extensionRanges
        // ---------------------------------------------------------------------------

        context("extensionRanges") {
            val extTarget = proto2File.messageTypes
                .find { it.name?.value == "Proto2ExtensionTarget" }
                .shouldNotBeNull()

            test("Proto2ExtensionTarget has exactly one extension range") {
                extTarget.extensionRanges shouldHaveSize 1
            }

            test("extension range start is 100 (inclusive)") {
                extTarget.extensionRanges[0].value.start shouldBe 100
            }

            test("extension range end is 201 (exclusive — proto stores end+1 for 'extensions 100 to 200')") {
                // `extensions 100 to 200;` means field numbers 100–200 inclusive.
                // ExtensionRange.end is exclusive, so it is stored as 201.
                extTarget.extensionRanges[0].value.end shouldBe 201
            }

            test("extension range location exists but has no leading comment — protoc drops comments on extensions statements") {
                // protoc emits a SourceCodeInfo.Location for the extension range, but
                // it does not preserve any leading or trailing comment text from the source.
                extTarget.extensionRanges[0].location.shouldNotBeNull()
                    .leadingComments.shouldBeNull()
            }

            test("Proto2MessageWithExtensions has no extension ranges of its own") {
                proto2File.messageTypes
                    .find { it.name?.value == "Proto2MessageWithExtensions" }
                    .shouldNotBeNull()
                    .extensionRanges.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // extensions (message-level — proto2 only)
        // ---------------------------------------------------------------------------

        context("extensions") {
            val msgWithExtensions = proto2File.messageTypes
                .find { it.name?.value == "Proto2MessageWithExtensions" }
                .shouldNotBeNull()

            test("Proto2MessageWithExtensions has exactly one extension") {
                msgWithExtensions.extensions shouldHaveSize 1
            }

            test("extension field name is correct") {
                msgWithExtensions.extensions[0].name?.value shouldBe "container_string"
            }

            test("extension extendee is the fully-qualified name of Proto2ExtensionTarget") {
                // protoc always writes extendee as a fully-qualified name with a leading dot
                msgWithExtensions.extensions[0].extendee?.value shouldBe
                    ".engine.protoc.utils.Proto2ExtensionTarget"
            }

            test("extension field number matches the proto source") {
                msgWithExtensions.extensions[0].number?.value shouldBe 100
            }

            test("Proto2ExtensionTarget has no message-level extensions of its own") {
                proto2File.messageTypes
                    .find { it.name?.value == "Proto2ExtensionTarget" }
                    .shouldNotBeNull()
                    .extensions.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // file-level extensions (FileDescriptorProto.extension — proto2 only)
        // ---------------------------------------------------------------------------

        context("file-level extensions") {
            test("proto2 file has exactly one file-level extension") {
                proto2File.extensions shouldHaveSize 1
            }

            test("file-level extension name is correct") {
                proto2File.extensions[0].name?.value shouldBe "file_level_string"
            }

            test("file-level extension extendee is the fully-qualified name of Proto2ExtensionTarget") {
                // protoc always writes extendee as a fully-qualified name with a leading dot
                proto2File.extensions[0].extendee?.value shouldBe
                    ".engine.protoc.utils.Proto2ExtensionTarget"
            }

            test("file-level extension field number matches the proto source") {
                proto2File.extensions[0].number?.value shouldBe 101
            }

            test("proto3 file has no file-level extensions") {
                file.extensions.shouldBeEmpty()
            }
        }

        // ---------------------------------------------------------------------------
        // edition
        // proto2 and proto3 files never carry an edition field; only files that
        // use the "editions" syntax do.  Both should always be null here.
        // ---------------------------------------------------------------------------

        context("edition") {
            test("proto2 file has null edition") {
                proto2File.edition.shouldBeNull()
            }

            test("proto3 file has null edition") {
                file.edition.shouldBeNull()
            }
        }
    })
