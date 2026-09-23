package com.rynvortex.spddump

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.rynvortex.spddump.databinding.ActivityMainBinding
import com.rynvortex.spddump.ui.ConsoleFragment
import com.rynvortex.spddump.ui.DeviceFragment
import com.rynvortex.spddump.ui.FilesFragment
import com.rynvortex.spddump.ui.OperationsFragment

/**
 * Thin shell hosting the four tabs from the reference UniTools screenshots:
 * Device (connect), Operations (one-tap command grid), Files (input/backup),
 * Console (raw command + live log). All shared state lives in FlashSession.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FlashSession.init(this)

        if (savedInstanceState == null) {
            showFragment(DeviceFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_device -> DeviceFragment()
                R.id.nav_operations -> OperationsFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_console -> ConsoleFragment()
                else -> return@setOnItemSelectedListener false
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
