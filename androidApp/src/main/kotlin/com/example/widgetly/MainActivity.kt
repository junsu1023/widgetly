package com.example.widgetly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.widgetly.dday.DdayListScreen
import com.example.widgetly.dday.DdayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DdayListScreen(DdayViewModel(WidgetlyApplication.ddayRepository))
        }
    }
}