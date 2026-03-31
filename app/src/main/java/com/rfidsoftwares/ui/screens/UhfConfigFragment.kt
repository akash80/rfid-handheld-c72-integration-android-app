package com.rfidsoftwares.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.rfidsoftwares.R
import com.rfidsoftwares.common.prefs.UhfConfig
import com.rfidsoftwares.common.prefs.UhfConfigPrefs
import com.rfidsoftwares.rfid.ChainwayUhfReaderGateway
import com.rfidsoftwares.ui.base.BaseScreenFragment
import java.util.concurrent.Executors

class UhfConfigFragment : BaseScreenFragment() {

    private var bg = Executors.newSingleThreadExecutor()

    override fun screenTitle(): String = "UHF Configuration"

    override fun screenSubtitle(): String? = "Adjust reader region and power before scanning"

    override fun allowOfflinePanel(): Boolean = false

    override fun createBody(inflater: LayoutInflater, container: FrameLayout): View {
        return inflater.inflate(R.layout.body_uhf_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ensureBg()

        val regionInput: TextInputEditText = view.findViewById(R.id.uhfRegionInput)
        val powerInput: TextInputEditText = view.findViewById(R.id.uhfPowerInput)
        val statusText: TextView = view.findViewById(R.id.uhfConfigStatusText)
        val saveApply: MaterialButton = view.findViewById(R.id.uhfSaveApplyButton)

        val cfg = UhfConfigPrefs.load(requireContext())
        regionInput.setText(cfg.regionCode ?: "")
        powerInput.setText((cfg.powerDbm ?: 30).toString())

        statusText.text = "Current settings loaded. Save and apply to refresh the reader."

        saveApply.setOnClickListener {
            val region = regionInput.text?.toString()?.trim().orEmpty()
            val power = powerInput.text?.toString()?.trim()?.toIntOrNull()

            if (power == null) {
                statusText.text = "Enter power as a whole number in dBm."
                return@setOnClickListener
            }

            val toSave = UhfConfig(
                regionCode = region.ifBlank { null },
                powerDbm = power,
            )
            UhfConfigPrefs.save(requireContext(), toSave)
            statusText.text = "Saving settings and applying them to the reader..."

            bg.execute {
                val gateway = ChainwayUhfReaderGateway()
                val ok = try {
                    gateway.init(requireContext().applicationContext) // applies config in init()
                } catch (_: Exception) {
                    false
                }
                try {
                    gateway.free()
                } catch (_: Exception) {
                }

                view.post {
                    statusText.text = if (ok) {
                        "Settings saved and applied. Start a new scan if you want to confirm the change."
                    } else {
                        "Settings were saved, but the reader could not apply them. Check whether this device supports the selected values."
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        bg.shutdownNow()
        super.onDestroyView()
    }

    private fun ensureBg() {
        if (bg.isShutdown || bg.isTerminated) {
            bg = Executors.newSingleThreadExecutor()
        }
    }
}

