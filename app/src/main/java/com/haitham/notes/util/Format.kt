package com.haitham.notes.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    private val loc = Locale("ar")
    fun date(t: Long): String {
        if (t <= 0) return "—"
        val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = t }
        val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        val pattern = when {
            sameDay -> "h:mm a"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> "d MMM"
            else -> "d MMM yyyy"
        }
        return SimpleDateFormat(pattern, loc).format(Date(t))
    }
    fun full(t: Long): String = if (t <= 0) "—" else SimpleDateFormat("d MMM yyyy - h:mm a", loc).format(Date(t))
}
