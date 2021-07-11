/*
 * CEPASCard.kt
 *
 * Copyright 2011 Sean Cross <sean@chumby.com>
 * Copyright 2013-2014 Eric Butler <eric@codebutler.com>
 * Copyright 2018 Michael Farrell <micolous+git@gmail.com>
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

package au.id.micolous.metrodroid.card.cepas

import au.id.micolous.metrodroid.card.TagReaderFeedbackInterface
import au.id.micolous.metrodroid.card.iso7816.*
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.time.TimestampFormatter
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.ezlink.CEPASPurse
import au.id.micolous.metrodroid.transit.ezlink.EZLinkTransitFactory
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.ui.ListItemRecursive
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class CEPASApplication(
        override val generic: ISO7816ApplicationCapsule,
        private val purses: Map<Int, ImmutableByteArray>,
        private val histories: Map<Int, ImmutableByteArray>) : ISO7816Application() {

    @Transient
    override val rawData: List<ListItem>?
        get() = purses.map { (key, value) ->
            ListItemRecursive.collapsedValue(
                Localizer.localizeString(R.string.cepas_purse_num, key),
                    value.toHexDump())
        } + histories.map { (key, value) ->
            ListItemRecursive.collapsedValue(
                Localizer.localizeString(R.string.cepas_purse_num_history, key),
                    value.toHexDump())
        }

    // FIXME: What about other purses?
    @Transient
    override val manufacturingInfo: List<ListItem>?
        get() {
            val purseRaw = getPurse(3) ?: return listOf(
                    HeaderListItem(R.string.cepas_purse_info),
                    ListItem(R.string.error, R.string.unknown))
            val purse = CEPASPurse(purseRaw)

            return listOf(
                    ListItem(R.string.cepas_version, purse.cepasVersion.toString()),
                    ListItem(R.string.cepas_purse_id, "3"),
                    ListItem(R.string.cepas_purse_status, purse.purseStatus.toString()),
                    ListItem(R.string.cepas_purse_balance, purse.purseBalance.toString()),

                    ListItem(R.string.cepas_purse_creation_date,
                            TimestampFormatter.longDateFormat(purse.purseCreationDate)),
                    ListItem(R.string.expiry_date,
                            TimestampFormatter.longDateFormat(purse.purseExpiryDate)),
                    ListItem(R.string.cepas_autoload_amount, purse.autoLoadAmount.toString()),
                    ListItem(R.string.cepas_can, purse.can.toHexDump()),
                    ListItem(R.string.cepas_csn, purse.csn.toHexDump()),

                    HeaderListItem(R.string.cepas_last_txn_info),
                    ListItem(R.string.cepas_trp, purse.lastTransactionTRP.toString()),
                    ListItem(R.string.cepas_credit_trp, purse.lastCreditTransactionTRP.toString()),
                    ListItem(R.string.cepas_credit_header, purse.lastCreditTransactionHeader.toHexDump()),
                    ListItem(R.string.cepas_debit_options, purse.lastTransactionDebitOptionsByte.toString()),

                    HeaderListItem(R.string.cepas_other_purse_info),
                    ListItem(R.string.cepas_logfile_record_count, purse.logfileRecordCount.toString()),
                    ListItem(R.string.cepas_issuer_data_length, purse.issuerDataLength.toString()),
                    ListItem(R.string.cepas_issuer_data, purse.issuerSpecificData.toHexDump()))
        }


    override fun parseTransitIdentity(card: ISO7816Card): TransitIdentity? {
        return if (EZLinkTransitFactory.check(this)) EZLinkTransitFactory.parseTransitIdentity(this) else null
    }

    override fun parseTransitData(card: ISO7816Card): TransitData? {
        return if (EZLinkTransitFactory.check(this)) EZLinkTransitFactory.parseTransitData(this) else null
    }

    fun getPurse(purseId: Int): ImmutableByteArray? = purses[purseId]

    fun getHistory(purseId: Int): ImmutableByteArray? = histories[purseId]

    @Transient
    override val type: String
        get() = TYPE

    companion object {
        private const val TAG = "CepasApplication"
        const val TYPE = "cepas"

        private fun setProgress(feedbackInterface: TagReaderFeedbackInterface, value: Int) {
            feedbackInterface.updateProgressBar(value, 64)
        }

        suspend fun dumpTag(iso7816Tag: ISO7816Protocol, capsule: ISO7816ApplicationMutableCapsule,
                            feedbackInterface: TagReaderFeedbackInterface): CEPASApplication? {
            val cepasPurses = mutableMapOf<Int, ImmutableByteArray>()
            val cepasHistories = mutableMapOf<Int, ImmutableByteArray>()
            var isValid = false
            val numPurses = 16

            val cepasTag = CEPASProtocol(iso7816Tag)

            try {
                iso7816Tag.selectById(0x4000)
            } catch (e: IllegalStateException) {
                Log.d(TAG, "CEPAS file not found [$e] -- this is expected for non-CEPAS ISO7816 cards")
                return null
            } catch (e: ISO7816Exception) {
                Log.d(TAG, "CEPAS file not found [$e] -- this is expected for non-CEPAS ISO7816 cards")
                return null
            }

            for (purseId in 0 until numPurses) {
                val purse = cepasTag.getPurse(purseId)
                if (purse != null) {
                    cepasPurses[purseId] = ImmutableByteArray(purse)
                    if (!isValid) {
                        val cardInfo = EZLinkTransitFactory.earlyCardInfo(purse)
                        feedbackInterface.updateStatusText(Localizer.localizeString(R.string.card_reading_type,
                                cardInfo.name))
                        feedbackInterface.showCardType(cardInfo)
                    }
                    isValid = true
                }
                if (isValid)
                    setProgress(feedbackInterface, purseId)
            }

            if (!isValid)
                return null

            for (historyId in 0 until numPurses) {
                var history: ImmutableByteArray? = null
                if (cepasPurses.containsKey(historyId)) {
                    history = cepasTag.getHistory(historyId)
                }
                if (history != null)
                    cepasHistories[historyId] = ImmutableByteArray(history)
                setProgress(feedbackInterface, historyId + numPurses)
            }

            for (i in 0x0..31) {
                try {
                    capsule.dumpFile(iso7816Tag, ISO7816Selector.makeSelector(0x3f00, 0x4000, i), 0)
                } catch (ex: Exception) {

                    Log.d(TAG, "Couldn't read :3f00:4000:" + i.toString(16))
                }

                setProgress(feedbackInterface, i + 2 * numPurses)
            }
            return CEPASApplication(capsule.freeze(), cepasPurses, cepasHistories)
        }
    }
}
