package ir.hiwa.grammarinuse

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("grammar_in_use_prefs", Context.MODE_PRIVATE)

    var lastPageIndex: Int
        get() = prefs.getInt("last_page", 0)
        set(value) = prefs.edit().putInt("last_page", value).apply()

    var playbackRate: Float
        get() = prefs.getFloat("rate", 1.0f)
        set(value) = prefs.edit().putFloat("rate", value).apply()

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("auto_next", false) // OFF by default
        set(value) = prefs.edit().putBoolean("auto_next", value).apply()

    var warmMode: Boolean
        get() = prefs.getBoolean("warm_mode", false)
        set(value) = prefs.edit().putBoolean("warm_mode", value).apply()

    val streakCount: Int
        get() = prefs.getInt("streak", 1)

    init {
        updateDailyStreak()
    }

    private fun updateDailyStreak() {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        val last = prefs.getString("last_date", "") ?: ""
        val curr = prefs.getInt("streak", 0)
        if (last == today) {
            if (curr < 1) prefs.edit().putInt("streak", 1).apply()
            return
        }
        val next = if (last.isNotEmpty()) {
            try {
                val diff = ((fmt.parse(today)!!.time - fmt.parse(last)!!.time) / 86_400_000L).toInt()
                if (diff == 1) curr + 1 else 1
            } catch (_: Exception) {
                1
            }
        } else 1
        prefs.edit().putString("last_date", today).putInt("streak", next).apply()
    }

    private fun getBoolMap(key: String): MutableMap<String, Boolean> {
        val raw = prefs.getString(key, "{}") ?: "{}"
        val obj = JSONObject(raw)
        val map = mutableMapOf<String, Boolean>()
        obj.keys().forEach { k -> if (obj.optBoolean(k, false)) map[k] = true }
        return map
    }

    private fun saveBoolMap(key: String, map: Map<String, Boolean>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> if (v) obj.put(k, true) }
        prefs.edit().putString(key, obj.toString()).apply()
    }

    fun isPageDone(pageId: String): Boolean = getBoolMap("done_pages")[pageId] == true
    fun setPageDone(pageId: String, done: Boolean) {
        val m = getBoolMap("done_pages")
        if (done) m[pageId] = true else m.remove(pageId)
        saveBoolMap("done_pages", m)
    }

    fun isUnitDone(unit: Int): Boolean = getBoolMap("done_units")[unit.toString()] == true
    fun setUnitDone(unit: Int, done: Boolean) {
        val m = getBoolMap("done_units")
        if (done) m[unit.toString()] = true else m.remove(unit.toString())
        saveBoolMap("done_units", m)
    }

    fun isBookmarked(unit: Int): Boolean = getBoolMap("bookmarks")[unit.toString()] == true
    fun toggleBookmark(unit: Int): Boolean {
        val m = getBoolMap("bookmarks")
        val next = m[unit.toString()] != true
        if (next) m[unit.toString()] = true else m.remove(unit.toString())
        saveBoolMap("bookmarks", m)
        return next
    }

    fun getNote(unit: Int): String = prefs.getString("note_$unit", "") ?: ""
    fun setNote(unit: Int, text: String) = prefs.edit().putString("note_$unit", text).apply()

    fun getQuizScore(unit: Int): Pair<Int, Int>? {
        val raw = prefs.getString("quiz_$unit", null) ?: return null
        val parts = raw.split("/")
        return if (parts.size == 2) Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0) else null
    }

    fun setQuizScore(unit: Int, correct: Int, total: Int) {
        prefs.edit().putString("quiz_$unit", "$correct/$total").apply()
    }

    fun countCompletedPages(pages: List<BookPage>): Int {
        val m = getBoolMap("done_pages")
        return pages.count { it.unitNumber > 0 && m[it.id] == true }
    }

    fun countCompletedUnits(units: List<UnitSummary>): Int {
        val m = getBoolMap("done_units")
        return units.count { m[it.unitNumber.toString()] == true }
    }
}
