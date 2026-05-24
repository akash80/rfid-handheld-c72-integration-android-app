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

class WriteTagFragment : BaseScreenFragment() {
  override fun screenTitle(): String = "Write Tag"
  override fun screenSubtitle(): String? = "Write EPC/USER memory and set Access password"
  override fun allowOfflinePanel(): Boolean = false

  private var bg = Executors.newSingleThreadExecutor()
  private var access: ChainwayUhfTagAccess? = null

  override fun createBody(inflater: LayoutInflater, container: FrameLayout): View {
    return inflater.inflate(R.layout.body_write_tag, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ensureBg()

    val epcInput: TextInputEditText = view.findViewById(R.id.writeTagEpcInput)
    val pwdInput: TextInputEditText = view.findViewById(R.id.writeTagAccessPwdInput)
    val bankInput: TextInputEditText = view.findViewById(R.id.writeTagBankInput)
    val ptrInput: TextInputEditText = view.findViewById(R.id.writeTagWordPtrInput)
    val dataInput: TextInputEditText = view.findViewById(R.id.writeTagDataInput)
    val newPwdInput: TextInputEditText = view.findViewById(R.id.writeTagNewAccessPwdInput)
    val statusText: TextView = view.findViewById(R.id.writeTagStatusText)

    val scanBtn: MaterialButton = view.findViewById(R.id.writeTagScanButton)
    val writeBtn: MaterialButton = view.findViewById(R.id.writeTagWriteButton)
    val setPwdBtn: MaterialButton = view.findViewById(R.id.writeTagSetPwdButton)
    val lockBtn: MaterialButton = view.findViewById(R.id.writeTagLockButton)
    val unlockBtn: MaterialButton = view.findViewById(R.id.writeTagUnlockButton)

    bankInput.setText("USER")
    ptrInput.setText("0")
    statusText.text = "Enter data as HEX (no spaces). Passwords are 8-hex (32-bit)."

    scanBtn.setOnClickListener {
      statusText.text = "Opening reader and scanning..."
      bg.execute {
        val (msg, epc) = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed." to null
          val snap = a.scanNearestTag()
          if (snap == null) "No tag found. Move closer and try again." to null
          else "Tag detected (RSSI=${snap.rssi ?: "?"})." to snap.epc
        }.getOrElse {
          "Scan failed: ${it.message ?: it.javaClass.simpleName}" to null
        }
        view.post {
          statusText.text = msg
          if (!epc.isNullOrBlank()) epcInput.setText(epc)
        }
      }
    }

    writeBtn.setOnClickListener {
      statusText.text = "Writing..."
      val bank = parseBank(bankInput.text?.toString())
      val wordPtr = ptrInput.text?.toString()?.trim()?.toIntOrNull() ?: 0
      val dataHex = dataInput.text?.toString()?.trim().orEmpty()
      val epc = epcInput.text?.toString()?.trim()
      val pwd = pwdInput.text?.toString()?.trim()

      bg.execute {
        val msg = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed."
          val ok = a.writeMemory(
            epc = epc,
            bank = bank,
            wordPtr = wordPtr,
            dataHex = dataHex,
            accessPasswordHex = pwd,
          )
          if (ok) "Write OK." else "Write failed (unsupported/rejected)."
        }.getOrElse {
          "Write failed: ${it.message ?: it.javaClass.simpleName}"
        }
        view.post { statusText.text = msg }
      }
    }

    setPwdBtn.setOnClickListener {
      statusText.text = "Setting access password..."
      val epc = epcInput.text?.toString()?.trim()
      val newPwd = newPwdInput.text?.toString()?.trim().orEmpty()
      bg.execute {
        val msg = runCatching {
          val a = access ?: ChainwayUhfTagAccess().also { access = it }
          val initOk = a.init(requireContext().applicationContext)
          if (!initOk) return@runCatching "Reader init failed."
          val ok = a.setAccessPassword(epc = epc, newAccessPasswordHex = newPwd)
          if (ok) "Access password updated." else "Failed to set access password."
        }.getOrElse {
          "Failed to set password: ${it.message ?: it.javaClass.simpleName}"
        }
        view.post { statusText.text = msg }
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

  private fun parseBank(raw: String?): ChainwayUhfTagAccess.MemoryBank {
    return when (raw?.trim()?.uppercase()) {
      "RESERVED" -> ChainwayUhfTagAccess.MemoryBank.RESERVED
      "EPC" -> ChainwayUhfTagAccess.MemoryBank.EPC
      "TID" -> ChainwayUhfTagAccess.MemoryBank.TID
      "USER" -> ChainwayUhfTagAccess.MemoryBank.USER
      else -> ChainwayUhfTagAccess.MemoryBank.USER
    }
  }
}

