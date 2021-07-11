/*
 * ISO7816Protocol.kt
 *
 * Copyright 2018-2019 Michael Farrell <micolous+git@gmail.com>
 * Copyright 2018-2019 Google
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
package au.id.micolous.metrodroid.card.iso7816

import au.id.micolous.metrodroid.card.CardTransceiveException
import au.id.micolous.metrodroid.card.CardTransceiver
import au.id.micolous.metrodroid.multi.Log
import au.id.micolous.metrodroid.multi.VisibleForTesting
import au.id.micolous.metrodroid.util.ImmutableByteArray
import au.id.micolous.metrodroid.util.hexString

/**
 * Implements communication with cards that talk over ISO7816-4 APDUs.
 *
 *
 * Android doesn't contain useful classes for interfacing with these APDUs, so this class implements
 * basic parts of the specification. In particular, this only supports open communication with the
 * card, and doesn't support writing data.
 *
 *
 * This is used by Calypso and CEPAS cards, as well as most credit cards.
 *
 *
 * References:
 * - EMV 4.3 Book 1 (s9, s11)
 * - https://en.wikipedia.org/wiki/Smart_card_application_protocol_data_unit
 */
class ISO7816Protocol(private val mTagTech: CardTransceiver) {

    /**
     * Creates a C-APDU. (EMV 4.3 Book 1 s9.4.1)
     *
     *
     * This always sends with Le (expected return length) of 0 (=256 bytes).
     *
     * @param cla        Instruction class, may be any value but 0xFF.
     * @param ins        Instruction code within the instruction class.
     * @param p1         Reference byte completing the INS.
     * @param p2         Reference byte completing the INS.
     * @param length     Length of the expected return value, or 0 for no limit.
     * @param parameters Additional data to be send in a command.
     * @return A wrapped command.
     */
    private fun wrapMessage(cla: Byte, ins: Byte, p1: Byte, p2: Byte,
                            length: Byte, parameters: ImmutableByteArray): ImmutableByteArray {
        var output = ImmutableByteArray.of(cla, ins, p1, p2)

        if (parameters.isNotEmpty()) {
            output += parameters.size.toByte()
            output += parameters
        }

        output += length
        return output
    }

    private suspend fun sendRequestReal(
            cla: Byte, ins: Byte, p1: Byte, p2: Byte,
            length: Byte, parameters: ImmutableByteArray): ImmutableByteArray {
        val sendBuffer = wrapMessage(cla, ins, p1, p2, length, parameters)
        @Suppress("ConstantConditionIf")
        if (ENABLE_TRACING) {
            Log.d(TAG, ">>> $sendBuffer")
        }
        val recvBuffer = mTagTech.transceive(sendBuffer)
        @Suppress("ConstantConditionIf")
        if (ENABLE_TRACING) {
            Log.d(TAG, "<<< $recvBuffer")
        }

        if (recvBuffer.size == 1) {
            // Android HCE does this for some commands ?
            throw ISO7816Exception("Got 1-byte result: $recvBuffer")
        }

        return recvBuffer
    }

    /**
     * Sends a command to the card and checks the response.
     *
     * @param cla        Instruction class, may be any value but 0xFF.
     * @param ins        Instruction code within the instruction class.
     * @param p1         Reference byte completing the INS.
     * @param p2         Reference byte completing the INS.
     * @param length     Length of the expected return value, or 0 for no limit.
     * @param parameters Additional data to be send in a command.
     * @throws ISOFileNotFoundException If a requested file can not be found
     * @throws ISOEOFException If a requested record can not be found
     * @throws ISONoCurrentEF If the command is not allowed, because there is no selected EF
     * @throws CardTransceiveException If there is a communication error
     * @throws ISO7816Exception If there is an unhandled error code
     * @return A wrapped command.
     */
    suspend fun sendRequest(cla: Byte, ins: Byte, p1: Byte, p2: Byte, length: Byte, parameters: ImmutableByteArray): ImmutableByteArray {
        Log.d(TAG, "First attempt")
        var recvBuffer = sendRequestReal(cla, ins, p1, p2, length, parameters)

        var sw1 = recvBuffer[recvBuffer.size - 2]
        var sw2 = recvBuffer[recvBuffer.size - 1]
        Log.d(TAG, "First attempt: ${sw1.hexString}, ${sw2.hexString}")

        if (sw1 == ERROR_WRONG_LENGTH && sw2 != length) {
            Log.d(TAG, "Wrong length, trying with corrected length")
            recvBuffer = sendRequestReal(cla, ins, p1, p2, sw2, parameters)
        }

        sw1 = recvBuffer[recvBuffer.size - 2]
        sw2 = recvBuffer[recvBuffer.size - 1]

        if (sw1 != STATUS_OK) {
            when (sw1) {
                ERROR_COMMAND_NOT_ALLOWED // Command not allowed
                -> when (sw2) {
                    CNA_NO_CURRENT_EF // Command not allowed (no current EF)
                    ->
                        // Emitted by Android HCE when doing a CEPAS probe
                        throw ISONoCurrentEF()
                    CNA_SECURITY_STATUS_NOT_SATISFIED
                        // Emitted by CEPAS on SFI=3 before app selection
                    -> throw ISOSecurityStatusNotSatisfied()
                }

                ERROR_WRONG_PARAMETERS // Wrong Parameters P1 - P2
                -> when (sw2) {
                    WP_FILE_NOT_FOUND // File not found
                    -> throw ISOFileNotFoundException()
                    WP_RECORD_NOT_FOUND // Record not found
                    -> throw ISOEOFException()
                }

                ERROR_INS_NOT_SUPPORTED_OR_INVALID // Instruction code not supported or invalid
                -> throw ISOInstructionCodeNotSupported()

                ERROR_CLASS_NOT_SUPPORTED
                -> throw ISOClassNotSupported()
            }

            // we get error?
            throw ISO7816Exception("Got unknown result: " + recvBuffer.getHexString(recvBuffer.size - 2, 2))
        }

        return recvBuffer.sliceOffLen(0, recvBuffer.size - 2)
    }

