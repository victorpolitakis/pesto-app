package com.victorpolitakis.pesto.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The UI follows the phone's language: values/ is English, values-el/ is Greek. Both must declare
 * the same strings, so a missing translation fails here and not as a hole in the app (lint's
 * MissingTranslation says the same, later). Read from the source tree, from the module folder.
 */
class StringResourcesTest {
    private val english = read("src/main/res/values/strings.xml")
    private val greek = read("src/main/res/values-el/strings.xml")

    /** name → text (or the quantities and texts of a plurals), in order of the file. */
    private fun read(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val out = linkedMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            assertTrue("$path: ${node.tagName} without a name", name.isNotEmpty())
            val text = when (node.tagName) {
                "string" -> node.textContent
                "plurals" -> (0 until node.childNodes.length)
                    .mapNotNull { node.childNodes.item(it) as? Element }
                    .joinToString("\n") { "${it.getAttribute("quantity")}=${it.textContent}" }
                else -> error("$path: unexpected <${node.tagName}>")
            }
            assertTrue("$path: duplicate name $name", out.put(name, text) == null)
        }
        return out
    }

    private fun arguments(text: String) = Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z]").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun `English and Greek declare the same names`() {
        assertEquals("only in Greek", emptySet<String>(), greek.keys - english.keys)
        assertEquals("only in English", emptySet<String>(), english.keys - greek.keys)
        assertTrue(english.size > 150)
    }

    @Test
    fun `the plurals of both languages have the quantities of their language`() {
        for (name in english.keys.filter { "\n" in english.getValue(it) }) {
            val en = english.getValue(name).lines().map { it.substringBefore("=") }
            assertEquals("$name in English", listOf("one", "other"), en)
            val el = greek.getValue(name).lines().map { it.substringBefore("=") }
            assertEquals("$name in Greek", listOf("one", "other"), el)
        }
    }

    @Test
    fun `a translation takes the same arguments as the other`() {
        for (name in english.keys) {
            assertEquals("arguments of $name", arguments(greek.getValue(name)).distinct(), arguments(english.getValue(name)).distinct())
        }
    }
}
