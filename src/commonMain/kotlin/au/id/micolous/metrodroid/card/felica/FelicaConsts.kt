/*
 * FelicaConsts.kt
 *
 * Copyright 2011 Kazzz
 * Copyright 2016-2019 Michael Farrell <micolous+git@gmail.com>
 * Copyright 2018-2019 Google Inc
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
 *
 * This code was imported from nfc-felica-lib[0], formerly licensed under
 * Apache License v2.0. The Apache Software Foundation considers the license
 * to be compatible with the GPLv3+[1].
 *
 * As the upstream library[0] appears to be no longer maintained, and we're not
 * aware of other users of Metrodroid's fork[2] of the library, this has been
 * pulled into Metrodroid proper[3]. It has been relicensed as GPLv3+ to be
 * consistent with the remainder of the project.
 *
 * [0]: https://github.com/Kazzz/nfc-felica-lib
 * [1]: https://www.apache.org/licenses/GPL-compatibility.html
 * [2]: https://github.com/metrodroid/nfc-felica-lib
 * [3]: https://github.com/micolous/metrodroid/pull/255
 */

package au.id.micolous.metrodroid.card.felica

object FelicaConsts {
    // CARD COMMANDS
    // Polling (s4.4.2)
    const val COMMAND_POLLING: Byte = 0x00
    const val RESPONSE_POLLING: Byte = 0x01

    // Request Service (s4.4.3)
    const val COMMAND_REQUEST_SERVICE: Byte = 0x02
    const val RESPONSE_REQUEST_SERVICE: Byte = 0x03

    // Request Response (s4.4.4)
    const val COMMAND_REQUEST_RESPONSE: Byte = 0x04
    const val RESPONSE_REQUEST_RESPONSE: Byte = 0x05

    // Read without encryption (s4.4.5)
    const val COMMAND_READ_WO_ENCRYPTION: Byte = 0x06
    const val RESPONSE_READ_WO_ENCRYPTION: Byte = 0x07

    // Write without encryption (s4.4.6)
    const val COMMAND_WRITE_WO_ENCRYPTION: Byte = 0x08
    const val RESPONSE_WRITE_WO_ENCRYPTION: Byte = 0x09

    // Search service code (s4.4.7, not documented publicly)
    const val COMMAND_SEARCH_SERVICECODE: Byte = 0x0a
    const val RESPONSE_SEARCH_SERVICECODE: Byte = 0x0b

    // Request system code (s4.4.8)
    const val COMMAND_REQUEST_SYSTEMCODE: Byte = 0x0c
    const val RESPONSE_REQUEST_SYSTEMCODE: Byte = 0x0d

    // Authentication 1 (s4.4.9, not documented publicly)
    const val COMMAND_AUTHENTICATION1: Byte = 0x10
    const val RESPONSE_AUTHENTICATION1: Byte = 0x11

    // Authentication 2 (s4.4.10, not documented publicly)
    const val COMMAND_AUTHENTICATION2: Byte = 0x12
    const val RESPONSE_AUTHENTICATION2: Byte = 0x13

    // Authenticated Read (s4.4.11, not documented publicly)
    const val COMMAND_READ: Byte = 0x14
    const val RESPONSE_READ: Byte = 0x15

    // Authenticated Write (s4.4.12, not documented publicly)
    const val COMMAND_WRITE: Byte = 0x16
    const val RESPONSE_WRITE: Byte = 0x17

    // Request Specification Version (s4.4.15)
    const val COMMAND_REQUEST_SPECIFICATION_VERSION: Byte = 0x3c
    const val RESPONSE_REQUEST_SPECIFICATION_VERSION: Byte = 0x3d

    // Reset Mode (s4.4.16)
    const val COMMAND_RESET_MODE: Byte = 0x3e
    const val RESPONSE_RESET_MODE: Byte = 0x3f

    // SYSTEM CODES
    // Wildcard, matches any system code.
    const val SYSTEMCODE_ANY = 0xffff
    // FeliCa Lite
    const val SYSTEMCODE_FELICA_LITE = 0x88b4
    // NDEF (NFC Data Exchange Format)
    const val SYSTEMCODE_NDEF = 0x12fc
    // Common Area (FeliCa Networks, Inc), used by IC (Suica) and Edy
    const val SYSTEMCODE_COMMON = 0xfe00

    // SERVICE CODES
    // FeliCa Lite, read-only mode
    const val SERVICE_FELICA_LITE_READONLY = 0x0b00
    // FeliCa Lite, read-write mode
    const val SERVICE_FELICA_LITE_READWRITE = 0x0900
}
