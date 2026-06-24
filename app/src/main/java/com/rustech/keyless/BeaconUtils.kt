package com.rustech.keyless

import java.nio.ByteBuffer
import java.util.UUID

/** A parsed iBeacon advertisement, as seen during a BLE scan. */
data class IBeacon(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int,
    val rssi: Int,
    val address: String,
    var lastSeen: Long = System.currentTimeMillis()
) {
    /**
     * Rough distance estimate in meters using the standard iBeacon path-loss
     * formula. Treat this as a relative "near/far" signal, not a precise ruler --
     * RSSI is noisy and varies with walls, body blocking, phone orientation, etc.
     */
    fun estimatedDistanceMeters(): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi.toDouble() / txPower.toDouble()
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            0.89976 * Math.pow(ratio, 7.7095) + 0.111
        }
    }
}

object BeaconUtils {

    // Apple's assigned Bluetooth company ID -- the iBeacon spec piggybacks on it.
    const val APPLE_COMPANY_ID = 0x004C
    private const val IBEACON_TYPE: Byte = 0x02
    private const val IBEACON_LEN: Byte = 0x15 // 21 bytes follow this header

    /**
     * Builds the 23-byte iBeacon payload (everything that goes AFTER the 2-byte
     * company ID) for use with AdvertiseData.Builder#addManufacturerData().
     */
    fun buildIBeaconPayload(uuid: String, major: Int, minor: Int, txPower: Byte = -59): ByteArray {
        val uuidBytes = uuidToBytes(uuid)
        val data = ByteArray(23)
        data[0] = IBEACON_TYPE
        data[1] = IBEACON_LEN
        System.arraycopy(uuidBytes, 0, data, 2, 16)
        data[18] = (major shr 8 and 0xFF).toByte()
        data[19] = (major and 0xFF).toByte()
        data[20] = (minor shr 8 and 0xFF).toByte()
        data[21] = (minor and 0xFF).toByte()
        data[22] = txPower
        return data
    }

    /**
     * Attempts to parse Apple manufacturer-specific data (already extracted from a
     * ScanRecord via getManufacturerSpecificData(APPLE_COMPANY_ID)) as an iBeacon.
     * Returns null if it doesn't look like a valid iBeacon frame.
     */
    fun parseIBeacon(manufacturerData: ByteArray?, rssi: Int, address: String): IBeacon? {
        if (manufacturerData == null || manufacturerData.size < 23) return null
        if (manufacturerData[0] != IBEACON_TYPE || manufacturerData[1] != IBEACON_LEN) return null

        val uuidBytes = manufacturerData.copyOfRange(2, 18)
        val uuid = bytesToUuid(uuidBytes)
        val major = ((manufacturerData[18].toInt() and 0xFF) shl 8) or (manufacturerData[19].toInt() and 0xFF)
        val minor = ((manufacturerData[20].toInt() and 0xFF) shl 8) or (manufacturerData[21].toInt() and 0xFF)
        val txPower = manufacturerData[22].toInt()

        return IBeacon(uuid, major, minor, txPower, rssi, address)
    }

    private fun uuidToBytes(uuid: String): ByteArray {
        val u = UUID.fromString(uuid)
        val bb = ByteBuffer.allocate(16)
        bb.putLong(u.mostSignificantBits)
        bb.putLong(u.leastSignificantBits)
        return bb.array()
    }

    private fun bytesToUuid(bytes: ByteArray): String {
        val bb = ByteBuffer.wrap(bytes)
        val high = bb.long
        val low = bb.long
        return UUID(high, low).toString()
    }
}
