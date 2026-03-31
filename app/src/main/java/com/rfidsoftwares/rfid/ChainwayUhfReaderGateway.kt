package com.rfidsoftwares.rfid

import android.content.Context
import java.util.Locale
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import com.rfidsoftwares.common.prefs.UhfConfigPrefs
import kotlin.math.roundToInt

/**
 * Chainway UHF gateway wrapper around `RFIDWithUHFUART`.
 *
 * Uses buffered reads via `readTagFromBuffer()` inside the session scan loop.
 */
class ChainwayUhfReaderGateway : UhfReaderGateway {

    private var reader: RFIDWithUHFUART? = null

    override fun init(context: Context): Boolean {
        return try {
            val r = RFIDWithUHFUART.getInstance()
            reader = r
            val ok = initReader(r, context)
            if (ok) {
                // On C72, init needs to succeed before EPC mode/config calls are reliable.
                try {
                    r.setEPCMode()
                } catch (_: Exception) {
                }
                applyStoredUhfConfig(r, context)
            } else {
                try {
                    r.free()
                } catch (_: Exception) {
                }
                reader = null
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun initReader(reader: RFIDWithUHFUART, context: Context): Boolean {
        val noArgInit = runCatching {
            val method = reader.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterCount == 0
            }
            (method?.invoke(reader) as? Boolean) == true
        }.getOrDefault(false)
        if (noArgInit) return true

        return runCatching {
            val method = reader.javaClass.methods.firstOrNull {
                it.name == "init" && it.parameterCount == 1 &&
                    Context::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            (method?.invoke(reader, context) as? Boolean) == true
        }.getOrDefault(false)
    }

    override fun startInventory(): Boolean {
        return try {
            reader?.startInventoryTag() ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun stopInventory(): Boolean {
        return try {
            reader?.stopInventory() ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun free(): Boolean {
        return try {
            reader?.free() ?: false
        } catch (_: Exception) {
            false
        } finally {
            reader = null
        }
    }

    override fun readBufferedTagEvents(): List<TagEvent> {
        val r = reader ?: return emptyList()
        return try {
            val info: UHFTAGInfo = r.readTagFromBuffer()
            val epcRaw = info.getEPC()
            val epc = epcRaw?.trim()?.uppercase(Locale.US)
            if (epc.isNullOrBlank()) {
                emptyList()
            } else {
                val rssi = parseRssi(info.getRssi())
                listOf(
                    TagEvent(
                        epc = epc,
                        rssi = rssi,
                        source = TagSource.EPC,
                        seenAt = System.currentTimeMillis(),
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRssi(rawRssi: String?): Int? {
        val normalized = rawRssi?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return normalized.toIntOrNull()
            ?: normalized.toDoubleOrNull()?.roundToInt()
            ?: Regex("-?\\d+(\\.\\d+)?").find(normalized)?.value?.toDoubleOrNull()?.roundToInt()
    }

    override fun readDiagnosticsSummary(): ReaderDiagnosticsSummary {
        val sessionReader = reader
        val probe = sessionReader ?: runCatching { RFIDWithUHFUART.getInstance() }.getOrNull()
            ?: return ReaderDiagnosticsSummary(
                sdkReady = false,
                readerOpen = false,
                powerDbm = null,
                regionOrFrequency = null,
                batteryNote = null,
                detailLine = "UHF SDK instance unavailable",
            )

        val (power, region, batteryNote) = reflectPowerRegionBattery(probe)
        val detail = buildString {
            if (sessionReader != null) {
                append("Session active on this gateway (EPC mode)")
            } else {
                append("SDK reachable (no open session on this gateway wrapper)")
            }
            if (power != null) append(" · power≈${power} dBm")
            if (!region.isNullOrBlank()) append(" · $region")
            if (!batteryNote.isNullOrBlank()) append(" · $batteryNote")
        }
        return ReaderDiagnosticsSummary(
            sdkReady = true,
            readerOpen = sessionReader != null,
            powerDbm = power,
            regionOrFrequency = region,
            batteryNote = batteryNote,
            detailLine = detail,
        )
    }

    private fun reflectPowerRegionBattery(r: RFIDWithUHFUART): Triple<Int?, String?, String?> {
        var power: Int? = null
        var region: String? = null
        var battery: String? = null
        try {
            val mPower = r.javaClass.methods.find { it.name == "getPower" && it.parameterCount == 0 }
            val rawPower = mPower?.invoke(r)
            power = (rawPower as? Number)?.toInt()
        } catch (_: Exception) {
        }
        try {
            val mFreq = r.javaClass.methods.find {
                (it.name == "getFrequencyMode" || it.name == "getFreHop" || it.name == "getRegion") &&
                    it.parameterCount == 0
            }
            val fr = mFreq?.invoke(r)
            region = fr?.toString()
        } catch (_: Exception) {
        }
        try {
            val batteryMethods = listOf(
                "getBatteryLevel", "getBattery", "getBatteryPercent", "getBatteryVoltage",
                "getPowerLevel", "getVoltage",
            )
            for (name in batteryMethods) {
                val m = r.javaClass.methods.find { it.name == name && it.parameterCount == 0 } ?: continue
                val v = m.invoke(r) ?: continue
                battery = when (v) {
                    is Number -> "${name}=${v}"
                    else -> "${name}=${v}"
                }
                break
            }
        } catch (_: Exception) {
        }
        return Triple(power, region, battery)
    }

    private fun applyStoredUhfConfig(r: RFIDWithUHFUART, context: Context) {
        val cfg = UhfConfigPrefs.load(context)
        cfg.powerDbm?.let { p ->
            // Power setter names vary across SDK versions.
            tryInvokeIntSetter(
                target = r,
                value = p,
                methodNames = listOf("setPower", "setPowerLevel", "setPowerDbm", "setOutputPower"),
            )
        }
        cfg.regionCode?.let { region ->
            // Region setter names vary across SDK versions.
            tryInvokeStringSetter(
                target = r,
                value = region,
                methodNames = listOf("setRegion", "setRegionCode", "setRegulatoryDomain"),
            )
        }
    }

    private fun tryInvokeIntSetter(target: Any, value: Int, methodNames: List<String>) {
        val methods = target.javaClass.methods.filter { it.name in methodNames && it.parameterCount == 1 }
        for (m in methods) {
            val pType = m.parameterTypes.firstOrNull() ?: continue
            try {
                when {
                    pType == Int::class.javaPrimitiveType || pType == Integer::class.java -> m.invoke(target, value)
                    pType == Double::class.javaPrimitiveType || pType == java.lang.Double::class.java -> m.invoke(target, value.toDouble())
                    pType == Float::class.javaPrimitiveType || pType == java.lang.Float::class.java -> m.invoke(target, value.toFloat())
                    else -> continue
                }
                return
            } catch (_: Exception) {
                // Keep trying other setter overloads.
            }
        }
    }

    private fun tryInvokeStringSetter(target: Any, value: String, methodNames: List<String>) {
        val methods = target.javaClass.methods.filter { it.name in methodNames && it.parameterCount == 1 }
        for (m in methods) {
            val pType = m.parameterTypes.firstOrNull() ?: continue
            if (pType != String::class.java) continue
            try {
                m.invoke(target, value)
                return
            } catch (_: Exception) {
                // Keep trying other setter overloads.
            }
        }
    }
}

