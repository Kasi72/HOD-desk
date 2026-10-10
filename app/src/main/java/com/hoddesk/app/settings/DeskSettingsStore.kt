package com.hoddesk.app.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

/** A recurring clinical block that planning must never book over (OPD, ward rounds, ECT, teaching). */
data class FixedBlock(val label: String, val days: Set<DayOfWeek>, val startMinute: Int, val endMinute: Int) {
    fun timeLabel(): String = "${formatMinute(startMinute)} – ${formatMinute(endMinute)}"
    fun daysLabel(): String = when {
        days.size == 7 -> "Daily"
        days == DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }.toSet() -> "Mon – Sat"
        else -> DayOfWeek.values().filter { it in days }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
    }
}

data class TeamMember(val name: String, val role: String, val topics: String)
data class Venue(val name: String, val travelMinutes: Int)

fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val suffix = if (h < 12) "am" else "pm"
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return if (m == 0) "$h12 $suffix" else "$h12:${m.toString().padStart(2, '0')} $suffix"
}

/**
 * Department profile and workflow preferences. Stored on device only; nothing here
 * is sensitive clinical data, so plain app-private preferences are sufficient.
 */
class DeskSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hod_desk_settings", Context.MODE_PRIVATE)

    var fixedBlocks: List<FixedBlock>
        get() = prefs.getString("fixed_blocks", null)?.let(::parseBlocks) ?: DEFAULT_BLOCKS
        set(value) = prefs.edit().putString("fixed_blocks", JSONArray(value.map { b ->
            JSONObject().put("label", b.label).put("days", JSONArray(b.days.map { it.value })).put("start", b.startMinute).put("end", b.endMinute)
        }).toString()).apply()

    var team: List<TeamMember>
        get() = prefs.getString("team", null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { TeamMember(it.getString("name"), it.optString("role"), it.optString("topics")) } } } }.getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit().putString("team", JSONArray(value.map { JSONObject().put("name", it.name).put("role", it.role).put("topics", it.topics) }).toString()).apply()

    var venues: List<Venue>
        get() = prefs.getString("venues", null)?.let { raw ->
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { Venue(it.getString("name"), it.optInt("minutes")) } } } }.getOrNull()
        } ?: emptyList()
        set(value) = prefs.edit().putString("venues", JSONArray(value.map { JSONObject().put("name", it.name).put("minutes", it.travelMinutes) }).toString()).apply()

    /** Highest authority first. Used to explain urgency; editable by the HOD. */
    var authorityOrder: List<String>
        get() = prefs.getString("authority_order", null)?.let { raw -> runCatching { JSONArray(raw).let { a -> (0 until a.length()).map(a::getString) } }.getOrNull() } ?: DEFAULT_AUTHORITY
        set(value) = prefs.edit().putString("authority_order", JSONArray(value).toString()).apply()

    /** Android calendar that confirmed events are written to; null means "ask / first available". */
    var calendarId: Long?
        get() = prefs.getLong("calendar_id", -1L).takeIf { it >= 0 }
        set(value) = prefs.edit().putLong("calendar_id", value ?: -1L).apply()

    var calendarName: String?
        get() = prefs.getString("calendar_name", null)
        set(value) = prefs.edit().putString("calendar_name", value).apply()

    /** Share of the working day "Plan my day" leaves unbooked for interruptions. */
    var slackPercent: Int
        get() = prefs.getInt("slack_percent", 30)
        set(value) = prefs.edit().putInt("slack_percent", value.coerceIn(0, 60)).apply()

    var workdayStartMinute: Int
        get() = prefs.getInt("workday_start", 9 * 60)
        set(value) = prefs.edit().putInt("workday_start", value).apply()

    var workdayEndMinute: Int
        get() = prefs.getInt("workday_end", 17 * 60)
        set(value) = prefs.edit().putInt("workday_end", value).apply()

    fun blocksOn(day: DayOfWeek): List<FixedBlock> = fixedBlocks.filter { day in it.days }.sortedBy { it.startMinute }

    private fun parseBlocks(raw: String): List<FixedBlock>? = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val days = o.getJSONArray("days").let { d -> (0 until d.length()).map { DayOfWeek.of(d.getInt(it)) }.toSet() }
            FixedBlock(o.getString("label"), days, o.getInt("start"), o.getInt("end"))
        }
    }.getOrNull()

    companion object {
        private val MON_SAT = DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }.toSet()
        val DEFAULT_BLOCKS = listOf(FixedBlock("OPD", MON_SAT, 9 * 60, 13 * 60))
        val DEFAULT_AUTHORITY = listOf("Secretary, Health", "District Collector", "DME / Joint Director", "Dean / Superintendent", "Other HODs", "Department staff", "Forwards")
    }
}
