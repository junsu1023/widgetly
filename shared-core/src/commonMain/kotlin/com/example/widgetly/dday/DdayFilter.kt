package com.example.widgetly.dday

fun filterByCategory(entries: List<DdayEntry>, category: DdayCategory?): List<DdayEntry> =
    if (category == null) entries else entries.filter { it.category == category }
