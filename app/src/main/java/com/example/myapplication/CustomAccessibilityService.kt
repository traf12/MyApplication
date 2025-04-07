package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CustomAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("ACCESSIBILITY", "Получено событие: ${event?.eventType}")
    }

    override fun onInterrupt() {
        Log.d("ACCESSIBILITY", "Сервис прерван")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ACCESSIBILITY", "Сервис специальных возможностей запущен")
    }
}
