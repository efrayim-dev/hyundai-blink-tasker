package com.hyundaicompanion.bluelinktasker

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hyundaicompanion.bluelinktasker.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as BlueLinkApp).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = SecurePrefs(this)
        setSupportActionBar(binding.toolbar)

        binding.switchBio.isChecked = prefs.requireBiometricForManualActions
        binding.switchBio.setOnCheckedChangeListener { _, checked ->
            prefs.requireBiometricForManualActions = checked
        }

        setupClimateSpinners()

        binding.btnUnlock.setOnClickListener { runWithOptionalBiometric { viewModel.unlock() } }
        binding.btnLock.setOnClickListener { runWithOptionalBiometric { viewModel.lock() } }
        binding.btnStart.setOnClickListener {
            runWithOptionalBiometric {
                val opts = readClimateFromUiAndPersist()
                viewModel.remoteStart(opts)
            }
        }
        binding.btnStop.setOnClickListener { runWithOptionalBiometric { viewModel.remoteStop() } }

        binding.btnTaskerHelp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.tasker_help)
                .setMessage(R.string.tasker_help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainViewModel.UiEvent.Busy ->
                            binding.progress.visibility = if (event.on) View.VISIBLE else View.GONE
                        is MainViewModel.UiEvent.Message ->
                            Toast.makeText(this@MainActivity, event.text, Toast.LENGTH_LONG).show()
                        is MainViewModel.UiEvent.Error ->
                            Toast.makeText(this@MainActivity, event.text, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.setupComplete) {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        refreshChrome()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_setup) {
            startActivity(Intent(this, SetupActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshChrome() {
        val repo = (application as BlueLinkApp).repository
        val v = repo.currentVehicle()
        binding.vehicleTitle.text = getString(R.string.vehicle_label, v?.nickname ?: "—")
        binding.secretValue.text = prefs.taskerSecret ?: getString(R.string.tasker_secret_not_set)

        binding.inputRsDuration.setText(prefs.rsDurationMinutes.toString())
        binding.inputRsTemp.setText(prefs.rsTempF.toString())
        binding.switchRsClimate.isChecked = prefs.rsClimateOn
        binding.switchRsDefrost.isChecked = prefs.rsDefrost
        binding.spinnerRsHeated.setSelection(prefs.rsHeatedFeatures.coerceIn(0, 3))
        binding.spinnerSeatDriver.setSelection(seatLevelToSpinnerIndex(prefs.rsSeatDriver))
        binding.spinnerSeatPassenger.setSelection(seatLevelToSpinnerIndex(prefs.rsSeatPassenger))
        binding.spinnerSeatRearLeft.setSelection(seatLevelToSpinnerIndex(prefs.rsSeatRearLeft))
        binding.spinnerSeatRearRight.setSelection(seatLevelToSpinnerIndex(prefs.rsSeatRearRight))

        val ready = prefs.setupComplete && v != null
        listOf(binding.btnUnlock, binding.btnLock, binding.btnStart, binding.btnStop).forEach {
            it.isEnabled = ready
        }
    }

    private fun setupClimateSpinners() {
        val heatedAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.heated_feature_labels,
            android.R.layout.simple_spinner_dropdown_item,
        )
        binding.spinnerRsHeated.adapter = heatedAdapter
        val seatAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.seat_climate_labels,
            android.R.layout.simple_spinner_dropdown_item,
        )
        binding.spinnerSeatDriver.adapter = seatAdapter
        binding.spinnerSeatPassenger.adapter = seatAdapter
        binding.spinnerSeatRearLeft.adapter = seatAdapter
        binding.spinnerSeatRearRight.adapter = seatAdapter
    }

    private fun readClimateFromUiAndPersist(): RemoteStartOptions {
        val dur = binding.inputRsDuration.text?.toString()?.toIntOrNull()?.coerceIn(1, 30) ?: 10
        val temp = binding.inputRsTemp.text?.toString()?.toIntOrNull()?.coerceIn(60, 90) ?: 70
        prefs.rsDurationMinutes = dur
        prefs.rsTempF = temp
        prefs.rsClimateOn = binding.switchRsClimate.isChecked
        prefs.rsDefrost = binding.switchRsDefrost.isChecked
        prefs.rsHeatedFeatures = binding.spinnerRsHeated.selectedItemPosition.coerceIn(0, 3)
        prefs.rsSeatDriver = spinnerIndexToSeatStored(binding.spinnerSeatDriver.selectedItemPosition)
        prefs.rsSeatPassenger = spinnerIndexToSeatStored(binding.spinnerSeatPassenger.selectedItemPosition)
        prefs.rsSeatRearLeft = spinnerIndexToSeatStored(binding.spinnerSeatRearLeft.selectedItemPosition)
        prefs.rsSeatRearRight = spinnerIndexToSeatStored(binding.spinnerSeatRearRight.selectedItemPosition)
        return RemoteStartOptions.fromSecurePrefs(prefs)
    }

    private fun spinnerIndexToSeatStored(index: Int): Int = when (index) {
        0 -> -1
        1 -> 0
        2 -> 6
        3 -> 7
        4 -> 8
        else -> -1
    }

    private fun seatLevelToSpinnerIndex(level: Int): Int = when (level) {
        -1 -> 0
        0 -> 1
        6 -> 2
        7 -> 3
        8 -> 4
        else -> 0
    }

    private fun runWithOptionalBiometric(action: () -> Unit) {
        if (!prefs.requireBiometricForManualActions) {
            action()
            return
        }
        if (!BiometricHelper.canAuthenticate(this)) {
            Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        BiometricHelper.authenticate(
            this,
            onSuccess = action,
            onFailed = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() },
        )
    }
}
