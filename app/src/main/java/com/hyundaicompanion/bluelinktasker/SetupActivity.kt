package com.hyundaicompanion.bluelinktasker

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hyundaicompanion.bluelinktasker.databinding.ActivitySetupBinding
import kotlinx.coroutines.launch
import java.util.UUID

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: SecurePrefs
    private var vehicleList: List<VehicleInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        prefs = SecurePrefs(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!prefs.setupComplete) {
                        finishAffinity()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        binding.inputEmail.setText(prefs.username.orEmpty())
        binding.inputPin.setText(prefs.pin.orEmpty())

        ensureTaskerSecretUi()

        binding.btnSignIn.setOnClickListener { signInAndLoad() }
        binding.btnSave.setOnClickListener { saveAndFinish() }
        binding.btnRegenerateSecret.setOnClickListener {
            prefs.taskerSecret = UUID.randomUUID().toString()
            ensureTaskerSecretUi()
            Toast.makeText(this, R.string.secret_regenerated, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureTaskerSecretUi() {
        if (prefs.taskerSecret.isNullOrBlank()) {
            prefs.taskerSecret = UUID.randomUUID().toString()
        }
        binding.secretValue.text = prefs.taskerSecret
    }

    private fun signInAndLoad() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val pin = binding.inputPin.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() || password.isEmpty() || pin.isEmpty()) {
            Toast.makeText(this, "Enter email, password, and PIN", Toast.LENGTH_LONG).show()
            return
        }

        val repo = (application as BlueLinkApp).repository
        lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            binding.btnSignIn.isEnabled = false
            prefs.pin = pin
            val login = repo.loginAndSave(email, password)
            if (login.isFailure) {
                binding.progress.visibility = View.GONE
                binding.btnSignIn.isEnabled = true
                Toast.makeText(
                    this@SetupActivity,
                    login.exceptionOrNull()?.message ?: getString(R.string.error_prefix),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val vehicles = repo.fetchVehicles()
            binding.progress.visibility = View.GONE
            binding.btnSignIn.isEnabled = true
            if (vehicles.isFailure) {
                Toast.makeText(
                    this@SetupActivity,
                    vehicles.exceptionOrNull()?.message ?: getString(R.string.error_prefix),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            vehicleList = vehicles.getOrElse { emptyList() }
            if (vehicleList.isEmpty()) {
                Toast.makeText(this@SetupActivity, "No vehicles found on this account", Toast.LENGTH_LONG).show()
                return@launch
            }
            val adapter = ArrayAdapter(
                this@SetupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                vehicleList.map { it.nickname },
            )
            binding.vehicleSpinner.adapter = adapter
            Toast.makeText(this@SetupActivity, "Loaded ${vehicleList.size} vehicle(s)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndFinish() {
        if (vehicleList.isEmpty()) {
            Toast.makeText(this, "Sign in and load vehicles first", Toast.LENGTH_LONG).show()
            return
        }
        val pos = binding.vehicleSpinner.selectedItemPosition
        if (pos < 0 || pos >= vehicleList.size) {
            Toast.makeText(this, "Select a vehicle", Toast.LENGTH_LONG).show()
            return
        }
        val selected = vehicleList[pos]
        if (selected.regId.isBlank()) {
            Toast.makeText(this, "Vehicle data incomplete — sign in again to reload", Toast.LENGTH_LONG).show()
            return
        }
        val repo = (application as BlueLinkApp).repository
        repo.saveVehicleSelection(selected)
        ensureTaskerSecretUi()
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
