package com.airouter.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 应用内调试日志管理器。
 * 开关由 AppConfig.debugLogEnabled 控制，
 * UI 层订阅 [logs] 即可实时显示。
 */
object DebugLog {

    private const val MAX_ENTRIES = 500

    data class Entry(
        val time: String,
        val tag: String,
        val message: String,
    )

    private val _logs = MutableStateFlow<List<Entry>>(emptyList())
    val logs: StateFlow<List<Entry>> = _logs

    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /** 是否启用，由外部（AppConfig）同步设置 */
    var enabled: Boolean = false

    fun log(tag: String, message: String) {
        if (!enabled) return
        val entry = Entry(
            time = sdf.format(Date()),
            tag = tag,
            message = message,
        )
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > MAX_ENTRIES) {
            _logs.value = current.takeLast(MAX_ENTRIES)
        } else {
            _logs.value = current
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
