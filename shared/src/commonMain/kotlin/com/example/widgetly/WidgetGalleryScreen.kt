package com.example.widgetly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Placeholder destination for the "위젯갤러리" tab - out of MVP scope per
 * docs/superpowers/specs/2026-10-04-widgetly-mvp-design.md. Just a reachable
 * stub so the 4-tab bottom nav has somewhere to go.
 */
@Composable
fun WidgetGalleryScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("위젯갤러리 - 준비 중")
        }
    }
}
