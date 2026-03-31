package com.rfidsoftwares.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.rfidsoftwares.R
import com.rfidsoftwares.common.prefs.ThemeModePrefs

interface HardwareKeyHandler {
    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent): Boolean
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply persisted theme before activity inflates any views.
        ThemeModePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(com.rfidsoftwares.R.layout.activity_main)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
            val currentFragment = navHost?.childFragmentManager?.primaryNavigationFragment
            if (currentFragment is HardwareKeyHandler &&
                currentFragment.onHardwareKeyDown(event.keyCode, event)
            ) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

