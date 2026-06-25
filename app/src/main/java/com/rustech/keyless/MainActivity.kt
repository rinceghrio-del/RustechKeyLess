package com.rustech.keyless

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.rustech.keyless.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = BeaconAdapter()
    private val beaconMap = LinkedHashMap<String, IBeacon>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null
    private var isScanning = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(BeaconAdvertiseService.EXTRA_RUNNING, false) ?: false
            val message = intent?.getStringExtra(BeaconAdvertiseService.EXTRA_MESSAGE) ?: ""
            binding.switchBroadcast.isChecked = running
            binding.tvBroadcastStatus.text = message
            updateBroadcastCard(running)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            pruneStaleBeacons()
            val list = beaconMap.values.toList()
            adapter.submit(list)
            binding.scopeView.updateBeacons(list)
            refreshHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupTabs()
        setupBroadcastTab()
        setupScopeTab()
        requestNeededPermissions()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(BeaconAdvertiseService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
        val running = BeaconAdvertiseService.isRunning
        binding.switchBroadcast.isChecked = running
        updateBroadcastCard(running)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(statusReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private fun setupTabs() {
        binding.btnTabBroadcast.setOnClickListener { showTab(broadcast = true) }
        binding.btnTabScope.setOnClickListener { showTab(broadcast = false) }
        showTab(broadcast = true)
    }

    private fun showTab(broadcast: Boolean) {
        binding.layoutBroadcast.visibility = if (broadcast) View.VISIBLE else View.GONE
        binding.layoutScope.visibility = if (broadcast) View.GONE else View.VISIBLE

        val green = ContextCompat.getColor(this, R.color.accent_green)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        val activeBg = ContextCompat.getDrawable(this, R.drawable.bg_tab_active)
        val clearBg = ContextCompat.getDrawable(this, android.R.color.transparent)

        binding.btnTabBroadcast.setTextColor(if (broadcast) green else secondary)
        binding.btnTabBroadcast.background = if (broadcast) activeBg else clearBg
        binding.btnTabScope.setTextColor(if (!broadcast) green else secondary)
        binding.btnTabScope.background = if (!broadcast) activeBg else clearBg
    }

    // ── Broadcast tab ────────────────────────────────────────────────────────

    private fun setupBroadcastTab() {
        binding.etUuid.setText(Prefs.getUuid(this))
        binding.etMajor.setText(Prefs.getMajor(this).toString())
        binding.etMinor.setText(Prefs.getMinor(this).toString())
        binding.etLabel.setText(Prefs.getLabel(this))
        val running = BeaconAdvertiseService.isRunning
        binding.switchBroadcast.isChecked = running
        updateBroadcastCard(running)

        binding.btnRandomUuid.setOnClickListener {
            binding.etUuid.setText(Prefs.randomUuid())
        }

        binding.btnSaveSettings.setOnClickListener {
            saveBroadcastSettings()
            Snackbar.make(binding.root, "Saved — restart broadcast to apply.", Snackbar.LENGTH_SHORT).show()
        }

        binding.switchBroadcast.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { saveBroadcastSettings(); startBroadcast() }
            else stopBroadcast()
        }
    }

    private fun updateBroadcastCard(running: Boolean) {
        binding.layoutBroadcastStats.visibility = if (running) View.VISIBLE else View.GONE
        binding.tvBroadcastStatus.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.accent_green else R.color.text_secondary)
        )
        binding.cardBroadcast.strokeColor = ContextCompat.getColor(
            this, if (running) R.color.accent_green_dim else R.color.accent_green_border
        )
    }

    private fun saveBroadcastSettings() {
        val uuid = binding.etUuid.text.toString().trim()
        val major = binding.etMajor.text.toString().toIntOrNull() ?: 1
        val minor = binding.etMinor.text.toString().toIntOrNull() ?: 1
        val label = binding.etLabel.text.toString().trim()
        try {
            UUID.fromString(uuid)
            Prefs.setUuid(this, uuid)
        } catch (e: IllegalArgumentException) {
            Snackbar.make(binding.root, "Invalid UUID format — keeping previous value.", Snackbar.LENGTH_LONG).show()
        }
        Prefs.setMajor(this, major)
        Prefs.setMinor(this, minor)
        Prefs.setLabel(this, label.ifBlank { "Rustech Phone" })
    }

    private fun startBroadcast() {
        val intent = Intent(this, BeaconAdvertiseService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopBroadcast() {
        stopService(Intent(this, BeaconAdvertiseService::class.java))
        binding.tvBroadcastStatus.text = "Stopped"
        updateBroadcastCard(false)
    }

    // ── Scope tab ────────────────────────────────────────────────────────────

    private fun setupScopeTab() {
        binding.rvBeacons.layoutManager = LinearLayoutManager(this)
        binding.rvBeacons.adapter = adapter
        binding.switchScan.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startScanning() else stopScanning()
        }
    }

    private fun startScanning() {
        val btManager = getSystemService(BluetoothManager::class.java)
        val adapterBt = btManager?.adapter
        val scanner = adapterBt?.bluetoothLeScanner
        if (adapterBt == null || !adapterBt.isEnabled || scanner == null) {
            Snackbar.make(binding.root, "Turn on Bluetooth first", Snackbar.LENGTH_LONG).show()
            binding.switchScan.isChecked = false
            return
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handleScanResult(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handleScanResult(it) }
        }
        try {
            scanner.startScan(null, settings, scanCallback)
            isScanning = true
            refreshHandler.post(refreshRunnable)
        } catch (e: SecurityException) {
            Snackbar.make(binding.root, "Missing Bluetooth scan permission", Snackbar.LENGTH_LONG).show()
            binding.switchScan.isChecked = false
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val mfgData = result.scanRecord?.getManufacturerSpecificData(BeaconUtils.APPLE_COMPANY_ID)
        val beacon = BeaconUtils.parseIBeacon(mfgData, result.rssi, result.device.address) ?: return
        beaconMap[beacon.address] = beacon
    }

    private fun pruneStaleBeacons() {
        val cutoff = System.currentTimeMillis() - 8000
        val stale = beaconMap.entries.filter { it.value.lastSeen < cutoff }.map { it.key }
        stale.forEach { beaconMap.remove(it) }
    }

    private fun stopScanning() {
        if (!isScanning) return
        try {
            getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) { }
        isScanning = false
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_SCAN
            needed += Manifest.permission.BLUETOOTH_ADVERTISE
            needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
