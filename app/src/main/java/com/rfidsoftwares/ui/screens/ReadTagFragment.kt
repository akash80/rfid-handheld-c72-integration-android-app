package com.rfidsoftwares.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.rfidsoftwares.R
import com.rfidsoftwares.rfid.ChainwayUhfTagAccess
import com.rfidsoftwares.ui.base.BaseScreenFragment
import java.util.concurrent.Executors

class ReadTagFragment : BaseScreenFragment() {
  override fun screenTitle(): String = "Read Tag"
  override fun screenSubtitle(): String? = "Scan a tag and read EPC/TID/USER/Reserved memory"
  override fun allowOfflinePanel(): Boolean = false

  private var bg = Executors.newSingleThreadExecutor()
  private var access: ChainwayUhfTagAccess? = null

  override fun createBody(inflater: LayoutInflater, container: FrameLayout): View {
    return inflater.inflate(R.layout.body_read_tag, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ensureBg()

    val epcInput: TextInputEditText = view.findViewById(R.id.readTagEpcInput)
    val pwdInput: TextInputEditText = view.findViewById(R.id.readTagAccessPwdInput)
    val bankInput: TextInputEditText = view.findViewById(R.id.readTagBankInput)
    val ptrInput: TextInputEditText = view.findViewById(R.id.readTagWordPtrInput)
    val countInput: TextInputEditText = view.findViewById(R.id.readTagWordCountInput)
    val statusText: TextView = view.findViewById(R.id.readTagStatusText)
    val resultText: TextView = view.findViewById(R.id.readTagResultText)

    val scanBtn: MaterialButton = view.findViewById(R.id.readTagScanButton)
    val readBtn: MaterialButton = view.findViewById(R.id.readTagReadButton)
    val lockBtn: MaterialButton = view.findViewById(R.id.readTagLockButton)
    val unlockBtn: MaterialButton = view.findViewById(R.id.readTagUnlockButton)

    bankInput.setText("TID")
    ptrInput.setText("0")
    countInput.setText("6")
    statusText.text = "Reader idle. Press Scan to capture an EPC."
    resultText.text = "—"

    scanBtn.setOnClickListener {
      statusText.text = "Opening reader and scanning..."
      resultText.text = "—"
      bg.execute {
        val (ok, msg, epc) = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching Triple(false, "Reader init failed.", null)
          val snap = a.scanNearestTag()
          if (snap == null) Triple(false, "No tag found. Move closer and try again.", null)
          else Triple(true, "Tag detected (RSSI=${snap.rssi ?: "?"}).", snap.epc)
        }.getOrElse {
          Triple(false, "Scan failed: ${it.message ?: it.javaClass.simpleName}", null)
        }
        view.post {
          statusText.text = msg
          if (ok && !epc.isNullOrBlank()) epcInput.setText(epc)
        }
      }
    }

    readBtn.setOnClickListener {
      statusText.text = "Reading..."
      resultText.text = "—"
      val bankText = bankInput.text?.toString()?.trim().orEmpty()
      val bank = parseBank(bankText)
      val wordPtr = ptrInput.text?.toString()?.trim()?.toIntOrNull() ?: 0
      val wordCount = countInput.text?.toString()?.trim()?.toIntOrNull() ?: 0
      val epc = epcInput.text?.toString()?.trim()
      val pwd = pwdInput.text?.toString()?.trim()

      bg.execute {
        val (msg, value) = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed." to null
          val hex = a.readMemory(
            epc = epc,
            bank = bank,
            wordPtr = wordPtr,
            wordCount = wordCount,
            accessPasswordHex = pwd,
          )
          if (hex.isNullOrBlank()) "Read failed (unsupported or no response)." to null
          else "Read OK (${hex.length / 2} bytes)." to hex
        }.getOrElse {
          "Read failed: ${it.message ?: it.javaClass.simpleName}" to null
        }
        view.post {
          statusText.text = msg
          resultText.text = value ?: "—"
        }
      }
    }

    lockBtn.setOnClickListener {
      statusText.text = "Locking..."
      val epc = epcInput.text?.toString()?.trim()
      val pwd = pwdInput.text?.toString()?.trim()
      bg.execute {
        val msg = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed."
          val ok = a.setLockState(epc = epc, accessPasswordHex = pwd, lock = true)
          if (ok) "Lock command sent." else "Lock failed (unsupported or rejected)."
        }.getOrElse {
          "Lock failed: ${it.message ?: it.javaClass.simpleName}"
        }
        view.post { statusText.text = msg }
      }
    }

    unlockBtn.setOnClickListener {
      statusText.text = "Unlocking..."
      val epc = epcInput.text?.toString()?.trim()
      val pwd = pwdInput.text?.toString()?.trim()
      bg.execute {
        val msg = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed."
          val ok = a.setLockState(epc = epc, accessPasswordHex = pwd, lock = false)
          if (ok) "Unlock command sent." else "Unlock failed (unsupported or rejected)."
        }.getOrElse {
          "Unlock failed: ${it.message ?: it.javaClass.simpleName}"
        }
        view.post { statusText.text = msg }
      }
    }
  }

  override fun onDestroyView() {
    try {
      access?.free()
    } catch (_: Exception) {
    }
    access = null
    bg.shutdownNow()
    super.onDestroyView()
  }

  private fun ensureBg() {
    if (bg.isShutdown || bg.isTerminated) {
      bg = Executors.newSingleThreadExecutor()
    }
  }

  private fun parseBank(raw: String): ChainwayUhfTagAccess.MemoryBank {
    return when (raw.trim().uppercase()) {
      "RESERVED" -> ChainwayUhfTagAccess.MemoryBank.RESERVED
      "EPC" -> ChainwayUhfTagAccess.MemoryBank.EPC
      "TID" -> ChainwayUhfTagAccess.MemoryBank.TID
      "USER" -> ChainwayUhfTagAccess.MemoryBank.USER
      else -> ChainwayUhfTagAccess.MemoryBank.TID
    }
  }
}

