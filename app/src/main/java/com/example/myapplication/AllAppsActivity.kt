package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*

class AllAppsActivity : Activity() {

    data class AppItem(val label: String, val icon: Drawable, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AllAppsActivity", "Activity created")
        setContentView(R.layout.activity_all_apps)

        val listView: ListView = findViewById(R.id.appsListView)
        val packageManager: PackageManager = packageManager

        // Получаем все установленные приложения
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        // Фильтруем только пользовательские приложения
        val appItems = apps.filter {
            it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
        }.map {
            AppItem(
                packageManager.getApplicationLabel(it).toString(),
                packageManager.getApplicationIcon(it),
                it.packageName
            )
        }

        val adapter = object : ArrayAdapter<AppItem>(this, android.R.layout.simple_list_item_1, appItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false)
                val item = appItems[position]
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = item.label
                textView.setCompoundDrawablesWithIntrinsicBounds(item.icon, null, null, null)
                textView.compoundDrawablePadding = 16
                return view
            }
        }

        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedApp = appItems[position]
            val launchIntent = packageManager.getLaunchIntentForPackage(selectedApp.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Невозможно запустить ${selectedApp.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
