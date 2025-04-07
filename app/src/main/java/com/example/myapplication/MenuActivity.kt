package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.widget.GridLayout
import android.widget.Button
import android.view.KeyEvent

class MenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        val gridLayout: GridLayout = findViewById(R.id.gridLayout)

        // Настройка кнопок для запуска приложений
        val button1: Button = findViewById(R.id.appButton1)
        val button2: Button = findViewById(R.id.appButton2)
        // Добавь обработчики для других кнопок

        button1.setOnClickListener {
            // Действие при нажатии на кнопку
        }

        button2.setOnClickListener {
            // Действие при нажатии на кнопку
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Навигация влево
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Навигация вправо
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Навигация вверх
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Навигация вниз
                true
            }
            KeyEvent.KEYCODE_SOFT_LEFT -> {
                // Возврат в главное меню
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
