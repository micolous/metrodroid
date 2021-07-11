/*
 * EmvCard.kt
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

package au.id.micolous.metrodroid.card.emv

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.*
import au.id.micolous.metrodroid.card.iso7816.ISO7816Data.TAG_DISCRETIONARY_DATA
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.emv.EmvData.PARSER_IGNORED_AID_PREFIX
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_AMOUNT_AUTHORISED
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_AMOUNT_OTHER
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_PDOL
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TERMINAL_COUNTRY_CODE
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TERMINAL_TRANSACTION_QUALIFIERS
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TERMINAL_VERIFICATION_RESULTS
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TRANSACTION_CURRENCY_CODE
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TRANSACTION_DATE
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_TRANSACTION_TYPE
import au.id.micolous.metrodroid.transit.emv.EmvData.TAG_UNPREDICTABLE_NUMBER
import au.id.micolous.metrodroid.transit.emv.EmvTransitFactory
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

private const val LOG_FORMAT = 0x9f4f
private const val PIN_RETRY = 0x9f17
private const val TYPE_MAIN = "emv-main"
private const val TYPE_ANCHOR = "emv-anchor"

@Serializable
class EmvCard(override val generic: ISO7816ApplicationCapsule) : ISO7816Application() {
    @Transient
    override val type: String
        get() = TYPE_ANCHOR
}

@Serializable
class EmvCardMain internal constructor(
        override val generic: ISO7816ApplicationCapsule,
        private val dataResponse: Map<String, ImmutableByteArray>,
        val gpoResponse: ImmutableByteArray?) : ISO7816Application() {
    @Transient
    override val type: String
        get() = TYPE_MAIN

    @Transient
    val logFormat: ImmutableByteArray?
        get() = dataResponse[LOG_FORMAT.toString(16)]
    @Transient
    val pinTriesRemaining: ImmutableByteArray?
        get() = dataResponse[PIN_RETRY.toString(16)]

    @Transient
    val parserIgnore: Boolean
        get() = generic.appName?.let { appName ->
            PARSER_IGNORED_AID_PREFIX.indexOfFirst {
                appName.startsWith(it) } >= 0
        } ?: false

    override fun parseTransitData(card: ISO7816Card) = if (parserIgnore) {
        null
    } else {
        EmvTransitFactory.parseTransitData(this)
    }

    override fun parseTransitIdentity(card: ISO7816Card) = if (parserIgnore) {
        null
    } else {
        EmvTransitFactory.parseTransitIdentity(this)
    }

    @Transient
    override val rawData get() = super.rawData.orEmpty() +
            ListItem(R.string.emv_gpo_response, gpoResponse?.toHexString() ?: "null") +
            (dataResponse.map { (k, v) -> ListItem(Localizer.localizeString(R.string.emv_data_response, k), v.toHexString()) })

    fun getAllTlv(): List<ImmutableByteArray> {
        val res = mutableListOf<ImmutableByteArray>()
        val a5 = appProprietaryBerTlv
        if (a5 != null) {
            res += listOfNotNull(
                    gpoResponse,
                    a5,
                    ISO7816TLV.findBERTLV(a5, TAG_DISCRETIONARY_DATA, true))
        }

        // SFI's
        res += (1..10).flatMap { file ->
            getSfiFile(file)?.recordList
                    ?: emptyList()
        }
        return res.filter { it.isNotEmpty() }
    }
}

class EmvFactory : ISO7816ApplicationFactory {
    override val typeMap: Map<String, KSerializer<out ISO7816Application>>
        get() = mapOf(TYPE_ANCHOR to EmvCard.serializer(), TYPE_MAIN to EmvCardMain.serializer())
    override val applicationNames get() = listOf(
            ImmutableByteArray.fromASCII("1PAY.SYS.DDF01"),
            ImmutableByteArray.fromASCII("2PAY.SYS.DDF01")
    )

    override val fixedAppIds get() = false

    override suspend fun dumpTag(protocol: ISO7816Protocol,
                                 capsule: ISO7816ApplicationMutableCapsule,
                                 feedbackInterface: TagReaderFeedbackInterface): List<ISO7816Application>? {
        feedbackInterface.updateStatusText(Localizer.localizeString(R.string.card_reading_emv))
        feedbackInterface.showCardType(EmvTransitFactory.CARD_INFO)
        feedbackInterface.updateProgressBar(0, 32)
        var failed = 0
        capsule.dumpAllSfis(protocol, feedbackInterface, 0, 64) { sfi, file ->
            // Bail-out condition
            if (file == null) {
                failed++
                // Android Pay drops the connection if we take too long.
                (sfi == 3 && failed == 3)
            } else {
                false
            }
        }

        val fci = capsule.appFci

        Log.d(TAG, "EMV: FCI=$fci")

        // Iterate over apps
        //
        // This is useful for multi-application cards. For example, most Australian cards
        // typically have two EFTPOS applications (savings and cheque), followed by a Mastercard
        // or Visa application.
        val mainApps = mutableListOf<ISO7816Application>(EmvCard(generic = capsule.freeze()))

        val discretionaryData = capsule.appProprietaryBerTlv?.let {
            ISO7816TLV.findBERTLV(it,TAG_DISCRETIONARY_DATA, true)
        }

        if (discretionaryData != null) {
            for (appInfo in ISO7816TLV.findRepeatedBERTLV(discretionaryData, "61", true)) {
                val aid = ISO7816TLV.findBERTLV(appInfo, "4f", false) ?: continue
                Log.d(TAG, "EMV: MAN=$aid")

                val mainAppFci = protocol.selectByNameOrNull(aid)
                val mainAppCapsule = ISO7816ApplicationMutableCapsule(appFci = mainAppFci,
                        appName = aid)
                mainAppCapsule.dumpAllSfis(protocol, feedbackInterface, 32, 64)
                val gpoResponse = readGpo(protocol, mainAppFci)

                val dr = mutableMapOf<String, ImmutableByteArray>()
                for (p1p2 in listOf(0x9f13, PIN_RETRY, 0x9f36, LOG_FORMAT)) {
                    val r = readData(protocol, p1p2)
                    if (r != null)
                        dr[p1p2.toString(16)] = r
                }

                mainApps.add(EmvCardMain(generic = mainAppCapsule.freeze(),
                        dataResponse = dr,
                        gpoResponse = gpoResponse))
            }
        }

        return mainApps.toList()
    }

    private suspend fun readData(tag: ISO7816Protocol, p1p2: Int) = try {
        tag.sendRequest(ISO7816Protocol.CLASS_80, 0xca.toByte(),
                (p1p2 shr 8).toByte(), p1p2.toByte(), 0)
    } catch (e: Exception) {
        null
    }

    private suspend fun readGpo(tag: ISO7816Protocol, mainAppData: ImmutableByteArray?): ImmutableByteArray? {
        if (mainAppData == null)
            return null
        Log.d(TAG, "AD = $mainAppData")

        val a5 = getProprietaryBerTlv(mainAppData) ?: return null

        val pdolTemplate = ISO7816TLV.findBERTLV(a5, TAG_PDOL, false) ?: return null

        Log.d(TAG, "PDOL = $pdolTemplate")

        var pdolFilled = ImmutableByteArray.empty()
        ISO7816TLV.pdolIterate(pdolTemplate) { id, len ->
            val contents = when (id.toHexString()) {
                // Currency: USD
                TAG_TRANSACTION_CURRENCY_CODE -> ImmutableByteArray.fromHex("0840")
                TAG_TERMINAL_VERIFICATION_RESULTS -> ImmutableByteArray(5)
                // Transaction Date: 2018-01-01
                TAG_TRANSACTION_DATE -> ImmutableByteArray.fromHex("180101")
                // Transaction Type
                TAG_TRANSACTION_TYPE -> ImmutableByteArray(1)
                // Amount, Authorised: 0 cents
                TAG_AMOUNT_AUTHORISED -> ImmutableByteArray(6)
                // Amount, Other: 0 cents
                TAG_AMOUNT_OTHER -> ImmutableByteArray(6)
                // Country: USA
                TAG_TERMINAL_COUNTRY_CODE -> ImmutableByteArray.fromHex("0840")
                TAG_UNPREDICTABLE_NUMBER -> ImmutableByteArray.fromHex("08130CE7")
                TAG_TERMINAL_TRANSACTION_QUALIFIERS -> ImmutableByteArray.fromHex("F620C000")
                else -> ImmutableByteArray.empty()
            }
            val adjusted = when {
                contents.size > len -> contents.sliceArray((contents.size - len)..contents.size)
                contents.size < len -> ImmutableByteArray(len - contents.size) { 0 } + contents
                else -> contents
            }
            pdolFilled += adjusted
        }

        val gpoRequest = ImmutableByteArray.ofB(0x83, pdolFilled.size) + pdolFilled
        try {
            return tag.sendRequest(ISO7816Protocol.CLASS_80, 0xa8.toByte(),
                    0, 0, 0, gpoRequest)
        } catch (e: Exception) {
            return null
        }
    }
}

private const val TAG = "EmvCard"