    suspend fun sendRequest(cla: Byte, ins: Byte, p1: Byte, p2: Byte, length: Byte)
            : ImmutableByteArray = sendRequest(cla, ins, p1, p2, length,
            ImmutableByteArray.empty())

    suspend fun selectByName(name: ImmutableByteArray, nextOccurrence: Boolean): ImmutableByteArray {
        Log.d(TAG, "Select by name $name")
        // Select an application by file name
        return sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_SELECT,
                SELECT_BY_NAME, if (nextOccurrence) 0x02.toByte() else 0x00.toByte(), 0.toByte(),
                name)
    }

    suspend fun unselectFile() {
        Log.d(TAG, "Unselect file")
        sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_SELECT,
                0.toByte(), 0.toByte(), 0.toByte())
    }

    suspend fun selectById(fileId: Int): ImmutableByteArray {
        val file = ImmutableByteArray.of((fileId shr 8).toByte(), fileId.toByte())
        Log.d(TAG, "Select file $file")
        return sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_SELECT,
                0.toByte(), 0.toByte(), 0.toByte(),
                file)
    }

    suspend fun readRecord(recordNumber: Byte, length: Byte) = try {
        Log.d(TAG, "Read record $recordNumber")
        sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_READ_RECORD,
                recordNumber, 0x4.toByte() /* p1 is record number */, length)
    } catch (e: ISO7816Exception) {
        if (e is ISOEOFException)
            throw e
        Log.d(TAG, "couldn't read record", e)
        null
    }

    suspend fun readBinary() = try {
        Log.d(TAG, "Read binary")
        sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_READ_BINARY, 0.toByte(), 0.toByte(), 0.toByte())
    } catch (e: ISO7816Exception) {
        if (e is ISOEOFException)
            throw e
        null
    }

    suspend fun selectByNameOrNull(name: ImmutableByteArray) =
            try {
                selectByName(name, false)
            } catch (e: ISO7816Exception) {
                null
            } catch (e: CardTransceiveException) {
                null
            }

    suspend fun readBinary(sfi: Int) =
            try {
                Log.d(TAG, "Read binary")
                sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_READ_BINARY, (0x80 or sfi).toByte(), 0.toByte(), 0.toByte())
            } catch (e: ISO7816Exception) {
                if (e is ISOEOFException)
                    throw e
                Log.d(TAG, "couldn't read record", e)
                null
            }

    suspend fun readRecord(sfi: Int, recordNumber: Byte, length: Byte) =
            try {
                Log.d(TAG, "Read record $recordNumber")
                sendRequest(CLASS_ISO7816, INSTRUCTION_ISO7816_READ_RECORD,
                        recordNumber, ((sfi shl 3) or 4).toByte() /* p1 is record number */, length)
            } catch (e: ISO7816Exception) {
                if (e is ISOEOFException)
                    throw e
                Log.d(TAG, "couldn't read record", e)
                null
            }

    companion object {
        /**
         * If true, this turns on debug logs that show ISO7816 communication.
         */
        private const val ENABLE_TRACING = false

        private const val TAG = "ISO7816Protocol"
        @VisibleForTesting
        const val CLASS_ISO7816 = 0x00.toByte()
        const val CLASS_80 = 0x80.toByte()
        const val CLASS_90 = 0x90.toByte()

        @VisibleForTesting
        const val INSTRUCTION_ISO7816_SELECT = 0xA4.toByte()
        @VisibleForTesting
        const val INSTRUCTION_ISO7816_READ_BINARY = 0xB0.toByte()
        @VisibleForTesting
        const val INSTRUCTION_ISO7816_READ_RECORD = 0xB2.toByte()
        @VisibleForTesting
        const val ERROR_COMMAND_NOT_ALLOWED = 0x69.toByte()
        @VisibleForTesting
        const val ERROR_WRONG_PARAMETERS = 0x6A.toByte()
        @VisibleForTesting
        const val ERROR_WRONG_LENGTH = 0x6C.toByte()
        @VisibleForTesting
        const val ERROR_INS_NOT_SUPPORTED_OR_INVALID = 0x6D.toByte()
        @VisibleForTesting
        const val ERROR_CLASS_NOT_SUPPORTED = 0x6E.toByte()
        @VisibleForTesting
        const val CNA_NO_CURRENT_EF = 0x86.toByte()
        @VisibleForTesting
        const val CNA_SECURITY_STATUS_NOT_SATISFIED = 0x82.toByte()
        @VisibleForTesting
        const val WP_FILE_NOT_FOUND = 0x82.toByte()
        @VisibleForTesting
        const val WP_RECORD_NOT_FOUND = 0x83.toByte()
        @VisibleForTesting
        const val SELECT_BY_NAME = 0x04.toByte()
        @VisibleForTesting
        const val STATUS_OK = 0x90.toByte()
    }
}
