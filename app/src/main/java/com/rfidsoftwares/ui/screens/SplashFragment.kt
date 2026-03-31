package com.rfidsoftwares.ui.screens

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.FrameLayout
import androidx.navigation.fragment.findNavController
import com.rfidsoftwares.R
import com.rfidsoftwares.ui.base.BaseScreenFragment

class SplashFragment : BaseScreenFragment() {

    private val handler = Handler(Looper.getMainLooper())

    override fun screenTitle(): String = "RFID Inventory"
    override fun screenSubtitle(): String? = "Preparing your workspace"
    override fun isTopLevelScreen(): Boolean = true

    override fun createBody(inflater: LayoutInflater, container: FrameLayout): View {
        val loading = inflater.inflate(R.layout.view_state_loading, container, false)
        val msg = loading.findViewById<TextView>(R.id.loadingMessage)
        msg.text = "Loading app..."
        return loading
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handler.postDelayed(
            {
                findNavController().navigate(R.id.action_splash_to_firstOpenCheckFragment)
            },
            550
        )
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}

