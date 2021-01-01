/*
 * CardKeysFromFiles.kt
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

package au.id.micolous.metrodroid.key

import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.json.*

interface CardKeysFileReader {
    fun readFile(fileName: String): String?

    fun listFiles(dir: String): List<String>?
}

class CardKeysFromFiles(private val fileReader: CardKeysFileReader) : CardKeysRetriever {
    data class CardKeyRead(val id: Int, val tagId: String, val cardType: String,
                           val keyData: String, val fileName: String,
                           val parsed: JsonObject)

    override fun forClassicStatic(): ClassicStaticKeys? {
        val list = makeCursor(listOf("static"))
        var keys: ClassicStaticKeys? = null

        // Static key requests should give all of the static keys.
        for (cursor in list.filter { it.cardType == CardKeys.TYPE_MFC_STATIC }) {
            try {
                val nk = ClassicStaticKeys.fromJSON(cursor.parsed, cursor.fileName)
                        ?: continue
                if (keys == null)
                    keys = nk
                else
                    keys += nk
            } catch (ignored: Exception) {
            }
        }
        return keys
    }

    override fun forID(id: Int) =
            keyForId(listOf("classic", "static"), id)

    fun getKeyList(): List<CardKeyRead> = makeCursor(listOf("classic", "static"))

    override fun forTagID(tagID: ImmutableByteArray): CardKeys? =
            fromEmbed("classic", tagID.toHexString())

    private fun fromEmbed(dir: String, name: String): CardKeys? {
        return try {
            val inputStream = fileReader.readFile("$dir/$name.json") ?: return null
            val k = CardKeys.jsonParser.parseToJsonElement(inputStream).jsonObject
            CardKeys.fromJSON(k, "$dir/$name")
        } catch (e: Exception) {
            null
        }
    }

    private fun keyForId(dirs: List<String>, targetId: Int): CardKeys? {
        var ctr = START_COUNTER
        for (dir in dirs) {
            try {
                for (file in fileReader.listFiles(dir).orEmpty()) {
                    if (!file.endsWith(".json"))
                        continue
                    ctr--
                    if (ctr == targetId) {
                        val inputStream = fileReader.readFile("$dir/$file")
                        val k = CardKeys.jsonParser.parseToJsonElement(inputStream ?: return null).jsonObject
                        return CardKeys.fromJSON(k, "$dir/$file")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key retrieve failed", e)
            }
        }
        return null
    }

    private fun makeCursor(dirs: List<String>): List<CardKeyRead> {
        var ctr = START_COUNTER
        val cur = mutableListOf<CardKeyRead>()
        for (dir in dirs) {
            try {
                for (it in fileReader.listFiles(dir).orEmpty().filter { it.endsWith(".json") })
                    try {
                        ctr--
                        val b = fileReader.readFile("$dir/$it") ?: continue
                        val k = CardKeys.jsonParser.parseToJsonElement(b).jsonObject
                        val type = k[CardKeys.JSON_KEY_TYPE_KEY]?.jsonPrimitive?.contentOrNull
                        val tagId = when (type) {
                            CardKeys.TYPE_MFC_STATIC -> CardKeys.CLASSIC_STATIC_TAG_ID
                            else -> k[CardKeys.JSON_TAG_ID_KEY]?.jsonPrimitive?.contentOrNull
                        } ?: continue
                        cur += CardKeyRead(id = ctr, tagId = tagId, cardType = type ?: "", keyData = b,
                                parsed = k, fileName = "$dir/$it")
                    } catch (e: Exception) {
                        Log.e(TAG, "Key retrieve failed", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Key retrieve failed", e)
            }
        }
        Log.d("CardKeysEmbed", "loaded ${START_COUNTER - ctr} keys for dirs ${dirs.joinToString(", ")}")
        return cur
    }

    companion object {
        private const val START_COUNTER = -10000
        private const val TAG = "CardKeysFromFiles"
    }
}

class CardKeysDummy : CardKeysRetriever {
    override fun forClassicStatic(): ClassicStaticKeys? = null

    override fun forID(id: Int) = null

    override fun forTagID(tagID: ImmutableByteArray): CardKeys? = null
}
