package au.id.micolous.metrodroid.transit.troika

import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.transit.Subscription
import au.id.micolous.metrodroid.transit.TransitBalance
import au.id.micolous.metrodroid.transit.TransitBalanceStored
import au.id.micolous.metrodroid.transit.TransitCurrency
import au.id.micolous.metrodroid.ui.ListItem
import au.id.micolous.metrodroid.util.ImmutableByteArray

// This is e-purse layout
@Parcelize
internal class TroikaPurseE5(val rawData: ImmutableByteArray) : TroikaBlock(
        rawData,
        mExpiryDate = convertDate2019(rawData.getBitsFromBuffer(61, 13)),
        mLastValidationTime = convertDateTime2019(0, rawData.getBitsFromBuffer(128, 23)),
        mLastValidator = rawData.getBitsFromBuffer(186, 16),

        mLastTransfer = null,
        mLastTransportLeadingCode = null,
        mLastTransportLongCode = null,
        mFareDesc = null,
//        mLastTransfer = rawData.getBitsFromBuffer(171, 7),
 //       mLastTransportLeadingCode = rawData.getBitsFromBuffer(178, 2),
   //     mLastTransportLongCode = rawData.getBitsFromBuffer(180, 8),
     //   mFareDesc = when (rawData.getBitsFromBuffer(210, 2)) {
       //     1 -> Localizer.localizeString(R.string.troika_fare_single)
      //      2 -> Localizer.localizeString(R.string.troika_fare_90mins)
      //      else -> null
      //  }
        mCheckSum = rawData.getHexString(28, 4)
) {

    /**
     * Balance of the card, in kopeyka (0.01 RUB).
     */
    private val mBalance get() = rawData.getBitsFromBuffer(167, 19)

    override val balance: TransitBalance?
        get() = TransitBalanceStored(
                TransitCurrency.RUB(mBalance),
                Localizer.localizeString(R.string.card_name_troika),
                mExpiryDate)

    override val subscription: Subscription?
        get() = null

    override val lastRefillTime get() = convertDateTime2019(0, rawData.getBitsFromBuffer(84, 23))

    override val debug get() = super.debug + listOf(
            ListItem("Ticket Type 2", "0x" + rawData.getBitsFromBuffer(74, 10).toString(16)),
            ListItem("refillCounter", refillCounter.toString()),
            ListItem("groundTransactionCounter", groundTransactionCounter.toString()),
            ListItem("B", "0x" + rawData.getBitsFromBuffer(117, 11).toString(16)),
            ListItem("C", "0x" + rawData.getBitsFromBuffer(151, 16).toString(16)),
            ListItem("D", "0x" + rawData.getBitsFromBuffer(202, 14).toString(16)),
            ListItem("E", "0x" + rawData.getBitsFromBuffer(223, 1).toString(16))
    )

    val refillCounter get() = rawData.getBitsFromBuffer(107, 10)
    val groundTransactionCounter get() = rawData.getBitsFromBuffer(216, 7)
    val checksum get() = rawData.getHexString(28, 4)
}
