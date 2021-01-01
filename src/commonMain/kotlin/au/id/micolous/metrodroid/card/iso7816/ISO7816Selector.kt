/*
 * ISO7816Selector.kt
 *
 * Copyright 2018 Google
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

package au.id.micolous.metrodroid.card.iso7816

import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = ISO7816Selector.Companion::class)
data class ISO7816Selector (private val path: List<ISO7816SelectorElement>) {
    fun formatString(): String {
        val ret = StringBuilder()
        for (it in path) {
            ret.append(it.formatString())
        }
        return ret.toString()
    }

    suspend fun select(tag: ISO7816Protocol): ImmutableByteArray? {
        var fci: ImmutableByteArray? = null
        for (sel in path) {
            fci = sel.select(tag)
        }
        return fci
    }

    /**
     * If this selector starts with (or is the same as) {@param other}, return true.
     *
     * @param other The other selector to compare with.
     * @return True if this starts with {@param other}.
     */
    fun startsWith(other: ISO7816Selector): Boolean {
        val a = path.iterator()
        val b = other.path.iterator()

        while (true) {
            if (!b.hasNext())
                return true // "other" is shorter or equal length to this
            if (!a.hasNext())
                return false // "other" is longer
            if (a.next() != b.next())
                return false
        }
    }

    fun appendPath(vararg addPath: Int) = ISO7816Selector(path + addPath.map {ISO7816SelectorById(it)} )

    /**
     * Returns the number of [ISO7816SelectorElement]s in this [ISO7816Selector].
     */
    fun size() = path.size

    /**
     * Returns the parent selector, or `null` if at the root (or 1 level from the root).
     *
     * @return The parent of the path selector represented by this [ISO7816Selector].
     */
    fun parent(): ISO7816Selector? {
        if (path.size <= 1) {
            return null
        }

        return ISO7816Selector(path.dropLast(1))
    }

    override fun toString() = formatString()

    @Serializer(forClass = ISO7816Selector::class)
    companion object : KSerializer<ISO7816Selector> {

        fun makeSelector(vararg path: Int) = ISO7816Selector(path.map {ISO7816SelectorById(it)})

        fun makeSelector(name: ImmutableByteArray): ISO7816Selector =
                ISO7816Selector(listOf<ISO7816SelectorElement>(ISO7816SelectorByName(name)))

        fun makeSelector(folder: ImmutableByteArray, file: Int): ISO7816Selector =
                ISO7816Selector(listOf(ISO7816SelectorByName(folder), ISO7816SelectorById(file)))

        @OptIn(kotlinx.serialization.InternalSerializationApi::class)
        override val descriptor: SerialDescriptor =
            buildSerialDescriptor("ISO7816Selector", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ISO7816Selector) {
            encoder.encodeString(value.formatString())
        }

        override fun deserialize(decoder: Decoder): ISO7816Selector = fromString(decoder.decodeString())

        private fun fromString(input: String): ISO7816Selector {
            val path = mutableListOf<ISO7816SelectorElement>()
            var off = 0
            while (off < input.length) {
                when (input[off]) {
                    ':' -> {
                        val startOff = off + 1
                        off++
                        while (off < input.length && input[off] in listOf('0'..'9', 'a'..'f', 'A'..'F').flatten())
                            off++
                        path.add(ISO7816SelectorById(input.substring(startOff, off).toInt(16)))
                    }
                    '#' -> {
                        val startOff = off + 1
                        off++
                        while (off < input.length && input[off] in listOf('0'..'9', 'a'..'f', 'A'..'F').flatten())
                            off++
                        path.add(ISO7816SelectorByName(ImmutableByteArray.fromHex(input.substring(startOff, off))))
                    }
                    else -> throw IllegalArgumentException("Bad path $input")
                }
            }
            return ISO7816Selector(path)
        }
    }
}
