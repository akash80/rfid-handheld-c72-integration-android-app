package com.rfidsoftwares.common.prefs

import android.content.Context

data class UhfConfig(
    val regionCode: String?,
    val powerDbm: Int?,
)

/**
 * Persisted UHF reader configuration edited from the UI.
 *
 * Note: Chainway SDK configuration methods vary by model/library version, so application
 * is best-effort via reflection in `ChainwayUhfReaderGateway`.
 */
object UhfConfigPrefs {
    private const val PREFS_NAME = "rfid_inventory_uhf_prefs"
    private const val KEY_REGION = "region_code"
    private const val KEY_POWER_DBM = "power_dbm"

    fun load(context: Context): UhfConfig {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val region = p.getString(KEY_REGION, null)?.takeIf { it.isNotBlank() }
        val power = if (p.contains(KEY_POWER_DBM)) p.getInt(KEY_POWER_DBM, 30) else null
        return UhfConfig(regionCode = region, powerDbm = power)
    }

    fun save(context: Context, config: UhfConfig) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit().apply {
            if (config.regionCode.isNullOrBlank()) remove(KEY_REGION) else putString(KEY_REGION, config.regionCode)
            if (config.powerDbm == null) remove(KEY_POWER_DBM) else putInt(KEY_POWER_DBM, config.powerDbm)
            apply()
        }
    }
}

