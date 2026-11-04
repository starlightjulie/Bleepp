package com.bleepp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bleepp.ui.BleeppRoot
import com.bleepp.ui.MainViewModel
import com.bleepp.ui.theme.BleeppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private val askPost = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val look by vm.look.collectAsStateWithLifecycle()
            BleeppTheme(look) {
                BleeppRoot(vm, onAskPost = {
                    if (Build.VERSION.SDK_INT >= 33) askPost.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }
        // Setup state and the silent switch change outside the app, so check while it's on screen
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.refreshApps()
                while (true) {
                    vm.refreshStatus()
                    delay(2_000)
                }
            }
        }
    }
}
