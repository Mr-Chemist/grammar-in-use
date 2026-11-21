package ir.hiwa.grammarinuse

import android.view.View

enum class AppLang(val code: String) {
    EN("en"),
    FA("fa");

    val layoutDir: Int
        get() = if (this == FA) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    companion object {
        fun fromCode(code: String?): AppLang = if (code == "fa") FA else EN
    }
}

/**
 * Complete bilingual string table (Default: English, switchable to Persian in Settings).
 */
object Strings {
    fun t(lang: AppLang, en: String, fa: String): String = if (lang == AppLang.FA) fa else en

    fun kindLabel(lang: AppLang, kind: String): String = when (kind) {
        "lesson" -> t(lang, "Lesson", "آموزش")
        "exercise" -> t(lang, "Exercises", "تمرین")
        "appendix" -> t(lang, "Appendix", "ضمیمه")
        "additional" -> t(lang, "Additional", "تمرین اضافه")
        else -> "Study Guide"
    }

    fun pageHeader(lang: AppLang, unitNum: Int, bookPage: Int, kind: String): String {
        val k = kindLabel(lang, kind)
        return if (unitNum > 0) {
            if (lang == AppLang.FA) "یونیت $unitNum · صفحه $bookPage ($k)"
            else "Unit $unitNum · p. $bookPage ($k)"
        } else {
            if (lang == AppLang.FA) "صفحه $bookPage ($k)"
            else "p. $bookPage ($k)"
        }
    }

    fun quizBtn(lang: AppLang) = t(lang, "Quiz", "آزمون")
    fun keyBtn(lang: AppLang) = t(lang, "Key", "پاسخ")
    fun quizTitle(lang: AppLang, unit: Int) = t(lang, "Unit $unit Quiz", "آزمون یونیت $unit")
    fun tabMcq(lang: AppLang, n: Int) = t(lang, "Multiple Choice ($n)", "تستی ($n)")
    fun tabWritten(lang: AppLang, n: Int) = t(lang, "Fill in the Blank ($n)", "تشریحی ($n)")
    fun questionCounter(lang: AppLang, idx: Int, total: Int, multi: Boolean): String {
        val base = t(lang, "Question $idx of $total", "سوال $idx از $total")
        return if (multi) "$base ${t(lang, "(Multiple answers)", "(چندگزینه‌ای)")}" else base
    }
    fun exerciseCounter(lang: AppLang, idx: Int, total: Int, source: String) =
        t(lang, "Item $idx of $total · $source", "تمرین $idx از $total · $source")
    fun checkAnswer(lang: AppLang) = t(lang, "Check Answer", "بررسی پاسخ")
    fun showAnswer(lang: AppLang) = t(lang, "Show Key", "نمایش جواب")
    fun correctAnswerLabel(lang: AppLang, ans: String) = t(lang, "Correct answer: $ans", "پاسخ صحیح: $ans")
    fun writtenCorrect(lang: AppLang) = t(lang, "Correct!", "آفرین! پاسخ صحیح است.")
    fun writtenWrong(lang: AppLang) = t(lang, "Not quite. Accepted answer:", "پاسخ دقیق نبود؛ جواب صحیح:")
    fun typeBlankHint(lang: AppLang) = t(lang, "Type the missing words...", "پاسخ جای خالی را تایپ کنید...")
    fun prevQ(lang: AppLang) = t(lang, "Previous", "قبلی")
    fun nextQ(lang: AppLang) = t(lang, "Next", "بعدی")
    fun noMcq(lang: AppLang) = t(
        lang,
        "No Study Guide MCQ linked to this unit. Try the Fill in the Blank tab.",
        "سوال تستی از Study Guide برای این یونیت یافت نشد؛ تب «تشریحی» را امتحان کنید."
    )
    fun noWritten(lang: AppLang) = t(lang, "No written exercises available for this unit.", "تمرین تشریحی برای این یونیت موجود نیست.")

    fun answerKeyTitle(lang: AppLang, unit: Int) = t(lang, "Answer Key · Unit $unit", "پاسخ‌نامه یونیت $unit")
    fun answerKeyMissing(lang: AppLang) = t(lang, "Answer key is not available for this unit.", "پاسخ‌نامه این یونیت موجود نیست.")
    fun close(lang: AppLang) = t(lang, "Close", "بستن")

    fun downloadManagerTitle(lang: AppLang) = t(lang, "Download Manager", "مدیریت دانلود")
    fun storageUsed(lang: AppLang, mb: Double, files: Int) =
        t(lang, "Storage used: $mb MB ($files files)", "حجم ذخیره‌شده روی گوشی: $mb مگابایت ($files فایل)")
    fun installed(lang: AppLang) = t(lang, "Downloaded", "دانلود شده")
    fun download(lang: AppLang) = t(lang, "Download", "دانلود")
    fun extracting(lang: AppLang) = t(lang, "Extracting...", "استخراج...")
    fun clearCache(lang: AppLang) = t(lang, "Clear Downloaded Files", "پاک‌سازی فایل‌های دانلودشده")
    fun downloadingPack(lang: AppLang, name: String, pct: Int) =
        t(lang, "Downloading $name: $pct%", "در حال دانلود $name: $pct%")
    fun extractingPack(lang: AppLang, name: String) =
        t(lang, "Extracting $name...", "در حال استخراج $name...")
    fun downloadFailed(lang: AppLang) =
        t(lang, "Download failed. Check your internet connection and retry.", "خطا در دانلود. اتصال اینترنت را بررسی کرده و دوباره تلاش کنید.")
    fun retry(lang: AppLang) = t(lang, "Retry", "تلاش مجدد")

