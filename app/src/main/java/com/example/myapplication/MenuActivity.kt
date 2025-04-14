package com.example.myapplication

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.*
import android.util.Base64
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.io.ByteArrayOutputStream

class MenuActivity : Activity() {

    private lateinit var buttons: Array<ImageButton>
    private lateinit var headerTextView: TextView
    private val prefs by lazy {
        getSharedPreferences("menu_prefs", Context.MODE_PRIVATE)
    }

    private var selectedIndex = 4
    private var isAppDialogShowing = false
    private var isRemoveDialogShowing = false
    private val holdThreshold = 1000L
    private val holdHandlers = Array(9) { Handler(Looper.getMainLooper()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        hideSystemUI()

        headerTextView = findViewById(R.id.headerTextView)
        buttons = arrayOf(
            findViewById(R.id.appButton1),
            findViewById(R.id.appButton2),
            findViewById(R.id.appButton3),
            findViewById(R.id.appButton4),
            findViewById(R.id.appButton5),
            findViewById(R.id.appButton6),
            findViewById(R.id.appButton7),
            findViewById(R.id.appButton8),
            findViewById(R.id.appButton9)
        )

        buttons.forEachIndexed { index, button ->
            button.background = getDrawable(R.drawable.button_selector)

            button.isFocusable = true
            button.isFocusableInTouchMode = true
            button.scaleType = ImageView.ScaleType.FIT_XY
            button.setPadding(0, 0, 0, 0)
            button.adjustViewBounds = true

            // Отключить сенсор
            button.setOnTouchListener { _, _ -> true }

            button.setOnClickListener {
                val intentUri = prefs.getString("intent_$index", null)
                intentUri?.let {
                    try {
                        val intent = Intent.parseUri(it, 0)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Ошибка запуска", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            button.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    selectedIndex = index
                    updateHeader(index)
                    loadIcon(index)
                    // ✅ Накладываем затемнение
                    button.setColorFilter(0x88000000.toInt()) // прозрачный чёрный
                } else {
                    button.clearColorFilter()
                }
            }


            button.setOnKeyListener { _, keyCode, event ->
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            holdHandlers[index].postDelayed({
                                if (!isAppDialogShowing) {
                                    isAppDialogShowing = true
                                    showAppChooserDialog(index)
                                }
                            }, holdThreshold)
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            holdHandlers[index].removeCallbacksAndMessages(null)
                        }
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            holdHandlers[index].postDelayed({
                                if (!isRemoveDialogShowing) {
                                    isRemoveDialogShowing = true
                                    showRemoveDialog(index)
                                }
                            }, holdThreshold)
                        } else if (event.action == KeyEvent.ACTION_UP) {
                            holdHandlers[index].removeCallbacksAndMessages(null)
                            if (!isRemoveDialogShowing) {
                                finish()
                            }
                        }
                    }
                }
                false
            }

            loadIcon(index)
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            val btn = buttons[selectedIndex]
            btn.requestFocus()
            btn.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            btn.refreshDrawableState() // 💡 ключевая строка
            updateHeader(selectedIndex)
            loadIcon(selectedIndex)
        }, 100)
    }



    private fun updateHeader(index: Int) {
        val intentUri = prefs.getString("intent_$index", null)
        if (intentUri != null) {
            try {
                val intent = Intent.parseUri(intentUri, 0)
                val pkg = intent.`package`
                if (pkg != null) {
                    val appName = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                    headerTextView.text = appName
                } else {
                    headerTextView.text = ""
                }
            } catch (e: Exception) {
                headerTextView.text = ""
            }
        } else {
            headerTextView.text = ""
        }
    }

    private fun showAppChooserDialog(index: Int) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val pkgs = apps.map { it.packageName }

        AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setItems(labels) { _, which ->
                val intent = pm.getLaunchIntentForPackage(pkgs[which])
                if (intent != null) {
                    val icon = pm.getApplicationIcon(pkgs[which])
                    prefs.edit()
                        .putString("intent_$index", intent.toUri(0))
                        .putString("icon_$index", iconToBase64(icon))
                        .apply()
                    buttons[index].setImageDrawable(icon)
                    updateHeader(index)
                }
            }
            .setOnDismissListener { isAppDialogShowing = false }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showRemoveDialog(index: Int) {
        if (prefs.contains("intent_$index")) {
            AlertDialog.Builder(this)
                .setTitle("Удалить?")
                .setMessage("Удалить назначение с плитки?")
                .setPositiveButton("Удалить") { _, _ ->
                    prefs.edit()
                        .remove("intent_$index")
                        .remove("icon_$index")
                        .apply()
                    buttons[index].setImageDrawable(null)
                    headerTextView.text = ""
                }
                .setNegativeButton("Отмена", null)
                .setOnDismissListener { isRemoveDialogShowing = false }
                .show()
        } else {
            Toast.makeText(this, "Ничего не назначено", Toast.LENGTH_SHORT).show()
            isRemoveDialogShowing = false
        }
    }

    private fun loadIcon(index: Int) {
        val base64 = prefs.getString("icon_$index", null)
        if (base64 != null) {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            buttons[index].setImageDrawable(BitmapDrawable(resources, bitmap))
        } else {
            buttons[index].setImageDrawable(null)
        }
    }

    private fun iconToBase64(drawable: Drawable?): String? {
        if (drawable == null) return null
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 100
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 100
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bmp
            }
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }
}
