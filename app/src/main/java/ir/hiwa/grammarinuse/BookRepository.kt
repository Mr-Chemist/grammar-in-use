package ir.hiwa.grammarinuse

import android.content.Context
import org.json.JSONObject

class BookRepository(context: Context) {
    val pages: List<BookPage>
    val units: List<UnitSummary>
    val resources: List<ResourceEntry>
    val mcqs: List<McqQuestion>
    val writtens: List<WrittenQuestion>

    init {
        val bookJson = JSONObject(readAsset(context, "book_data.json"))
        val pList = mutableListOf<BookPage>()
        val pArr = bookJson.getJSONArray("pages")
        for (i in 0 until pArr.length()) {
            val o = pArr.getJSONObject(i)
            val sList = mutableListOf<SectionHotspot>()
            val sArr = o.optJSONArray("sections")
            if (sArr != null) {
                for (j in 0 until sArr.length()) {
                    val so = sArr.getJSONObject(j)
                    sList.add(
                        SectionHotspot(
                            label = so.getString("label"),
                            audioPath = if (so.isNull("audioPath")) null else so.optString("audioPath", "").ifEmpty { null },
                            topPct = so.getDouble("topPct").toFloat(),
                            heightPct = so.getDouble("heightPct").toFloat()
                        )
                    )
                }
            }
            pList.add(
                BookPage(
                    id = o.getString("id"),
                    unitNumber = o.getInt("unitNumber"),
                    unitTitle = o.getString("unitTitle"),
                    bookPage = o.getInt("bookPage"),
                    kind = o.getString("kind"),
                    pdfPath = o.optString("pdfPath", ""),
                    pdfPageIndex = o.optInt("pdfPageIndex", -1),
                    imagePath = o.optString("imagePath", ""),
                    aspectRatio = o.optDouble("aspectRatio", 0.7386).toFloat(),
                    sections = sList
                )
            )
        }
        pages = pList

        val uList = mutableListOf<UnitSummary>()
        val uArr = bookJson.getJSONArray("units")
        for (i in 0 until uArr.length()) {
            val o = uArr.getJSONObject(i)
            uList.add(
                UnitSummary(
                    unitNumber = o.getInt("unitNumber"),
                    title = o.getString("title"),
                    lessonPageId = o.getString("lessonPageId"),
                    exercisePageId = o.getString("exercisePageId"),
                    lessonBookPage = o.getInt("lessonBookPage"),
                    exerciseBookPage = o.getInt("exerciseBookPage"),
                    lessonPageIndex = o.getInt("lessonPageIndex"),
                    exercisePageIndex = o.getInt("exercisePageIndex"),
                    answerKey = o.optString("answerKey", ""),
                    answerKeyImagePath = o.optString("answerKeyImagePath", "")
                )
            )
        }
        units = uList

        val rList = mutableListOf<ResourceEntry>()
        val rArr = bookJson.optJSONArray("resources")
        if (rArr != null) {
            for (i in 0 until rArr.length()) {
                val o = rArr.getJSONObject(i)
                rList.add(
                    ResourceEntry(
                        id = o.getString("id"),
                        category = o.getString("category"),
                        title = o.getString("title"),
                        subtitle = o.getString("subtitle"),
                        startPageIndex = o.getInt("startPageIndex"),
                        pageCount = o.getInt("pageCount")
                    )
                )
            }
        }
        resources = rList

        val quizJson = JSONObject(readAsset(context, "quiz_data.json"))
        val mList = mutableListOf<McqQuestion>()
        val mArr = quizJson.optJSONArray("mcqs")
        if (mArr != null) {
            for (i in 0 until mArr.length()) {
                val o = mArr.getJSONObject(i)
                val uNums = mutableListOf<Int>()
                val uA = o.getJSONArray("unitNumbers")
                for (j in 0 until uA.length()) uNums.add(uA.getInt(j))
                val opts = mutableListOf<McqOption>()
                val oA = o.getJSONArray("options")
                for (j in 0 until oA.length()) {
                    val oo = oA.getJSONObject(j)
                    opts.add(McqOption(oo.getString("key"), oo.getString("text")))
                }
                val cKeys = mutableListOf<String>()
                val cA = o.getJSONArray("correctKeys")
                for (j in 0 until cA.length()) cKeys.add(cA.getString(j))
                mList.add(McqQuestion(o.getString("id"), uNums, o.getString("prompt"), opts, cKeys))
            }
        }
        mcqs = mList

        val wList = mutableListOf<WrittenQuestion>()
        val wArr = quizJson.optJSONArray("writtens")
        if (wArr != null) {
            for (i in 0 until wArr.length()) {
                val o = wArr.getJSONObject(i)
                val uNums = mutableListOf<Int>()
                val uA = o.getJSONArray("unitNumbers")
                for (j in 0 until uA.length()) uNums.add(uA.getInt(j))
                val acc = mutableListOf<String>()
                val aA = o.getJSONArray("acceptedAnswers")
                for (j in 0 until aA.length()) acc.add(aA.getString(j))
                wList.add(
                    WrittenQuestion(
                        id = o.getString("id"),
                        unitNumbers = uNums,
                        sourceLabel = o.getString("sourceLabel"),
                        instruction = o.optString("instruction", ""),
                        prompt = o.getString("prompt"),
                        acceptedAnswers = acc
                    )
                )
            }
        }
        writtens = wList
    }

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
