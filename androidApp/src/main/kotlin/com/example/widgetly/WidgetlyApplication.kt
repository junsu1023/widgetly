package com.example.widgetly

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.example.widgetly.dday.AndroidDdayStorage
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.widget.DdayGlanceWidget

class WidgetlyApplication : Application() {
    private val repository: DdayRepository by lazy {
        DdayRepository(AndroidDdayStorage(this)) {
            DdayGlanceWidget().updateAll(this@WidgetlyApplication)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: WidgetlyApplication
        val ddayRepository: DdayRepository get() = instance.repository
    }
}
