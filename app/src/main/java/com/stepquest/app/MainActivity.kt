package com.stepquest.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.stepquest.app.ui.StepQuestApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    val stepPermission = mutableStateOf(true)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        stepPermission.value = hasStepPermission()
        if (stepPermission.value) Game.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Game.init(this)
        stepPermission.value = hasStepPermission()
        setContent { StepQuestApp(stepPermission.value, ::requestPermissions) }
        requestPermissions()
        scope.launch {
            while (isActive) {
                delay(3000)
                Game.syncSteps() // main thread: the session is only touched from here and the UI
            }
        }
    }

    fun hasStepPermission() = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun requestPermissions() {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    override fun onResume() { super.onResume(); if (hasStepPermission()) Game.onResume() }
    override fun onPause() { super.onPause(); Game.onPause() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
