package au.id.micolous.metrodroid.transit.troika

import au.id.micolous.metrodroid.multi.Parcelize
import au.id.micolous.metrodroid.util.ImmutableByteArray

// This layout is found on newer single and double-rides
@Suppress("CanBeParameter")
@Parcelize
class TroikaLayoutA(val rawData: ImmutableByteArray,
                    val validityStart : Int = rawData.getBitsFromBuffer(67, 9)) :
        TroikaBlock(rawData,
                // 3 bits unknown
                mValidityLengthMinutes = rawData.getBitsFromBuffer(76, 19),
                // 1 bit unknown
                mLastValidationTime = convertDateTime2016(validityStart, rawData.getBitsFromBuffer(96, 19)),
                // 4 bits unknown
                mLastTransfer = rawData.getBitsFromBuffer(119, 7),
                mRemainingTrips = rawData.getBitsFromBuffer(128, 8),
                mLastValidator = rawData.getBitsFromBuffer(136, 16),
                mLastTransportLeadingCode = rawData.getBitsFromBuffer(126, 2),
                mLastTransportLongCode = rawData.getBitsFromBuffer(152, 8),
                // 32 bits zero
                mCheckSum = rawData.getHexString(8, 5).substring(1, 4),
                mValidityEnd = convertDateTime2016(validityStart, rawData.getBitsFromBuffer(76, 19) - 1),
                mValidityStart = convertDateTime2016(validityStart, 0)
                // missing: expiry date
        )
