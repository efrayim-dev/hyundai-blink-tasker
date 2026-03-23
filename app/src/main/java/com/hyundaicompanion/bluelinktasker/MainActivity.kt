package com.hyundaicompanion.bluelinktasker

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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

        binding.btnUnlock.setOnClickListener { runWithOptionalBiometric { viewModel.unlock() } }
        binding.btnLock.setOnClickListener { runWithOptionalBiometric { viewModel.lock() } }
        binding.btnStart.setOnClickListener { runWithOptionalBiometric { viewModel.remoteStart() } }
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

        val ready = prefs.setupComplete && v != null
        listOf(binding.btnUnlock, binding.btnLock, binding.btnStart, binding.btnStop).forEach {
            it.isEnabled = ready
        }
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
