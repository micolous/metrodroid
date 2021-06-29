/*
 * UnsupportedTagException.kt
 *
 * Copyright 2011 Eric Butler <eric@codebutler.com>
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

import au.id.micolous.metrodroid.multi.Localizer
import au.id.micolous.metrodroid.multi.R

abstract class UnsupportedTagException: Exception() {
    abstract val dialogMessage: String
}

class UnsupportedTagProtocolException(private val techList: List<String>, private val tagId: String) : UnsupportedTagException() {
    private val techListText get() = techList.joinToString ("\n  ") { it.replace("android.nfc.tech.", "") }
    override val message get(): String = "Identifier: $tagId\n\nTechnologies: \n$techListText"

    override val dialogMessage get() = Localizer.localizeString(R.string.unsupported_tag_message, tagId, techListText)
}

class UnknownUltralightException : UnsupportedTagException() {
    override val message get(): String = "Unknown MIFARE Ultralight"

    override val dialogMessage get() = Localizer.localizeString(R.string.unknown_ultralight_message)
}
