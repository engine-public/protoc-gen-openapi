import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

class MDTest :
    FunSpec({
        assertSoftly = true

        test("foo") {
            val parser = Parser.builder().build()
            val parsed = MDTest::class.java.classLoader.getResourceAsStream("helpme.md").use { stream -> stream.reader().use { parser.parseReader(it) } }

            val createdByAst = Document().apply {
                appendChild(
                    Heading().apply {
                        level = 1
                        appendChild(Text("Example Markdown"))
                    },
                )
                appendChild(
                    Paragraph().apply {
                        appendChild(Text("Here's some text."))
                    },
                )
                appendChild(
                    BulletList().apply {
                        appendChild(
                            ListItem().apply {
                                appendChild(
                                    Paragraph().apply {
                                        appendChild(Text("bullet number one"))
                                    },
                                )
                            },
                        )
                        appendChild(
                            ListItem().apply {
                                appendChild(
                                    Paragraph().apply {
                                        appendChild(Text("bullet number two"))
                                    },
                                )
                                appendChild(
                                    Paragraph().apply {
                                        appendChild(Text("with continuation line"))
                                    },
                                )
                            },
                        )
                        appendChild(
                            ListItem().apply {
                                appendChild(
                                    Paragraph().apply {
                                        appendChild(Text("bullet number three"))
                                    },
                                )
                            },
                        )
                    },
                )
            }

            createdByAst shouldNotBe parsed

            val renderer = HtmlRenderer.builder().build()
            renderer.render(parsed) shouldBe renderer.render(createdByAst)
        }
    })