    fun menuTitle(lang: AppLang) = t(lang, "Contents & Checklist", "فهرست کتاب و چک‌لیست")
    fun tabUnits(lang: AppLang) = t(lang, "145 Units", "۱۴۵ یونیت")
    fun tabResources(lang: AppLang) = t(lang, "Appendices & Guide", "ضمائم و راهنما")
    fun statsLine(lang: AppLang, doneUnits: Int, totalUnits: Int, donePages: Int, totalPages: Int) =
        t(lang, "Units: $doneUnits / $totalUnits    |    Pages: $donePages / $totalPages", "یونیت‌ها: $doneUnits از $totalUnits    |    صفحات: $donePages از $totalPages")
    fun searchHint(lang: AppLang) = t(lang, "Search unit, page number, or topic...", "جستجوی یونیت، شماره صفحه یا موضوع...")
    fun filterAll(lang: AppLang) = t(lang, "All", "همه")
    fun filterTodo(lang: AppLang) = t(lang, "Remaining", "باقی‌مانده")
    fun filterDone(lang: AppLang) = t(lang, "Completed", "تکمیل‌شده")
    fun filterStarred(lang: AppLang) = t(lang, "Bookmarked", "نشان‌دار")
    fun unitLabel(lang: AppLang, n: Int) = t(lang, "Unit $n", "یونیت $n")
    fun lessonChip(lang: AppLang, p: Int) = t(lang, "Lesson · p. $p", "آموزش · ص $p")
    fun exerciseChip(lang: AppLang, p: Int) = t(lang, "Exercises · p. $p", "تمرین · ص $p")
    fun pagesCount(lang: AppLang, n: Int) = t(lang, "$n pages", "$n صفحه")

    fun settingsTitle(lang: AppLang) = t(lang, "Settings", "تنظیمات")
    fun languageLabel(lang: AppLang) = t(lang, "App Language", "زبان برنامه")
    fun warmModeLabel(lang: AppLang) = t(lang, "Eye-Comfort (Warm) Mode", "حالت مطالعه گرم (محافظ چشم)")
    fun replayTour(lang: AppLang) = t(lang, "Show Quick Guide Again", "نمایش مجدد آموزش برنامه")
    fun openDownloads(lang: AppLang) = t(lang, "Open Download Manager", "باز کردن مدیریت دانلود")

    fun exitTitle(lang: AppLang) = t(lang, "Exit App?", "خروج از برنامه")
    fun exitMsg(lang: AppLang) = t(lang, "Do you want to exit English Grammar In Use?", "آیا می‌خواهید از برنامه خارج شوید؟")
    fun exitYes(lang: AppLang) = t(lang, "Exit", "خروج")
    fun cancel(lang: AppLang) = t(lang, "Cancel", "انصراف")

    fun addNoteTitle(lang: AppLang) = t(lang, "Text Note", "یادداشت متنی")
    fun addNoteHint(lang: AppLang) = t(lang, "Write your note here...", "یادداشت خود را اینجا بنویسید...")
    fun save(lang: AppLang) = t(lang, "Save", "ذخیره")
    fun delete(lang: AppLang) = t(lang, "Delete", "حذف")
    fun clearDrawingsConfirm(lang: AppLang) = t(lang, "Clear all drawings and notes on this page?", "همه خط‌کشی‌ها و یادداشت‌های این صفحه پاک شود؟")
    fun clear(lang: AppLang) = t(lang, "Clear", "پاک کردن")
    fun tapToPlaceNote(lang: AppLang) = t(lang, "Tap anywhere on the page to place a note", "روی هر کجای صفحه بزنید تا یادداشت قرار گیرد")

    fun skip(lang: AppLang) = t(lang, "Skip", "رد شدن")
    fun getStarted(lang: AppLang) = t(lang, "Get Started", "شروع یادگیری")
    fun onboardingSteps(lang: AppLang): List<Triple<String, String, String>> = listOf(
        Triple(
            "📖",
            t(lang, "Crisp PDF & Native Audio", "صفحات PDF باکیفیت و صوت اصلی"),
            t(lang, "Pinch or double-tap to zoom sharply. Tap any section (A–F) on the page or bottom bar to listen to Cambridge audio.",
                "با دو انگشت یا دوبار ضربه، صفحات PDF را با کیفیت بالا زوم کنید. روی هر بخش (A تا F) بزنید تا صوت اصلی پخش شود.")
        ),
        Triple(
            "✎",
            t(lang, "Draw, Highlight & Notes", "مداد، هایلایت و یادداشت آزاد"),
            t(lang, "Tap the pen icon to draw, highlight, or place movable text notes anywhere on the page. Everything saves automatically.",
                "با دکمه مداد می‌توانید روی صفحه بنویسید، هایلایت کنید یا هر کجا خواستید یادداشت متنی بگذارید؛ همه‌چیز خودکار ذخیره می‌شود.")
        ),
        Triple(
            "✓",
            t(lang, "Unit Quizzes & Answer Key", "آزمون یونیت و پاسخ‌نامه اصلی"),
            t(lang, "Practice with multiple-choice and fill-in-the-blank quizzes with smart grading, or view the exact Cambridge Answer Key.",
                "در بخش آزمون، سوالات تستی و جای‌خالی را با تصحیح هوشمند حل کنید یا پاسخ‌نامه دقیق کتاب را ببینید.")
        ),
        Triple(
            "↓",
            t(lang, "Offline Packs & Settings", "مدیریت دانلود و تنظیمات"),
            t(lang, "Download units once for offline reading. Switch between English and Persian anytime in Settings.",
                "بسته‌ها را یک‌بار برای مطالعه آفلاین دانلود کنید. هر زمان خواستید زبان برنامه را از تنظیمات تغییر دهید.")
        )
    )
}
