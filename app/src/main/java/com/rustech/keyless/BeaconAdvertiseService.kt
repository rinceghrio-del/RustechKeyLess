package com.rustech.keyless

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Keeps the phone broadcasting its iBeacon identity even when the app is in
 * the background -- this is what the ESP32 side actually "sees" for RSSI
 * proximity. Runs as a foreground service so Android doesn't kill it.
 */
class BeaconAdvertiseService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    companion object {
        const val CHANNEL_ID = "rustech_keyless_broadcast"
        const val NOTIF_ID = 1001
        const val ACTION_STATUS = "com.rustech.keyless.BROADCAST_STATUS"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_MESSAGE = "message"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Starting broadcast…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        startAdvertising()
        return START_STICKY
    }

    private fun startAdvertising() {
        val btManager = getSystemService(BluetoothManager::class.java)
        val btAdapter = btManager?.adapter
        if (btAdapter == null || !btAdapter.isEnabled) {
            broadcastStatus(false, "Bluetooth is off")
            stopSelf()
            return
        }
        advertiser = btAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            broadcastStatus(false, "This device can't advertise BLE")
            stopSelf()
            return
        }

        val uuid = Prefs.getUuid(this)
        val major = Prefs.getMajor(this)
        val minor = Prefs.getMinor(this)
        val payload = BeaconUtils.buildIBeaconPayload(uuid, major, minor)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BeaconUtils.APPLE_COMPANY_ID, payload)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isRunning = true
                val msg = "Broadcasting · Major:$major Minor:$minor"
                updateNotification(msg)
                broadcastStatus(true, msg)
            }

            override fun onStartFailure(errorCode: Int) {
                isRunning = false
                broadcastStatus(false, "Failed to start (code $errorCode)")
                stopSelf()
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            broadcastStatus(false, "Missing Bluetooth permission")
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: SecurityException) {
            // Permission revoked mid-flight -- nothing else to clean up.
        }
        isRunning = false
        broadcastStatus(false, "Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun broadcastStatus(running: Boolean, message: String) {
        val intent = Intent(ACTION_STATUS)
        intent.putExtra(EXTRA_RUNNING, running)
        intent.putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Rustech Key Less Broadcast", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RUSTECH KEY LESS")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_broadcast)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notif = buildNotification(text)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notif)
    }
}
