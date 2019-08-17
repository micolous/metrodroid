/*
 * Localizer.kt
 *
 * Copyright 2019 Google
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package au.id.micolous.metrodroid

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

object LocalizeGenerator {
    data class XMLStringsFile(
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>
    ) 
    
    const val pkg = "au.id.micolous.metrodroid.multi"
    const val androidR = "au.id.micolous.farebot.R"

    fun generateMappedAppleStringsFile(outputFile: File, inputFile: File, mapFile: File) {
        val xmlFile = readStringsXml(inputFile)

        val mp = mapFile.inputStream().reader(charset = Charset.forName("UTF-8"))
        val strMap = mutableMapOf<String, String>()
        mp.forEachLine lam@{ line ->
            val parts = line.split("=")
            if (parts.size != 2)
                return@lam
            val cleaned = parts.map { it.trim { ch -> ch in " ;\""}}
            strMap[cleaned[0]] = cleaned[1]
        }

        outputFile.getParentFile().mkdirs()
        val os = outputFile.outputStream()
        val w = os.writer(charset = Charset.forName("UTF-8"))
        w.write("/* This file is autogenerated. Do not edit manually.  */\n")
        for ((appleId, androidId) in strMap) {
            if (!androidId.startsWith("string."))
               continue

            val value = xmlFile.strings[androidId.removePrefix("string.")] ?: continue
            w.write("\"${escape(appleId)}\" = \"${escape(value)}\";\n")
        }
        w.close()
    }

    fun generateAppleStringsFile(outputFile: File, inputFile: File, lang: String) {
        val xmlFile = readStringsXml(inputFile)
        outputFile.getParentFile().mkdirs()
        outputFile.createNewFile()
        val os = outputFile.outputStream()
        val w = os.writer(charset = Charset.forName("UTF-8"))
        w.write("/* This file is autogenerated. Do not edit manually.  */\n")
        for ((id, s) in xmlFile.strings) {
            w.write("\"strings.${escape(id)}\" = \"${escape(s)}\";\n")
        }
        for ((id, pl) in xmlFile.plurals) {
            for ((plkey, plval) in pl) {
                w.write("\"plurals.$plkey.${escape(id)}\" = \"${escape(plval)}\";\n")
            }
        }
        w.write("\"meta.lang\" = \"${escape(lang.substringBefore("-"))}\";\n")
        w.write("\"meta.androidLocale\" = \"${escape(lang)}\";\n")
        w.close()
    }

    fun transformExpr(input: String): String? {
        var t = input.substringBefore("@")
        t = t.trim()
        // optimization. We don't do fractions, so v and t are always 0
        t = t.removeSuffix(" and v = 0").removePrefix("v = 0 and ").removeSuffix(" or v != 0").removePrefix("v != 0 or ")
        t = t.removeSuffix(" and t = 0").removePrefix("t = 0 and ").removeSuffix(" or t != 0").removePrefix("t != 0 or ")
        if (t.isEmpty())
           return "else"
        t = t.replace("and", "&&")
        t = t.replace("or", "||")
        return "$t"
    }

    private fun makeRFile(outputDir: File, flavour: String) : OutputStreamWriter {
        val pkgDir = pkg.replace('.', '/')
        val dir = File(outputDir, "$flavour/kotlin/$pkgDir")
        dir.mkdirs()
        val r = File(dir, "R.kt")
        r.createNewFile()
        val os = r.outputStream()
        return os.writer(charset = Charset.forName("UTF-8"))
    }
    
    private fun writeRfile(outputDir: File, flavour: String,
                           keyword: String,
                           xmlFile: XMLStringsFile,
                           drawables: List<String>,
                           transform: (name: String, type: String) -> String) {
        val writer = makeRFile(outputDir, flavour)
        writer.write("/* This file is autogenerated. Do not edit manually.  */\n")
        writer.write("package $pkg\n")
        writer.write("\n")
        writer.write("$keyword object Rstring {\n")
        for (string in xmlFile.strings.keys) {
            writer.write("    " + transform(string, "string") + "\n")
        }
        writer.write("}\n")
        writer.write("\n")
        writer.write("$keyword object Rplurals {\n")
        for (string in xmlFile.plurals.keys) {
            writer.write("    " + transform(string, "plurals") + "\n")
        }
        writer.write("}\n")
        writer.write("\n")
        writer.write("$keyword object Rdrawable {\n")
        for (string in drawables) {
            writer.write("    " + transform(string, "drawable") + "\n")
        }
        writer.write("}\n")
        if (keyword == "expect") {
            writer.write("\n")
            writer.write("\n" +
                    "interface Rinterface {\n" +
                    "    val string: Rstring\n" +
                    "    val plurals: Rplurals\n" +
                    "    val drawable: Rdrawable\n" +
                    "}\n" +
                    "val R get(): Rinterface = object: Rinterface {\n" +
                    "    override val string get() = Rstring\n" +
                    "    override val plurals get() = Rplurals\n" +
                    "    override val drawable get() = Rdrawable\n" +
                    "}")
        }

        writer.close()
    }

    fun readStringsXml(stringsFile: File): XMLStringsFile {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(stringsFile)
        val root = doc.documentElement
        val elements = root.childNodes
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        for (nodeNum in 0 until elements.length) {
            val node = elements.item(nodeNum)
            val resName = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
            when (node.nodeName) {
                "string" -> {
                    strings[resName] = node.textContent
                }
                "plurals" -> {
                    plurals[resName] = getPlurals(node)
                }
            }
        }
        return XMLStringsFile(strings = strings, plurals = plurals)
    }

    private fun getPlurals(node: Node): Map<String, String> {
        val elements = node.childNodes
        val res = mutableMapOf<String, String>()
        for (nodeNum in 0 until elements.length) {
            val itemNode = elements.item(nodeNum)
            if (itemNode.nodeName != "item")
               continue
            res[itemNode.attributes.getNamedItem("quantity").nodeValue] = itemNode.textContent
        }
        return res
    }

    private fun escape(input: String): String {
        val sb = StringBuilder()
        var isEscape = false
        for (c in input) {
            if (isEscape) {
                when (c) {
                    'n', '\\', '"', '\'' -> { sb.append('\\').append(c) }
                    '?' -> { sb.append ("?") }
                    else -> throw Exception("Unknown escape <$c> in <$input>")
                }
                isEscape = false
            } else when(c) {
                '\n' -> {}
                '\\' -> isEscape = true
                '"', '\'', '$' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun generateLocalize(outputDir: File, stringsFile: File, drawablesDirs: List<File>) {
        val xml = readStringsXml(stringsFile)

        val drawables = drawablesDirs.flatMap {
            it.list().toList()
        }.filterNotNull().filter {
            it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".xml")
        }.map {
            it.substringBefore(".").trim()
        }.filterNot {
            it.isBlank()
        }

        writeRfile(outputDir, "commonMain", "expect", xml, drawables) {
            name, type -> "val $name: ${typeName(type)}" }
        writeRfile(outputDir, "androidMain", "actual", xml, drawables) { name, type -> "actual val $name get() = $androidR.$type.$name" }
        writeFallbackRFile(outputDir, "chromeosMain", xml, drawables)
        writeFallbackRFile(outputDir, "iOSMain", xml, drawables)
        writeFallbackRFile(outputDir, "jvmCliMain", xml, drawables)
    }

    private fun writeFallbackRFile(outputDir: File, flavour: String, xmlStrings: XMLStringsFile, drawables: List<String>) {
        writeRfile(outputDir, flavour, "actual", xmlStrings, drawables) { name, type ->
            when (type) {
                "string" -> "actual val $name = ${typeName(type)}(\"$name\", \"${escape(xmlStrings.strings[name]!!)}\")"
                "plurals" -> "actual val $name = ${typeName(type)}(\"$name\", \"${escape(xmlStrings.plurals[name]!!["one"]!!)}\", \"${escape(xmlStrings.plurals[name]!!["other"]!!)}\")"
                else -> "actual val $name = ${typeName(type)}(\"$name\")"
            }
        }
    }

    private fun typeName(type: String): String = type[0].toUpperCase() + type.substring(1) + "Resource"
}
