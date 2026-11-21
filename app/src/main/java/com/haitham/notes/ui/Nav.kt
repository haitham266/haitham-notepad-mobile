package com.haitham.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.haitham.notes.data.Note

sealed class Screen {
    data object Home : Screen()
    data class Editor(val noteId: String) : Screen()
    data object Trash : Screen()
    data object Categories : Screen()
    data object Settings : Screen()
    data object About : Screen()
    data class Image(val imageId: String) : Screen()
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

val LocalNav = staticCompositionLocalOf { Navigator() }

/** حالة الواجهة المشتركة */
object UiState {
    /** القسم المختار في الرئيسية (null = كل الملاحظات) */
    var selectedCategory by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(0)
    val nav = Navigator()
    /** ملاحظات جديدة لم تُحفظ بعد */
    val drafts = mutableMapOf<String, Note>()
}
