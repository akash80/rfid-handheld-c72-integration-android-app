package com.rfidsoftwares.rfid

import android.content.Context
import com.rscja.deviceapi.RFIDWithUHFUART
import com.rscja.deviceapi.entity.UHFTAGInfo
import java.util.Locale

/**
 * Best-effort tag access wrapper (read/write/lock) for Chainway UHF SDK.
 *
 * Note: The Chainway SDK API surface varies by device/firmware. This wrapper uses reflection to
 * find common method names and fails safely with null/false when unsupported.
 */
class ChainwayUhfTagAccess {
  enum class MemoryBank(val bankId: Int) {
    RESERVED(0),
    EPC(1),
    TID(2),
    USER(3),
  }

  data class TagSnapshot(
    val epc: String,
    val rssi: Int?,
  )

  private var reader: RFIDWithUHFUART? = null

  fun init(context: Context): Boolean {
    return try {
      val r = RFIDWithUHFUART.getInstance()
      reader = r
      val ok = initReader(r, context)
      if (!ok) {
        try {
          r.free()
        } catch (_: Exception) {
        }
        reader = null
        return false
      }

      try {
        r.setEPCMode()
      } catch (_: Exception) {
      }
      // Reuse the same stored-config behavior as the scan gateway (power/region).
      // The methods are private there, so we reflect/defensively try the common setter names here too.
      applyStoredUhfConfigBestEffort(r, context)
      true
    } catch (_: Exception) {
      false
    }
  }

  fun free(): Boolean {
    return try {
      reader?.free() ?: false
    } catch (_: Exception) {
      false
    } finally {
      reader = null
    }
  }

  /**
   * Runs a short inventory window and returns the first EPC observed.
   */
  fun scanNearestTag(timeoutMs: Long = 1800L): TagSnapshot? {
    val r = reader ?: return null
    val started = try {
      r.startInventoryTag()
    } catch (_: Exception) {
      false
    }
    if (!started) return null

    val deadline = System.currentTimeMillis() + timeoutMs
    var found: TagSnapshot? = null
    while (System.currentTimeMillis() < deadline) {
      val info: UHFTAGInfo? = try {
        r.readTagFromBuffer()
      } catch (_: Exception) {
        null
      }
      val epc = info?.getEPC()?.trim()?.uppercase(Locale.US)
      if (!epc.isNullOrBlank()) {
        val rssi = info.getRssi()?.trim()?.toIntOrNull()
        found = TagSnapshot(epc = epc, rssi = rssi)
        break
      }
      try {
        Thread.sleep(40)
      } catch (_: InterruptedException) {
        break
      }
    }

    try {
      r.stopInventory()
    } catch (_: Exception) {
    }
    return found
  }

  /**
   * Read memory from a selected tag.
   *
   * @return hex string (uppercase, no spaces) or null if unsupported / failed.
   */
  fun readMemory(
    epc: String?,
    bank: MemoryBank,
    wordPtr: Int,
    wordCount: Int,
    accessPasswordHex: String?,
  ): String? {
    val r = reader ?: return null
    if (wordPtr < 0 || wordCount <= 0) return null

    selectTagBestEffort(r, epc)
    val pwd = normalizePwd(accessPasswordHex)

    // Common SDK variants:
    // - readData(String password, int bank, int ptr, int len)
    // - readData(String password, String epc, int bank, int ptr, int len)
    // - readData(int bank, int ptr, int len)
    val result: Any? = invokeFirstMatch(
      target = r,
      methodNames = listOf("readData", "readTagData", "read"),
      args = listOf(
        arrayOf(pwd, bank.bankId, wordPtr, wordCount),
        arrayOf(pwd, (epc ?: ""), bank.bankId, wordPtr, wordCount),
        arrayOf(bank.bankId, wordPtr, wordCount),
      )
    )
    return normalizeHex(result)
  }

  /**
   * Write memory to a selected tag.
   *
   * @param dataHex expected to be an even-length hex string representing whole words.
   */
  fun writeMemory(
    epc: String?,
    bank: MemoryBank,
    wordPtr: Int,
    dataHex: String,
    accessPasswordHex: String?,
  ): Boolean {
    val r = reader ?: return false
    if (wordPtr < 0) return false
    val hex = normalizeHexString(dataHex) ?: return false
    if (hex.length % 4 != 0) return false // word-aligned (2 bytes per word => 4 hex chars per word)

    selectTagBestEffort(r, epc)
    val pwd = normalizePwd(accessPasswordHex)
    val wordCount = hex.length / 4

    // Common SDK variants:
    // - writeData(String password, int bank, int ptr, int len, String data)
    // - writeData(String password, String epc, int bank, int ptr, int len, String data)
    // - writeData(int bank, int ptr, int len, String data)
    val result: Any? = invokeFirstMatch(
      target = r,
      methodNames = listOf("writeData", "writeTagData", "write"),
      args = listOf(
        arrayOf(pwd, bank.bankId, wordPtr, wordCount, hex),
        arrayOf(pwd, (epc ?: ""), bank.bankId, wordPtr, wordCount, hex),
        arrayOf(bank.bankId, wordPtr, wordCount, hex),
      )
    )
    return when (result) {
      is Boolean -> result
      is Int -> result == 0 || result == 1
      else -> false
    }
  }

