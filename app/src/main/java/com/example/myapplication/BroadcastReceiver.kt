package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName

class ScreenOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            lockScreen(context)
        }
    }

    private fun lockScreen(context: Context) {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, MyDeviceAdminReceiver::class.java)

        if (devicePolicyManager.isAdminActive(componentName)) {
            devicePolicyManager.lockNow() // Блокировка экрана
        }
    }
}
