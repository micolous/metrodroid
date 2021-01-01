/*
 * Card.kt
 *
 * Copyright 2011-2014 Eric Butler <eric@codebutler.com>
 * Copyright 2016 Michael Farrell <micolous+git@gmail.com>
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

package au.id.micolous.metrodroid.card

import au.id.micolous.metrodroid.card.cepascompat.CEPASCard
import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.desfire.DesfireCard
import au.id.micolous.metrodroid.card.felica.FelicaCard
import au.id.micolous.metrodroid.card.iso7816.ISO7816Card
import au.id.micolous.metrodroid.card.nfcv.NFCVCard
import au.id.micolous.metrodroid.card.ultralight.UltralightCard
import au.id.micolous.metrodroid.multi.NativeThrows
import au.id.micolous.metrodroid.multi.logAndSwiftWrap
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.transit.TransitData

import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray
import kotlinx.serialization.*

abstract class CardProtocol {
    /**
     * Is this a partial or incomplete card read?
     * @return true if there is not complete data in this scan.
     */

    abstract val isPartialRead: Boolean
    /**
     * Gets items to display when manufacturing information is requested for the card.
     */
    open val manufacturingInfo: List<ListItem>?
        get() = null
    /**
     * Gets items to display when raw data is requested for the card.
     */
    open val rawData: List<ListItem>?
        get() = null

    @Transient
    lateinit var scannedAt: TimestampFull
        private set
    @Transient
    lateinit var tagId: ImmutableByteArray
        private set

    /**
     * This is where a card is actually parsed into TransitData compatible data.
     * @return
    */
    abstract fun parseTransitData(): TransitData?

    /**
     * This is where the "transit identity" is parsed, that is, a combination of the card type,
     * and the card's serial number (according to the operator).
     * @return
     */
    abstract fun parseTransitIdentity(): TransitIdentity?

    open fun postCreate(card: Card) {
        scannedAt = card.scannedAt
        tagId = card.tagId
    }
}

@Serializable
class Card(
        val tagId: ImmutableByteArray,
        val scannedAt: TimestampFull,
        val label: String? = null,
        val mifareClassic: ClassicCard? = null,
        val mifareDesfire: DesfireCard? = null,
        val mifareUltralight: UltralightCard? = null,
        val cepasCompat: CEPASCard? = null,
        val felica: FelicaCard? = null,
        val iso7816: ISO7816Card? = null,
        val vicinity: NFCVCard? = null
) {
    @Transient
    val allProtocols: List<CardProtocol>
        get() = listOfNotNull(mifareClassic, mifareDesfire, mifareUltralight, cepasCompat,
                felica, iso7816, vicinity)
    @Transient
    val manufacturingInfo: List<ListItem>?
        get() = allProtocols.mapNotNull { it.manufacturingInfo }.flatten().ifEmpty { null }
    @Transient
    val rawData: List<ListItem>?
        get() = allProtocols.mapNotNull { it.rawData }.flatten().ifEmpty { null }
    @Transient
    val isPartialRead: Boolean
        get() = allProtocols.any { it.isPartialRead }
    @Transient
    val cardType: CardType
        get () = when {
            allProtocols.size > 1 -> CardType.MultiProtocol
            mifareClassic != null -> when (mifareClassic.subType) {
                ClassicCard.SubType.CLASSIC -> CardType.MifareClassic
                ClassicCard.SubType.PLUS -> CardType.MifarePlus
            }
            mifareUltralight != null -> CardType.MifareUltralight
            mifareDesfire != null -> CardType.MifareDesfire
            cepasCompat != null -> CardType.CEPAS
            felica != null -> CardType.FeliCa
            iso7816 != null -> CardType.ISO7816
            vicinity != null -> CardType.Vicinity
            else -> CardType.Unknown
        }

    @NativeThrows
    fun parseTransitIdentity(): TransitIdentity? = logAndSwiftWrap("Card", "parseTransitIdentity failed") lam@{
        for (protocol in allProtocols) {
            val td = protocol.parseTransitIdentity()
            if (td != null)
                return@lam td
        }
        return@lam null
    }

    @NativeThrows
    fun parseTransitData(): TransitData? = logAndSwiftWrap("Card", "parseTransitData failed") lam@{
        for (protocol in allProtocols) {
            val td = protocol.parseTransitData()
            if (td != null)
                return@lam td
        }
        return@lam null
    }

    // Convenience for Swift interop
    val safeBalance : TransitCurrency? by lazy {
        try {
            parseTransitData()?.balances?.first()?.balance
        } catch (e: Exception) {
            null
        }
    }

    // Convenience for Swift interop
    val safeTransitIdentity: TransitIdentity? by lazy {
        try {
            parseTransitIdentity()
        } catch (e: Exception) {
            null
        }
    }

    init {
        allProtocols.forEach {
            it.postCreate(this)
        }
    }
}
