package com.example.widgetly

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform