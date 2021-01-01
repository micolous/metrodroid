/*
 * FelicaUtils.kt
 *
 * Copyright 2018-2019 Michael Farrell <micolous+git@gmail.com>
 * Copyright 2018-2019 Google
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General private License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General private License for more details.
 *
 * You should have received a copy of the GNU General private License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.id.micolous.metrodroid.card.felica

import au.id.micolous.metrodroid.multi.R
import au.id.micolous.metrodroid.multi.StringResource
import au.id.micolous.metrodroid.transit.edy.EdyTransitData
import au.id.micolous.metrodroid.transit.kmt.KMTTransitData
import au.id.micolous.metrodroid.transit.mrtj.MRTJTransitData
import au.id.micolous.metrodroid.transit.octopus.OctopusTransitData
import au.id.micolous.metrodroid.transit.suica.*

/**
 * Utilities for working with FeliCa cards.
 */
object FelicaUtils {

    /**
     * Translates the System Name to a human-readable name.
     *
     * Systems in FeliCa are like Applications in MIFARE.  They represent
     * a particular system operator's data.
     *
     * @param systemCode FeliCa system code to translate.
     * @return StringRes for what corresponds to that system ocde.
     */
    fun getFriendlySystemName(systemCode: Int): StringResource {
        return when (systemCode) {
            SYSTEMCODE_SUICA -> R.string.felica_system_cybernet
            SYSTEMCODE_SUICA_UNKNOWN -> R.string.card_name_suica
            SYSTEMCODE_HAYAKAKEN -> R.string.card_name_hayakaken
            EdyTransitData.SYSTEMCODE_EDY_EMPTY -> R.string.card_name_edy
            FelicaConsts.SYSTEMCODE_COMMON -> R.string.felica_system_common
            FelicaConsts.SYSTEMCODE_FELICA_LITE -> R.string.card_media_felica_lite
            OctopusTransitData.SYSTEMCODE_OCTOPUS -> R.string.card_name_octopus
            OctopusTransitData.SYSTEMCODE_SZT -> R.string.card_name_szt
            KMTTransitData.SYSTEMCODE_KMT -> R.string.card_name_kmt
            MRTJTransitData.SYSTEMCODE_MRTJ -> R.string.card_name_mrtj
            FelicaConsts.SYSTEMCODE_NDEF -> R.string.card_format_ndef
            else -> R.string.unknown
        }
    }

    /**
     * Translates the Service Name to a human-readable name.
     *
     * Services in FeliCa are like Files in MIFARE.
     *
     * Both the system and service codes must be specified, as service codes have different
     * meanings depending on the System.
     *
     * @param systemCode FeliCa system code to translate
     * @param serviceCode FeliCa service code to translate
     * @return Human-readable description of the service code.
     */
    fun getFriendlyServiceName(systemCode: Int, serviceCode: Int): StringResource {
        return when (systemCode) {
            SYSTEMCODE_SUICA -> when (serviceCode) {
                SERVICE_SUICA_ID -> R.string.suica_file_id
                SERVICE_SUICA_HISTORY -> R.string.suica_file_history
                SERVICE_SUICA_INOUT -> R.string.suica_file_in_out
                SERVICE_SUICA_ADMISSION -> R.string.suica_file_admission
                else -> R.string.unknown
            }

            FelicaConsts.SYSTEMCODE_COMMON -> when (serviceCode) {
                EdyTransitData.SERVICE_EDY_ID -> R.string.edy_file_id
                EdyTransitData.SERVICE_EDY_BALANCE -> R.string.edy_file_purse_balance
                EdyTransitData.SERVICE_EDY_HISTORY -> R.string.edy_file_history
                else -> R.string.unknown
            }

            FelicaConsts.SYSTEMCODE_FELICA_LITE -> when (serviceCode) {
                FelicaConsts.SERVICE_FELICA_LITE_READONLY -> R.string.felica_lite_read_only
                FelicaConsts.SERVICE_FELICA_LITE_READWRITE -> R.string.felica_lite_read_write
                else -> R.string.unknown
            }

            OctopusTransitData.SYSTEMCODE_OCTOPUS -> when (serviceCode) {
                OctopusTransitData.SERVICE_OCTOPUS -> R.string.card_name_octopus
                else -> R.string.unknown
            }

            OctopusTransitData.SYSTEMCODE_SZT -> when (serviceCode) {
                OctopusTransitData.SERVICE_SZT -> R.string.card_name_szt
                else -> R.string.unknown
            }

            KMTTransitData.SYSTEMCODE_KMT -> when (serviceCode) {
                KMTTransitData.SERVICE_KMT_ID -> R.string.kmt_file_id
                KMTTransitData.SERVICE_KMT_BALANCE -> R.string.kmt_file_purse_balance
                KMTTransitData.SERVICE_KMT_HISTORY -> R.string.kmt_file_history
                else -> R.string.unknown
            }

            MRTJTransitData.SYSTEMCODE_MRTJ -> when (serviceCode) {
                MRTJTransitData.SERVICE_MRTJ_ID -> R.string.mrtj_file_id
                MRTJTransitData.SERVICE_MRTJ_BALANCE -> R.string.mrtj_file_purse_balance
                else -> R.string.unknown
            }

            else -> R.string.unknown
        }
    }
}