  /**
   * Sets (writes) the Access password (32-bit) in Reserved memory (wordPtr=2, wordCount=2).
   */
  fun setAccessPassword(epc: String?, newAccessPasswordHex: String): Boolean {
    val pwd = normalizePwd(null)
    val newPwd = normalizePwd(newAccessPasswordHex) ?: return false
    if (newPwd.length != 8) return false
    return writeMemory(
      epc = epc,
      bank = MemoryBank.RESERVED,
      wordPtr = 2,
      dataHex = newPwd,
      accessPasswordHex = pwd,
    )
  }

  /**
   * Best-effort lock/unlock operation. Returns false when unsupported by SDK.
   *
   * Many SDKs expose lock APIs with device-specific enums. We try common method names and pass
   * arguments in a few likely shapes.
   */
  fun setLockState(
    epc: String?,
    accessPasswordHex: String?,
    lock: Boolean,
  ): Boolean {
    val r = reader ?: return false
    selectTagBestEffort(r, epc)
    val pwd = normalizePwd(accessPasswordHex)

    // Common patterns:
    // - lockMem(String password, int lockType)
    // - lockMem(String password, String epc, int lockType)
    // - setLock(String password, int lockType)
    // Where lockType is usually a bitmask. We only provide a coarse toggle.
    val lockType = if (lock) 1 else 0
    val result: Any? = invokeFirstMatch(
      target = r,
      methodNames = listOf("lockMem", "setLock", "lockTag", "lock"),
      args = listOf(
        arrayOf(pwd, lockType),
        arrayOf(pwd, (epc ?: ""), lockType),
        arrayOf(lockType),
      )
    )
    return when (result) {
      is Boolean -> result
      is Int -> result == 0 || result == 1
      else -> false
    }
  }

  private fun initReader(reader: RFIDWithUHFUART, context: Context): Boolean {
    val noArgInit = runCatching {
      val method = reader.javaClass.methods.firstOrNull { it.name == "init" && it.parameterCount == 0 }
      (method?.invoke(reader) as? Boolean) == true
    }.getOrDefault(false)
    if (noArgInit) return true

    return runCatching {
      val method = reader.javaClass.methods.firstOrNull {
        it.name == "init" && it.parameterCount == 1 && Context::class.java.isAssignableFrom(it.parameterTypes[0])
      }
      (method?.invoke(reader, context) as? Boolean) == true
    }.getOrDefault(false)
  }

  private fun selectTagBestEffort(r: RFIDWithUHFUART, epc: String?) {
    val value = epc?.trim()?.uppercase(Locale.US)
    if (value.isNullOrBlank()) return
    invokeFirstMatch(
      target = r,
      methodNames = listOf("selectEPC", "selectTag", "setSelectParam"),
      args = listOf(
        arrayOf(value),
        arrayOf(value.toByteArray()),
      )
    )
  }

  private fun invokeFirstMatch(target: Any, methodNames: List<String>, args: List<Array<Any>>): Any? {
    for (name in methodNames) {
      val candidates = target.javaClass.methods.filter { it.name == name }
      for (m in candidates) {
        for (argSet in args) {
          if (m.parameterCount != argSet.size) continue
          try {
            return m.invoke(target, *coerceArgs(m.parameterTypes, argSet))
          } catch (_: Exception) {
          }
        }
      }
    }
    return null
  }

  private fun coerceArgs(paramTypes: Array<Class<*>>, args: Array<Any>): Array<Any?> {
    val out = arrayOfNulls<Any>(args.size)
    for (i in args.indices) {
      val v = args[i]
      val t = paramTypes[i]
      out[i] = when {
        (t == Int::class.javaPrimitiveType || t == Integer::class.java) && v is Number -> v.toInt()
        (t == Boolean::class.javaPrimitiveType || t == java.lang.Boolean::class.java) && v is Boolean -> v
        t == String::class.java -> v.toString()
        t.isInstance(v) -> v
        else -> v
      }
    }
    return out
  }

  private fun normalizePwd(hex: String?): String {
    val normalized = normalizeHexString(hex) ?: "00000000"
    return normalized.padStart(8, '0').takeLast(8)
  }

  private fun normalizeHex(value: Any?): String? {
    return when (value) {
      is String -> normalizeHexString(value)
      is ByteArray -> value.joinToString(separator = "") { b -> "%02X".format(b) }
      else -> null
    }
  }

  private fun normalizeHexString(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.isBlank()) return null
    val cleaned = s.replace("0x", "", ignoreCase = true).replace("\\s+".toRegex(), "")
    if (!cleaned.matches(Regex("^[0-9a-fA-F]+$"))) return null
    return cleaned.uppercase(Locale.US)
  }

  private fun applyStoredUhfConfigBestEffort(r: RFIDWithUHFUART, context: Context) {
    val cfg = com.rfidsoftwares.common.prefs.UhfConfigPrefs.load(context)
    cfg.powerDbm?.let { p ->
      invokeFirstMatch(
        target = r,
        methodNames = listOf("setPower", "setPowerLevel", "setPowerDbm", "setOutputPower"),
        args = listOf(
          arrayOf(p),
          arrayOf(p.toDouble()),
          arrayOf(p.toFloat()),
        )
      )
    }
    cfg.regionCode?.let { region ->
      invokeFirstMatch(
        target = r,
        methodNames = listOf("setRegion", "setRegionCode", "setRegulatoryDomain"),
        args = listOf(arrayOf(region))
      )
    }
  }
}

