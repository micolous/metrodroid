/*
 * EdyTransitData.kt
 *
 * Copyright 2013 Chris Norden
 * Copyright 2013-2015 Eric Butler <eric@codebutler.com>
 * Copyright 2016-2018 Michael Farrell <micolous+git@gmail.com>
 * Copyright 2018 Google Inc.
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

package au.id.micolous.metrodroid.transit.edy

import au.id.micolous.metrodroid.time.Epoch
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.transit.*
import au.id.micolous.metrodroid.util.NumberUtils
import au.id.micolous.metrodroid.card.CardType
import au.id.micolous.metrodroid.card.felica.FelicaCard
import au.id.micolous.metrodroid.card.felica.FelicaCardTransitFactory
import au.id.micolous.metrodroid.card.felica.FelicaConsts
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.util.ImmutableByteArray

@Parcelize
class EdyTransitData (override val trips: List<EdyTrip>,
                      private val mSerialNumber: ImmutableByteArray?,
                      private val mCurrentBalance: Int?): TransitData() {

    public override val balance: TransitCurrency?
        get() = if (mCurrentBalance != null) TransitCurrency.JPY(mCurrentBalance) else null

    override val serialNumber: String?
        get() {
            if (mSerialNumber == null)
                return null
            return NumberUtils.groupString(mSerialNumber.toHexString().toUpperCase(),
                    " ", 4, 4, 4)
        }

    override val cardName: String
        get() = "Edy"

    companion object {
        const val SYSTEMCODE_EDY_EMPTY = 0x04c0

        const val SYSTEMCODE_EDY = FelicaConsts.SYSTEMCODE_COMMON
        const val SERVICE_EDY_ID = 0x110B
        const val SERVICE_EDY_BALANCE = 0x1317
        const val SERVICE_EDY_HISTORY = 0x170F

        private val CARD_INFO = CardInfo(
                imageId = R.drawable.edy_card,
                name = R.string.card_name_edy,
                locationId = R.string.location_tokyo,
                region = TransitRegion.JAPAN,
                cardType = CardType.FeliCa)

        const val FELICA_MODE_EDY_DEBIT = 0x20
        const val FELICA_MODE_EDY_CHARGE = 0x02
        const val FELICA_MODE_EDY_GIFT = 0x04
        internal val EPOCH = Epoch.local(2000, MetroTimeZone.TOKYO)

        private fun parse(card: FelicaCard): EdyTransitData? {
            val system = card.getSystem(SYSTEMCODE_EDY) ?: return null
            // card ID is in block 0, bytes 2-9, big-endian ordering
            val mSerialNumber = system.getService(SERVICE_EDY_ID)?.blocks?.get(0)
                    ?.data?.sliceOffLen(2, 8)

            // current balance info in block 0, bytes 0-3, little-endian ordering
            val mCurrentBalance = system.getService(SERVICE_EDY_BALANCE)
                    ?.blocks?.get(0)?.data?.byteArrayToIntReversed(0, 3)

            // now read the transaction history
            val serviceHistory = system.getService(SERVICE_EDY_HISTORY)

            // Read blocks in order
            val trips = serviceHistory?.blocks?.map { EdyTrip.parse(it) }.orEmpty()

            return EdyTransitData(trips = trips,
                    mCurrentBalance = mCurrentBalance,
                    mSerialNumber = mSerialNumber)
        }

        val FACTORY: FelicaCardTransitFactory = object : FelicaCardTransitFactory {
            override val allCards: List<CardInfo>
                get() = listOf(CARD_INFO)

            override fun earlyCheck(systemCodes: List<Int>) = SYSTEMCODE_EDY in systemCodes

            override fun parseTransitData(card: FelicaCard) = parse(card)

            override fun parseTransitIdentity(card: FelicaCard) = TransitIdentity("Edy", null)
        }
    }
}

