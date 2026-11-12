package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: JarvisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Root(vm) }
    }

    override fun onStart() { super.onStart(); vm.resumeClap() }
    override fun onStop() { super.onStop(); vm.pause() }
}

@Composable
fun Root(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onMic(granted(Manifest.permission.RECORD_AUDIO))
    }
    LaunchedEffect(Unit) {
        val want = buildList {
            add(Manifest.permission.RECORD_AUDIO); add(Manifest.permission.READ_CONTACTS); add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 29) { add(Manifest.permission.READ_EXTERNAL_STORAGE); add(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        }.filter { !granted(it) }
        if (want.isEmpty()) vm.onMic(true) else perms.launch(want.toTypedArray())
    }
    JarvisApp(vm)
}
