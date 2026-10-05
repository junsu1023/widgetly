package com.example.widgetly

import android.app.Application
import com.example.widgetly.dday.AndroidDdayStorage
import com.example.widgetly.dday.DdayRepository

class WidgetlyApplication : Application() {
    private val repository: DdayRepository by lazy { DdayRepository(AndroidDdayStorage(this)) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: WidgetlyApplication
        val ddayRepository: DdayRepository get() = instance.repository
    }
}
