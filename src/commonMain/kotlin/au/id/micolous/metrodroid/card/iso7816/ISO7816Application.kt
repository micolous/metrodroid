/*
 * ISO7816Application.kt
 *
 * Copyright 2018 Michael Farrell <micolous+git@gmail.com>
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


import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.ISO7816Data.TAG_PROPRIETARY_BER_TLV
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.multi.VisibleForTesting
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ISO7816ApplicationCapsule(
        val files: Map<ISO7816Selector, ISO7816File> = emptyMap(),
        val sfiFiles: Map<Int, ISO7816File> = emptyMap(),
        val appFci: ImmutableByteArray? = null,
        val appName: ImmutableByteArray? = null) {
    constructor(mc: ISO7816ApplicationMutableCapsule) : this(
            files = mc.files,
            sfiFiles = mc.sfiFiles,
            appFci = mc.appFci,
            appName = mc.appName
    )
}

class ISO7816ApplicationMutableCapsule(val appFci: ImmutableByteArray?,
                                       val appName: ImmutableByteArray?,
                                       val files: MutableMap<ISO7816Selector, ISO7816File> = mutableMapOf(),
                                       val sfiFiles: MutableMap<Int, ISO7816File> = mutableMapOf()) {
    suspend fun dumpFileSFI(protocol: ISO7816Protocol, sfi: Int, recordLen: Int): ISO7816File? {
        val data = try {
            protocol.readBinary(sfi)
        } catch (e: ISOFileNotFoundException) {
            // Stop processing, the file doesn't exist.
            return null
        } catch (e: ISOSecurityStatusNotSatisfied) {
            // Stop processing, the file is locked.
            return null
        } catch (e: Exception) {
            null // but continue
        }

        val records : MutableMap<Int, ImmutableByteArray> = mutableMapOf()
        var recordEOF = false
        try {
            for (r in 1..255) {
                try {
                    val record = protocol.readRecord(sfi, r.toByte(), recordLen.toByte()) ?: break

                    records[r] = record
                } catch (e: ISOEOFException) {
                    recordEOF = true
                    // End of file, stop here.
                    break
                }
            }
        } catch (e: Exception) {
        }

        if (data == null && records.isEmpty() && !recordEOF)
            return null
        val f = ISO7816File(records = records, binaryData = data)
        sfiFiles[sfi] = f
        return f
    }

    suspend fun dumpAllSfis(protocol: ISO7816Protocol,
                            feedbackInterface: TagReaderFeedbackInterface,
                            start: Int, total: Int,
                            bailOut: ((sfi: Int, file: ISO7816File?) -> Boolean)? = null) {
        var counter = start
        for (sfi in 1..31) {
            feedbackInterface.updateProgressBar(counter++, total)
            val file = dumpFileSFI(protocol, sfi, 0)
            if (bailOut != null && bailOut(sfi, file)) {
                break
            }
        }
    }

    suspend fun dumpFile(protocol: ISO7816Protocol, sel: ISO7816Selector, recordLen: Int): ISO7816File? {
        // Start dumping...
        val fci: ImmutableByteArray?
        try {
            protocol.unselectFile()
        } catch (e: ISO7816Exception) {
            Log.d(TAG, "Unselect failed, trying select nevertheless")
        }

        try {
            fci = sel.select(protocol)
        } catch (e: ISO7816Exception) {
            Log.d(TAG, "Select failed, aborting")
            return null
        }

        val data = protocol.readBinary()
        val records = mutableMapOf<Int, ImmutableByteArray>()

        for (r in 1..255) {
            try {
                records[r] = protocol.readRecord(r.toByte(), recordLen.toByte()) ?: break
            } catch (e: ISOEOFException) {
                // End of file, stop here.
                break
            }

        }
        val file = ISO7816File(records = records, binaryData = data, fci = fci)
        files[sel] = file
        return file
    }

    fun getFile(sel: ISO7816Selector): ISO7816File? = files[sel]

    fun freeze() = ISO7816ApplicationCapsule(this)

    @Transient
    val appProprietaryBerTlv : ImmutableByteArray? get() = getProprietaryBerTlv(this.appFci)

    companion object {
        private const val TAG = "ISO7816ApplicationMutableCapsule"
    }
}

fun getProprietaryBerTlv(fci: ImmutableByteArray?): ImmutableByteArray? {
    return fci?.let {
        ISO7816TLV.findBERTLV(it, TAG_PROPRIETARY_BER_TLV, true)
    }
}

/**
 * Returns `true` if the [List] of [ISO7816Application] contains the given [appName].
 */
fun List<ISO7816Application>.any(appName: ImmutableByteArray) =
        this.any { it.appName == appName }

/**
 * Returns true if the [List] of [ISO7816Application] contains any of the given [appNames].
 */
fun List<ISO7816Application>.any(appNames: List<ImmutableByteArray>) =
        this.any { appNames.contains(it.appName) }

/**
 * Generic card implementation for ISO7816. This doesn't have many smarts, but dispatches to other
 * readers.
 */
@Serializable(with = ISO7816AppSerializer::class)
abstract class ISO7816Application {
    abstract val generic: ISO7816ApplicationCapsule
    abstract val type: String

    @Transient
    @VisibleForTesting
    val files get() = generic.files
    @Transient
    @VisibleForTesting
    val sfiFiles get() = generic.sfiFiles
    @Transient
    val appName get() = generic.appName
    @Transient
    val appFci get() = generic.appFci

    @Transient
    val appProprietaryBerTlv : ImmutableByteArray? get() = getProprietaryBerTlv(this.appFci)

    @Transient
    open val rawData: List<ListItem>?
        get() = null

    @Transient
    val rawFiles: List<ListItem>
        get() =
            files.map {(selector, file) ->
                var selectorStr = selector.formatString()
                val fileDesc = nameFile(selector)
                if (fileDesc != null)
                    selectorStr = "$selectorStr ($fileDesc)"
                file.showRawData(selectorStr)
            } +
            sfiFiles.map { (sfi, value) ->
                var selectorStr = Localizer.localizeString(R.string.iso7816_sfi, sfi.toString(16))
                val fileDesc = nameSfiFile(sfi)
                if (fileDesc != null)
                    selectorStr = "$selectorStr ($fileDesc)"
                value.showRawData(selectorStr)
            }

    @Transient
    open val manufacturingInfo: List<ListItem>?
        get() = null

    protected open fun nameFile(selector: ISO7816Selector): String? = null

    protected open fun nameSfiFile(sfi: Int): String? = null

    fun getFile(sel: ISO7816Selector): ISO7816File? = files[sel]

    /**
     * If the selector given is a parent of one or more [ISO7816File]s in this application,
     * return true.
     *
     * @param sel The selector to look up.
     */
    fun pathExists(sel: ISO7816Selector): Boolean =
        files.keys.find { it.startsWith(sel) } != null

    fun getSfiFile(sfi: Int): ISO7816File? = sfiFiles[sfi]

    open fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? = null

    open fun parseTransitData(card: ISO7816Card): TransitData? = null

    companion object {
        private const val TAG = "ISO7816Application"
    }
}
