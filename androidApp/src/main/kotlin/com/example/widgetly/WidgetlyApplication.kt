package com.example.widgetly

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.example.widgetly.dday.AndroidDdayStorage
import com.example.widgetly.dday.DdayRepository
import com.example.widgetly.widget.DDAY_REFRESH_TOKEN_KEY
import com.example.widgetly.widget.DdayGlanceWidget

class WidgetlyApplication : Application() {
    private val repository: DdayRepository by lazy {
        DdayRepository(AndroidDdayStorage(this)) {
            val glanceIds = GlanceAppWidgetManager(this@WidgetlyApplication)
                .getGlanceIds(DdayGlanceWidget::class.java)
            for (glanceId in glanceIds) {
                updateAppWidgetState(this@WidgetlyApplication, glanceId) { prefs ->
                    prefs[DDAY_REFRESH_TOKEN_KEY] = System.currentTimeMillis()
                }
            }
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
