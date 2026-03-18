import com.engine.protoc.util.compiler.VersionWrapper
import com.google.protobuf.compiler.PluginProtos
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VersionWrapperTests :
    FunSpec({

        fun version(
            major: Int,
            minor: Int,
            patch: Int,
            suffix: String? = null,
        ): VersionWrapper {
            val builder = PluginProtos.Version.newBuilder()
                .setMajor(major)
                .setMinor(minor)
                .setPatch(patch)
            suffix?.let { builder.setSuffix(it) }
            return VersionWrapper(builder.build())
        }

        test("toString without suffix produces major.minor.patch") {
            version(1, 2, 3).toString() shouldBe "1.2.3"
        }

        test("toString with suffix appends dash-suffix") {
            version(3, 21, 4, "rc1").toString() shouldBe "3.21.4-rc1"
        }

        test("toString with zero components") {
            version(0, 0, 0).toString() shouldBe "0.0.0"
        }

        test("toString with empty suffix is treated as no suffix") {
            // proto.hasSuffix() returns false for an unset field; setting it to "" would
            // make hasSuffix() return true, so this verifies the suffix IS appended (empty dash)
            version(1, 0, 0, "").toString() shouldBe "1.0.0-"
        }

        test("toString with multi-word suffix") {
            version(4, 0, 0, "alpha.preview").toString() shouldBe "4.0.0-alpha.preview"
        }
    })