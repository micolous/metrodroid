/*
 * ClassicKeysImpl.kt
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

import au.id.micolous.metrodroid.multi.VisibleForTesting
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.json.*

abstract class ClassicKeysImpl : ClassicKeys {

    override fun isEmpty() = keys.isEmpty()

    protected abstract val keys: Map<Int, List<ClassicSectorAlgoKey>>

    private fun getKeySet(tagId: ImmutableByteArray): Set<String>
            = dedupKeys(keys.map { (secno, keys) -> keys.map { it.resolve(tagId, secno) } } + listOf(WELL_KNOWN_KEYS))

    private fun getProperKeySet(tagId: ImmutableByteArray): Set<String>
            = dedupKeys(keys.map { (secno, keys) -> keys.map { it.resolve(tagId, secno) } })

    private fun dedupKeys(input: Collection<List<ClassicSectorKey>>) = input.flatten()
            .map { it.key.toHexString() }.toSet()

    internal val keyCount: Int
        get () = keys.values.flatten().filterIsInstance<ClassicSectorKey>().map { it.key.toHexString() }.distinct().size +
                keys.values.flatten().filter { it !is ClassicSectorKey }.size

    private val keysJson: JsonArray
        get() = buildJsonArray {
                for ((sector, keys) in keys.entries.sortedBy { it.key }) {
                    keys.map { it.toJSON(sector) }.forEach {
                        +it
                    }
                }
            }

    protected val baseJson: JsonObject
        get() = json {
            KEYS to keysJson
            CardKeys.JSON_KEY_TYPE_KEY to type
        }


    /**
     * Gets all keys for the card.
     *
     * @return All [ClassicSectorKey] for the card.
     */
    override fun getAllKeys(tagId: ImmutableByteArray): List<ClassicSectorKey> = getKeySet(tagId).toList().map {
            ClassicSectorKey.fromDump(
                    ImmutableByteArray.fromHex(it), ClassicSectorKey.KeyType.UNKNOWN, "all-keys")
        }

    @VisibleForTesting
    fun getAllProperKeys(tagId: ImmutableByteArray): List<ClassicSectorKey> = getProperKeySet(tagId).toList().map {
        ClassicSectorKey.fromDump(
                ImmutableByteArray.fromHex(it), ClassicSectorKey.KeyType.UNKNOWN, "all-keys")
    }

    /**
     * Gets the keys for a particular sector on the card.
     *
     * @param sectorNumber The sector number to retrieve the key for
     * @return All candidate [ClassicSectorKey] for that sector, or an empty list if there is
     * no known key, or the sector is out of range.
     */
    override fun getCandidates(sectorNumber: Int, tagId: ImmutableByteArray, preferences: List<String>): List<ClassicSectorKey> =
            ((keys[sectorNumber]?.map { it.resolve(tagId, sectorNumber) } ?: emptyList()) + WELL_KNOWN_KEYS).sortedBy { key ->
                preferences.indexOf(key.bundle).let { if (it == -1) preferences.size else it }
            }

    internal fun getProperCandidates(sectorNumber: Int, tagId: ImmutableByteArray): List<ClassicSectorKey>? = keys[sectorNumber]?.map { it.resolve(tagId, sectorNumber) }

    companion object {
        private const val KEYS = "keys"
        const val TRANSFORM_KEY = "transform"
        private fun wellKnown(b: String, bundle: String): ClassicSectorKey = ClassicSectorKey.fromDump(
                ImmutableByteArray.fromHex(b), ClassicSectorKey.KeyType.A, bundle)

        /**
         * Contains a list of widely used MIFARE Classic keys.
         *
         * None of the keys here are unique to a particular transit card, or to a vendor of transit
         * ticketing systems.
         *
         * Even if a transit operator uses (some) fixed keys, please do not add them here!
         *
         * If you are unable to identify a card by some data on it (such as a "magic string"), then
         * you should use [au.id.micolous.metrodroid.util.HashUtils.checkKeyHash], and include a hashed version of the key in
         * Metrodroid.
         *
         * See [au.id.micolous.metrodroid.transit.smartrider.SmartRiderTransitData.detectKeyType] for an example of how to do this.
         */
        private val WELL_KNOWN_KEYS = listOf(
                wellKnown("ffffffffffff", "well-known-ff"),
                wellKnown("000000000000", "well-known-zero"),
                wellKnown("a0a1a2a3a4a5", "well-known-mad"),
                wellKnown("d3f7d3f7d3f7", "well-known-ndef"),
                wellKnown("ffffffffffffffffffffffffffffffff", "well-known-ff-aes"),
                wellKnown("00000000000000000000000000000000", "well-known-zero-aes"),
                wellKnown("a0a1a2a3a4a5a6a7a0a1a2a3a4a5a6a7", "well-known-mad-aes"),
                wellKnown("d3f7d3f7d3f7d3f7d3f7d3f7d3f7d3f7", "well-known-ndef-aes"))

        fun keysFromJSON(jsonRoot: JsonObject, allowMissingIdx: Boolean, defaultBundle: String):
                Map<Int, List<ClassicSectorAlgoKey>> {
            val keysJSON = jsonRoot[KEYS]!!.jsonArray
            val keys = mutableMapOf<Int, MutableList<ClassicSectorAlgoKey>>()
            for ((i, jsonElement) in keysJSON.withIndex()) {
                val json = jsonElement.jsonObject
                val w = classicFromJSON(json, defaultBundle) ?: continue
                var sectorIndex = json.getPrimitiveOrNull(ClassicSectorKey.SECTOR_IDX)?.intOrNull
                if (sectorIndex == null && allowMissingIdx)
                    sectorIndex = i
                // if allowMissingIdx is false, purposefully trip the exception
                sectorIndex!!

                if (!keys.containsKey(sectorIndex)) {
                    keys[sectorIndex] = mutableListOf()
                }

                keys[sectorIndex]?.add(w)
            }
            return keys
        }

        const val KEY_BUNDLE = "bundle"
        private const val KEY_LEN = 6

        private fun classicFromJSON(json: JsonObject, defaultBundle: String): ClassicSectorAlgoKey? {
            val t = json.getPrimitiveOrNull(ClassicSectorKey.KEY_TYPE)?.contentOrNull
            val kt = when (t) {
                "", null -> ClassicSectorKey.KeyType.UNKNOWN
                ClassicSectorKey.TYPE_KEYA -> ClassicSectorKey.KeyType.A
                ClassicSectorKey.TYPE_KEYB -> ClassicSectorKey.KeyType.B
                else -> ClassicSectorKey.KeyType.UNKNOWN
            }

            return when (json.getPrimitiveOrNull(TRANSFORM_KEY)?.contentOrNull ?: "none") {
                "none" -> {
                    val keyData = ImmutableByteArray.fromHex(json[ClassicSectorKey.KEY_VALUE]!!.content)

                    // Check that the key is the correct length
                    if (keyData.size != KEY_LEN) {
                        throw Exception("Expected $KEY_LEN bytes in key, got ${keyData.size}")
                    }

                    // Checks completed, pass the data back.
                    ClassicSectorKey(type = kt, key = keyData,
                            bundle = json.getPrimitiveOrNull(KEY_BUNDLE)?.contentOrNull ?: defaultBundle)
                }
                "touchngo" -> {
                    TouchnGoKey(type = kt, key = ImmutableByteArray.fromHex(json[ClassicSectorKey.KEY_VALUE]!!.content))
                }		
                else -> null
            }
        }

        fun classicFromJSON(json: String, defaultBundle: String): ClassicSectorAlgoKey? =
                classicFromJSON(CardKeys.jsonParser.parseJson(json).jsonObject, defaultBundle)
    }
}
