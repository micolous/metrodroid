/*
 * ClassicCardKeys.kt
 *
 * Copyright 2012-2014 Eric Butler <eric@codebutler.com>
 * Copyright 2015-2018 Michael Farrell <micolous+git@gmail.com>
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

import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.json.*

/**
 * Helper for access to MIFARE Classic keys.
 *
 * See https://github.com/micolous/metrodroid/wiki/Importing-MIFARE-Classic-keys for details about
 * the data formats implemented here.
 */
class ClassicCardKeys(override var uid: String?,
                      override val keys: Map<Int, List<ClassicSectorKey>>,
                      override val sourceDataLength: Int) : ClassicKeysImpl() {

    val keyType: ClassicSectorKey.KeyType?
        get() {
            val distinct = keys.values.flatten().map { it.type }.distinct()
            return when (distinct.size) {
                0 -> null
                1 -> distinct[0]
                else -> ClassicSectorKey.KeyType.MULTIPLE
            }
        }

    override val description
        get() = uid


    /**
     * Serialises this class to JSON format.
     *
     * Must be overridden by subclasses.
     * @return A JSON blob with all the sectors associated with this card.
     */
    override fun toJSON(): JsonObject {
        if (uid == null)
            return baseJson
        val add = buildJsonObject {
            CardKeys.JSON_TAG_ID_KEY to uid
        }
        return JsonObject(baseJson + add)
    }

    fun setAllKeyTypes(kt: ClassicSectorKey.KeyType) {
        keys.values.flatten().forEach { it.type = kt }
    }

    override val type = CardKeys.TYPE_MFC

    /**
     * Returns a localised description of the key file type and its contents.
     */
    override val fileType: String
        get() = Localizer.localizePlural(R.plurals.keytype_mfc, keyCount, keyCount)

    companion object {
        /**
         * Reads ClassicCardKeys in "raw" (farebotkeys) format.
         *
         * See https://github.com/micolous/metrodroid/wiki/Importing-MIFARE-Classic-keys#raw-farebotkeys
         */
        fun fromDump(keyData: ImmutableByteArray) = fromDump(keyData, ClassicSectorKey.KeyType.UNKNOWN)

        fun fromDump(keyData: ImmutableByteArray, keyType: ClassicSectorKey.KeyType): ClassicCardKeys {
            val keys = mutableMapOf<Int, List<ClassicSectorKey>>()

            val numSectors = keyData.size / ClassicSectorKey.CLASSIC_KEY_LEN
            for (i in 0 until numSectors) {
                val start = i * ClassicSectorKey.CLASSIC_KEY_LEN
                val key = keyData.sliceOffLen(start, ClassicSectorKey.CLASSIC_KEY_LEN)
                keys[i] = listOf(ClassicSectorKey.fromDump(key, keyType, "from-dump"))
            }

            return ClassicCardKeys(uid = null, keys = keys, sourceDataLength = keyData.size)
        }

        /**
         * Reads ClassicCardKeys from the internal (JSON) format.
         *
         * See https://github.com/micolous/metrodroid/wiki/Importing-MIFARE-Classic-keys#json
         */
        fun fromJSON(json: JsonObject, defaultBundle: String) =
            ClassicCardKeys(uid = json[CardKeys.JSON_TAG_ID_KEY]?.jsonPrimitive?.contentOrNull,
                    keys = keysFromJSON(json, true, defaultBundle).mapValues { (_, keys) -> keys.filterIsInstance<ClassicSectorKey>() },
                    sourceDataLength = json.toString().length)
    }
}
