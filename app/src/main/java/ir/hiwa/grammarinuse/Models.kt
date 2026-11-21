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
    val pdfPath: String,
    val pdfPageIndex: Int,
    val imagePath: String, // optional fallback
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
    val answerKey: String,
    val answerKeyImagePath: String
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
    val instruction: String,
    val prompt: String,
    val acceptedAnswers: List<String>
)

/**
 * Normalized (0..1) freehand stroke & movable sticky text note models for Paint-style annotations.
 */
data class StrokePoint(val x: Float, val y: Float)

data class AnnotationStroke(
    val color: Int,
    val widthDp: Float,
    val isHighlighter: Boolean,
    val points: MutableList<StrokePoint> = mutableListOf()
)

data class TextAnnotation(
    val id: String,
    var x: Float,
    var y: Float,
    var text: String,
    var color: Int
)

data class PageAnnotations(
    val strokes: MutableList<AnnotationStroke> = mutableListOf(),
    val notes: MutableList<TextAnnotation> = mutableListOf()
) {
    fun isEmpty(): Boolean = strokes.isEmpty() && notes.isEmpty()
}

data class PackDef(
    val id: String,
    val remotePath: String,
    val titleEn: String,
    val titleFa: String,
    val unitStart: Int,
    val unitEnd: Int
) {
    fun title(lang: AppLang): String = if (lang == AppLang.FA) titleFa else titleEn
}

object ArvanConfig {
    const val BASE_URL = "https://grammar-in-use-assets.s3.ir-central1.arvanstorage.ir"

    val PACKS: List<PackDef> = listOf(
        PackDef("pack_1", "packs/pack_1.zip", "Pack 1: Units 1–30", "بسته ۱: یونیت ۱ تا ۳۰", 1, 30),
        PackDef("pack_2", "packs/pack_2.zip", "Pack 2: Units 31–60", "بسته ۲: یونیت ۳۱ تا ۶۰", 31, 60),
        PackDef("pack_3", "packs/pack_3.zip", "Pack 3: Units 61–90", "بسته ۳: یونیت ۶۱ تا ۹۰", 61, 90),
        PackDef("pack_4", "packs/pack_4.zip", "Pack 4: Units 91–120", "بسته ۴: یونیت ۹۱ تا ۱۲۰", 91, 120),
        PackDef("pack_5", "packs/pack_5.zip", "Pack 5: Units 121–145", "بسته ۵: یونیت ۱۲۱ تا ۱۴۵", 121, 145),
        PackDef("pack_resources", "packs/pack_resources.zip", "Appendices & Study Guide", "ضمائم، تمرین اضافه و Study Guide", 0, 0)
    )

    fun packForPage(page: BookPage): PackDef {
        if (page.unitNumber <= 0) return PACKS.last()
        return PACKS.firstOrNull { page.unitNumber in it.unitStart..it.unitEnd } ?: PACKS.first()
    }
}
