package ir.hiwa.grammarinuse

data class SectionHotspot(
    val label: String,
    val audioPath: String?,
    val topPct: Float,
    val heightPct: Float
)

data class BookPage(
    val id: String,
    val unitNumber: Int,
    val unitTitle: String,
    val bookPage: Int,
    val kind: String, // lesson, exercise, appendix, additional, study_guide
    val imagePath: String,
    val aspectRatio: Float,
    val sections: List<SectionHotspot>
)

data class UnitSummary(
    val unitNumber: Int,
    val title: String,
    val lessonPageId: String,
    val exercisePageId: String,
    val lessonBookPage: Int,
    val exerciseBookPage: Int,
    val lessonPageIndex: Int,
    val exercisePageIndex: Int,
    val answerKey: String
)

data class ResourceEntry(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val startPageIndex: Int,
    val pageCount: Int
)

data class McqOption(val key: String, val text: String)

data class McqQuestion(
    val id: String,
    val unitNumbers: List<Int>,
    val prompt: String,
    val options: List<McqOption>,
    val correctKeys: List<String>
)

data class WrittenQuestion(
    val id: String,
    val unitNumbers: List<Int>,
    val sourceLabel: String,
    val prompt: String,
    val acceptedAnswers: List<String>
)

data class PackDef(
    val id: String,
    val remotePath: String,
    val title: String,
    val unitStart: Int,
    val unitEnd: Int
)

object ArvanConfig {
    const val BASE_URL = "https://grammar-in-use-assets.s3.ir-central1.arvanstorage.ir"

    val PACKS: List<PackDef> = listOf(
        PackDef("pack_1", "packs/pack_1.zip", "بسته ۱: یونیت ۱ تا ۳۰ (صفحات + صوت)", 1, 30),
        PackDef("pack_2", "packs/pack_2.zip", "بسته ۲: یونیت ۳۱ تا ۶۰ (صفحات + صوت)", 31, 60),
        PackDef("pack_3", "packs/pack_3.zip", "بسته ۳: یونیت ۶۱ تا ۹۰ (صفحات + صوت)", 61, 90),
        PackDef("pack_4", "packs/pack_4.zip", "بسته ۴: یونیت ۹۱ تا ۱۲۰ (صفحات + صوت)", 91, 120),
        PackDef("pack_5", "packs/pack_5.zip", "بسته ۵: یونیت ۱۲۱ تا ۱۴۵ (صفحات + صوت)", 121, 145),
        PackDef("pack_resources", "packs/pack_resources.zip", "بسته ضمائم، تمرین اضافه و Study Guide", 0, 0)
    )

    fun packForPage(page: BookPage): PackDef {
        if (page.unitNumber <= 0) return PACKS.last()
        return PACKS.firstOrNull { page.unitNumber in it.unitStart..it.unitEnd } ?: PACKS.first()
    }
}
