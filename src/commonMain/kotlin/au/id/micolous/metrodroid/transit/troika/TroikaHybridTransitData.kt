/*
 * TroikaTransitData.kt
 *
 * Copyright 2015-2016 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.transit.troika

import au.id.micolous.metrodroid.card.classic.ClassicCard
import au.id.micolous.metrodroid.card.classic.ClassicCardTransitFactory
import au.id.micolous.metrodroid.card.classic.ClassicSector
import au.id.micolous.metrodroid.key.ClassicSectorKey
import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.CardInfo
import au.id.micolous.metrodroid.transit.Subscription
import au.id.micolous.metrodroid.transit.TransitBalance
import au.id.micolous.metrodroid.transit.TransitData
import au.id.micolous.metrodroid.transit.TransitIdentity
import au.id.micolous.metrodroid.transit.Trip
import au.id.micolous.metrodroid.transit.podorozhnik.PodorozhnikTransitData
import au.id.micolous.metrodroid.transit.serialonly.StrelkaTransitData
import au.id.micolous.metrodroid.ui.HeaderListItem
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.HashUtils

/**
 * Hybrid cards containing both Troika and Podorozhnik.
 */

@Parcelize
class TroikaHybridTransitData (private val mTroika: TroikaTransitData,
                               private val mPodorozhnik: PodorozhnikTransitData?,
                               private val mStrelka: StrelkaTransitData?): TransitData() {

    override val serialNumber: String?
        get() = mTroika.serialNumber

    // This is Podorozhnik serial number. Combined card
    // has both serial numbers and both are printed on it.
    // We show Troika number as main serial as it's shorter
    // and printed in larger letters.
    // This is Podorozhnik serial number. Combined card
    // has both serial numbers and both are printed on it.
    // We show Troika number as main serial as it's shorter
    // and printed in larger letters.
    override val info: List<ListItem>?
        get() {
            val items = mutableListOf<ListItem>()

            val troikaItems = mTroika.info

            if (troikaItems != null && !troikaItems.isEmpty()) {
                items.add(HeaderListItem(R.string.card_name_troika))
                items.addAll(troikaItems)
            }

            if (mPodorozhnik != null) {
                items.add(HeaderListItem(R.string.card_name_podorozhnik))
                items.add(ListItem(R.string.card_number, mPodorozhnik.serialNumber))

                items += mPodorozhnik.info.orEmpty()
            }

            if (mStrelka != null) {
                items.add(HeaderListItem(R.string.card_name_strelka))
                items.add(ListItem(R.string.card_number, mStrelka.serialNumber))

                items += mStrelka.extraInfo
            }

            return items.ifEmpty { null }
        }

    override val cardName: String
        get() {
            var nameRes = R.string.card_name_troika
            if (mStrelka != null)
                nameRes = R.string.card_name_troika_strelka_hybrid
            if (mPodorozhnik != null)
                nameRes = R.string.card_name_troika_podorozhnik_hybrid
            return Localizer.localizeString(nameRes)
        }

    override val trips: List<Trip>?
        get() = mPodorozhnik?.trips.orEmpty() + mTroika.trips

    override val balances: List<TransitBalance>?
        get() = listOfNotNull(mTroika.balance) + mPodorozhnik?.balances.orEmpty()

    override val subscriptions: List<Subscription>?
        get() = mTroika.subscriptions

    override val warning: String?
        get() = mTroika.warning

    override fun getRawFields(level: RawLevel): List<ListItem>? = mTroika.debug

    private constructor(card: ClassicCard) : this(
            mTroika = TroikaTransitData(card),
            mPodorozhnik = if (PodorozhnikTransitData.FACTORY.check(card))
                PodorozhnikTransitData(card)
            else
                null,
            mStrelka = if (StrelkaTransitData.FACTORY.check(card))
                StrelkaTransitData.parse(card)
            else
                null
    )

    companion object {
        val mainBlocks = listOf(8,4)

        val FACTORY: ClassicCardTransitFactory = object : ClassicCardTransitFactory {
            override val earlySectors: Int
                get() = 2

            override val allCards: List<CardInfo>
                get() = listOf(TroikaTransitData.CARD_INFO)

            override fun parseTransitIdentity(card: ClassicCard): TransitIdentity {
                var nameRes = R.string.card_name_troika
                if (StrelkaTransitData.FACTORY.check(card))
                    nameRes = R.string.card_name_troika_strelka_hybrid
                if (PodorozhnikTransitData.FACTORY.check(card))
                    nameRes = R.string.card_name_troika_podorozhnik_hybrid
                val block = mainBlocks.find { TroikaBlock.check(card.getSector(it).getBlock(0).data) }!!
                return TransitIdentity(Localizer.localizeString(nameRes),
                        TroikaBlock.formatSerial(TroikaBlock.getSerial(card.getSector(block).getBlock(0).data)))
            }

            override fun parseTransitData(card: ClassicCard): TransitData {
                return TroikaHybridTransitData(card)
            }

            override fun check(card: ClassicCard): Boolean = 
                    mainBlocks.any { TroikaBlock.check(card.getSector(it).getBlock(0).data) }

            override fun earlyCheck(sectors: List<ClassicSector>): Boolean {
                return HashUtils.checkKeyHash(sectors[1], "troika",
                        "0045ccfe4749673d77273162e8d53015") >= 0
            }

            override fun isDynamicKeys(sectors: List<ClassicSector>, sectorIndex: Int,
                                       keyType: ClassicSectorKey.KeyType): Boolean =
                    try {
                        StrelkaTransitData.FACTORY.earlyCheck(sectors) && StrelkaTransitData.FACTORY.isDynamicKeys(sectors, sectorIndex, keyType)
                    } catch (e: Exception) {
                        false
                    }
        }
    }
}
