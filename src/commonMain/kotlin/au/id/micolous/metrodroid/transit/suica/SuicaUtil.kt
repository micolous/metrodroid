/*
 * SuicaUtil.kt
 *
 * Copyright 2011 Kazzz
 * Copyright 2014-2015 Eric Butler <eric@codebutler.com>
 * Copyright 2016-2020 Michael Farrell <micolous+git@gmail.com>
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
package au.id.micolous.metrodroid.transit.suica

import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.multi.StringResource
import au.id.micolous.metrodroid.time.Daystamp
import au.id.micolous.metrodroid.time.MetroTimeZone
import au.id.micolous.metrodroid.time.Timestamp
import au.id.micolous.metrodroid.time.TimestampFull
import au.id.micolous.metrodroid.util.ImmutableByteArray
import au.id.micolous.metrodroid.util.NumberUtils

internal object SuicaUtil {

    internal val TZ = MetroTimeZone.TOKYO

    // Services IDs >= 0x1800 that appear on each card in System Code 3.
    //
    // This includes both open and locked services. Older versions of Metrodroid didn't record
    // locked services, which will make old dumps of Hayakaken, ICOCA, nimoca, PiTaPa and SUGOCA
    // ambiguous.
    //
    // Services IDs 0x48 - 0x114a are nearly always the same (except for some locked service
    // codes), but there are enough other differing service codes that cards can be used.
    private val HAYAKAKEN_SERVICES = setOf(
        0x1f88, 0x1f8a, 0x2048, 0x204a, 0x2448, 0x244a, 0x2488, 0x248a, 0x24c8, 0x24ca, 0x2508,
        0x250a, 0x2548, 0x254a)

    private val ICOCA_SERVICES = setOf(
        0x1a48, 0x1a4a, 0x1a88, 0x1a8a, 0x9608, 0x960a)

    // TODO: verify
    private val KITACA_SERVICES = setOf(
        0x1848, 0x184b, 0x2088, 0x208b, 0x20c8, 0x20cb, 0x2108, 0x210b, 0x2148, 0x214b, 0x2188,
        0x218b)

    // TODO: verify
    private val MANACA_SERVICES = setOf(
        0x9888, 0x988b, 0x98cc, 0x98cf, 0x9908, 0x990a, 0x9948, 0x994a, 0x9988, 0x998b)

    private val NIMOCA_SERVICES = setOf(
        0x1f48, 0x1f4a, 0x1f88, 0x1f8a, 0x1fc8, 0x1fca, 0x2008, 0x200a, 0x2048, 0x204a)

    private val PASMO_SERVICES = setOf(
        0x1848, 0x184b, 0x1908, 0x190a, 0x1948, 0x194b, 0x1988, 0x198b, 0x1cc8, 0x1cca, 0x1d08,
        0x1d0a, 0x2308, 0x230a, 0x2348, 0x234b, 0x2388, 0x238b, 0x23c8, 0x23cb)

    // TODO: verify
    private val PITAPA_SERVICES = setOf(
        0x1b88, 0x1b8a, 0x9748, 0x974a)

    // TODO: verify
    private val SUGOCA_SERVICES = setOf(
        0x1f88, 0x1f8a, 0x2048, 0x204b, 0x21c8, 0x21cb, 0x2208, 0x220a, 0x2248, 0x224a, 0x2288,
        0x228a)

    private val SUICA_SERVICES = setOf(
        0x1808, 0x180a, 0x1848, 0x184b, 0x18c8, 0x18ca, 0x1908, 0x190a, 0x1948, 0x194b, 0x1988,
        0x198b, 0x2308, 0x230a, 0x2348, 0x234b, 0x2388, 0x238b, 0x23c8, 0x23cb)

    // TODO: verify
    private val TOICA_SERVICES = setOf(
        0x1848, 0x184b, 0x1e08, 0x1e0a, 0x1e48, 0x1e4a, 0x1e88, 0x1e8a, 0x1e8b, 0x1ecc, 0x1ecf)

    private enum class ICCardType(
        val localName: StringResource,
        val uniqueServices: Set<Int>) {
        Hayakaken(
            R.string.card_name_hayakaken,
            (HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        ICOCA(
            R.string.card_name_icoca,
            (ICOCA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract KITACA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        Kitaca(
            R.string.card_name_kitaca,
            (KITACA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        Manaca(
            R.string.card_name_manaca,
            (MANACA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        Nimoca(
            R.string.card_name_nimoca,
            (NIMOCA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract MANACA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        PASMO(
            R.string.card_name_pasmo,
            (PASMO_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract KITACA_SERVICES
                subtract ICOCA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        PiTaPa(
            R.string.card_name_pitapa,
            (PITAPA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES
                subtract TOICA_SERVICES)
        ),
        Suica(
            R.string.card_name_suica,
            (SUICA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract TOICA_SERVICES)
        ),
        TOICA(
            R.string.card_name_toica,
            (TOICA_SERVICES
                subtract HAYAKAKEN_SERVICES
                subtract ICOCA_SERVICES
                subtract KITACA_SERVICES
                subtract MANACA_SERVICES
                subtract NIMOCA_SERVICES
                subtract PASMO_SERVICES
                subtract PITAPA_SERVICES
                subtract SUGOCA_SERVICES
                subtract SUICA_SERVICES)
        );

        init {
            require(uniqueServices.isNotEmpty()) {
                "Japan IC cards need at least one unique service code"
            }
        }
    }

    /**
     * Gets a [StringResource] describing the name of the card, or `null` if unknown or
     * ambiguous.
     */
    fun getCardName(services: Set<Int>) = ICCardType.values().map {
        Pair(it.localName, (it.uniqueServices intersect services).size)
    }.singleOrNull {
        it.second > 0
    }?.first

    fun extractDate(isProductSale: Boolean, data: ImmutableByteArray): Timestamp? {
        if (data.byteArrayToInt(4, 2) == 0) {
            return null
        }
        val yy = data.getBitsFromBuffer(32, 7)
        val mm = data.getBitsFromBuffer(32 + 7, 4)
        val dd = data.getBitsFromBuffer(32 + 11, 5)

        // Product sales have time, too.
        // 物販だったら時s間もセット
        if (isProductSale) {
            val hh = data.getBitsFromBuffer(48, 5)
            val min = data.getBitsFromBuffer(48 + 5, 6)
            return TimestampFull(TZ, 2000 + yy, mm - 1, dd, hh, min, 0)
        } else {
            return Daystamp(2000 + yy, mm - 1, dd)
        }
    }

    /**
     * 機器種別を取得します
     * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
     *
     * @param cType コンソールタイプをセット
     * @return String 機器タイプが文字列で戻ります
     */
    fun getConsoleTypeName(cType: Int): String {
        when (cType and 0xff) {
            0x03 -> return Localizer.localizeString(R.string.felica_terminal_fare_adjustment)
            0x04 -> return Localizer.localizeString(R.string.felica_terminal_portable)
            0x05 -> return Localizer.localizeString(R.string.felica_terminal_vehicle) // bus
            0x07 -> return Localizer.localizeString(R.string.felica_terminal_ticket)
            0x08 -> return Localizer.localizeString(R.string.felica_terminal_ticket)
            0x09 -> return Localizer.localizeString(R.string.felica_terminal_deposit_quick_charge)
            0x12 -> return Localizer.localizeString(R.string.felica_terminal_tvm_tokyo_monorail)
            0x13 -> return Localizer.localizeString(R.string.felica_terminal_tvm_etc)
            0x14 -> return Localizer.localizeString(R.string.felica_terminal_tvm_etc)
            0x15 -> return Localizer.localizeString(R.string.felica_terminal_tvm_etc)
            0x16 -> return Localizer.localizeString(R.string.felica_terminal_turnstile)
            0x17 -> return Localizer.localizeString(R.string.felica_terminal_ticket_validator)
            0x18 -> return Localizer.localizeString(R.string.felica_terminal_ticket_booth)
            0x19 -> return Localizer.localizeString(R.string.felica_terminal_ticket_office_green)
            0x1a -> return Localizer.localizeString(R.string.felica_terminal_ticket_gate_terminal)
            0x1b -> return Localizer.localizeString(R.string.felica_terminal_mobile_phone)
            0x1c -> return Localizer.localizeString(R.string.felica_terminal_connection_adjustment)
            0x1d -> return Localizer.localizeString(R.string.felica_terminal_transfer_adjustment)
            0x1f -> return Localizer.localizeString(R.string.felica_terminal_simple_deposit)
            0x46 -> return Localizer.localizeString(R.string.felica_terminal_view_altte)
            0x48 -> return Localizer.localizeString(R.string.felica_terminal_view_altte)
            0xc7 -> return Localizer.localizeString(R.string.felica_terminal_pos)  // sales
            0xc8 -> return Localizer.localizeString(R.string.felica_terminal_vending)   // sales
            else -> return Localizer.localizeString(R.string.unknown_format, NumberUtils.intToHex(cType))
        }
    }

    /**
     * 処理種別を取得します
     * <pre>http:// sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
     *
     * @param proc 処理タイプをセット
     * @return String 処理タイプが文字列で戻ります
     */
    fun getProcessTypeName(proc: Int): String {
        when (proc and 0xff) {
            0x01 -> return Localizer.localizeString(R.string.felica_process_fare_exit_gate)
            0x02 -> return Localizer.localizeString(R.string.felica_process_charge)
            0x03 -> return Localizer.localizeString(R.string.felica_process_purchase_magnetic)
            0x04 -> return Localizer.localizeString(R.string.felica_process_fare_adjustment)
            0x05 -> return Localizer.localizeString(R.string.felica_process_admission_payment)
            0x06 -> return Localizer.localizeString(R.string.felica_process_booth_exit)
            0x07 -> return Localizer.localizeString(R.string.felica_process_issue_new)
            0x08 -> return Localizer.localizeString(R.string.felica_process_booth_deduction)
            0x0d -> return Localizer.localizeString(R.string.felica_process_bus_pitapa)                 // Bus
            0x0f -> return Localizer.localizeString(R.string.felica_process_bus_iruca)                  // Bus
            0x11 -> return Localizer.localizeString(R.string.felica_process_reissue)
            0x13 -> return Localizer.localizeString(R.string.felica_process_payment_shinkansen)
            0x14 -> return Localizer.localizeString(R.string.felica_process_entry_a_autocharge)
            0x15 -> return Localizer.localizeString(R.string.felica_process_exit_a_autocharge)
            0x1f -> return Localizer.localizeString(R.string.felica_process_deposit_bus)                // Bus
            0x23 -> return Localizer.localizeString(R.string.felica_process_purchase_special_ticket)    // Bus
            0x46 -> return Localizer.localizeString(R.string.felica_process_merchandise_purchase)       // Sales
            0x48 -> return Localizer.localizeString(R.string.felica_process_bonus_charge)
            0x49 -> return Localizer.localizeString(R.string.felica_process_register_deposit)           // Sales
            0x4a -> return Localizer.localizeString(R.string.felica_process_merchandise_cancel)         // Sales
            0x4b -> return Localizer.localizeString(R.string.felica_process_merchandise_admission)      // Sales
            0xc6 -> return Localizer.localizeString(R.string.felica_process_merchandise_purchase_cash)  // Sales
            0xcb -> return Localizer.localizeString(R.string.felica_process_merchandise_admission_cash) // Sales
            0x84 -> return Localizer.localizeString(R.string.felica_process_payment_thirdparty)
            0x85 -> return Localizer.localizeString(R.string.felica_process_admission_thirdparty)
            else -> return Localizer.localizeString(R.string.unknown_format, NumberUtils.intToHex(proc))
        }
    }
}
