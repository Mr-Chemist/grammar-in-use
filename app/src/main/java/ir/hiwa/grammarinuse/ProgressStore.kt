package ir.hiwa.grammarinuse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    var language: AppLang
        get() = AppLang.fromCode(prefs.getString("app_lang", "en"))
        set(value) = prefs.edit().putString("app_lang", value.code).apply()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean("seen_onboarding_v2", false)
        set(value) = prefs.edit().putBoolean("seen_onboarding_v2", value).apply()

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

    fun hasPageAnnotations(pageId: String): Boolean {
        val raw = prefs.getString("ann_$pageId", null) ?: return false
        return raw.length > 25
    }

    fun getPageAnnotations(pageId: String): PageAnnotations {
        val raw = prefs.getString("ann_$pageId", null) ?: return PageAnnotations()
        return try {
            val root = JSONObject(raw)
            val strokes = mutableListOf<AnnotationStroke>()
            val sArr = root.optJSONArray("strokes")
            if (sArr != null) {
                for (i in 0 until sArr.length()) {
                    val so = sArr.getJSONObject(i)
                    val pts = mutableListOf<StrokePoint>()
                    val pArr = so.optJSONArray("pts")
                    if (pArr != null) {
                        var j = 0
                        while (j + 1 < pArr.length()) {
                            pts.add(StrokePoint(pArr.getDouble(j).toFloat(), pArr.getDouble(j + 1).toFloat()))
                            j += 2
                        }
                    }
                    if (pts.isNotEmpty()) {
                        strokes.add(
                            AnnotationStroke(
                                color = so.getInt("c"),
                                widthDp = so.optDouble("w", 3.0).toFloat(),
                                isHighlighter = so.optBoolean("h", false),
                                points = pts
                            )
                        )
                    }
                }
            }
            val notes = mutableListOf<TextAnnotation>()
            val nArr = root.optJSONArray("notes")
            if (nArr != null) {
                for (i in 0 until nArr.length()) {
                    val no = nArr.getJSONObject(i)
                    notes.add(
                        TextAnnotation(
                            id = no.optString("id", "n_$i"),
                            x = no.optDouble("x", 0.5).toFloat(),
                            y = no.optDouble("y", 0.5).toFloat(),
                            text = no.optString("t", ""),
                            color = no.optInt("c", 0xFF0F172A.toInt())
                        )
                    )
                }
            }
            PageAnnotations(strokes, notes)
        } catch (_: Exception) {
            PageAnnotations()
        }
    }

    fun savePageAnnotations(pageId: String, ann: PageAnnotations) {
        if (ann.isEmpty()) {
            prefs.edit().remove("ann_$pageId").apply()
            return
        }
        val root = JSONObject()
        val sArr = JSONArray()
        for (st in ann.strokes) {
            if (st.points.isEmpty()) continue
            val so = JSONObject()
            so.put("c", st.color)
            so.put("w", st.widthDp.toDouble())
            so.put("h", st.isHighlighter)
            val pArr = JSONArray()
            for (pt in st.points) {
                pArr.put(Math.round(pt.x * 10000.0) / 10000.0)
                pArr.put(Math.round(pt.y * 10000.0) / 10000.0)
            }
            so.put("pts", pArr)
            sArr.put(so)
        }
        root.put("strokes", sArr)

        val nArr = JSONArray()
        for (nt in ann.notes) {
            if (nt.text.isBlank()) continue
            val no = JSONObject()
            no.put("id", nt.id)
            no.put("x", Math.round(nt.x * 10000.0) / 10000.0)
            no.put("y", Math.round(nt.y * 10000.0) / 10000.0)
            no.put("t", nt.text)
            no.put("c", nt.color)
            nArr.put(no)
        }
        root.put("notes", nArr)
        prefs.edit().putString("ann_$pageId", root.toString()).apply()
    }
}
