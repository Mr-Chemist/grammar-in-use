package ir.hiwa.grammarinuse

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ir.hiwa.grammarinuse.databinding.ActivityMainBinding
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: BookRepository
    private lateinit var store: ProgressStore
    private lateinit var packManager: PackManager
    private val pdfRenderer = PdfPageRenderer()

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var pageIndex = 0
    private var isFullscreen = false
    private var isPenMode = false
    private var activePenTool = DrawTool.PEN
    private var lastRenderedWidth = 0
    private var currentPdfFile: File? = null

    private val penColors = listOf(
        Color.parseColor("#0F172A"), // Ink Black
        Color.parseColor("#2563EB"), // Blue
        Color.parseColor("#DC2626"), // Red
        Color.parseColor("#16A34A"), // Green
        Color.parseColor("#FACC15")  // Yellow Highlighter
    )
    private val penWidths = listOf(2.0f, 3.8f, 6.2f)
    private var selectedColorIdx = 0
    private var selectedWidthIdx = 1

    // Audio playback state (stops automatically on screen off / onPause)
    private var mediaPlayer: MediaPlayer? = null
    private var activeSectionLabel: String? = null
    private var activeAudioKey: String? = null
    private var isLooping = false
    private var loopStartMs: Int? = null
    private var loopEndMs: Int? = null
    private var lastLoadedUnit = -1
    private val speedSteps = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    private val progressTicker = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val pos = mp.currentPosition
                val dur = mp.duration.coerceAtLeast(1)
                binding.audioSeekBar.max = dur
                binding.audioSeekBar.progress = pos
                binding.tvPosTime.text = formatTime(pos)
                binding.tvDurTime.text = formatTime(dur)

                val a = loopStartMs
                val b = loopEndMs
                if (a != null && b != null && b > a + 400 && pos >= b) {
                    mp.seekTo(a)
                }
                uiHandler.postDelayed(this, 180)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix Android 15+ Edge-to-Edge so topBar never sits behind status bar/notch
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, if (isFullscreen) 0 else bars.top, bars.right, bars.bottom)
            insets
        }

        repo = BookRepository(this)
        store = ProgressStore(this)
        packManager = PackManager(this)

        pageIndex = store.lastPageIndex.coerceIn(0, (repo.pages.size - 1).coerceAtLeast(0))

        setupListeners()
        setupBackHandler()
        buildPenStyleRow()
        applyLanguageUI()
        applyWarmModeUI()
        renderCurrentPage()
        runAnimatedSplash()
    }

    override fun onPause() {
        super.onPause()
        // Stop/pause audio whenever screen turns off or app goes to background
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                uiHandler.removeCallbacks(progressTicker)
                updatePlayPauseUI()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudio()
        pdfRenderer.close()
    }

    private fun lang(): AppLang = store.language
    private fun currentPage(): BookPage = repo.pages[pageIndex.coerceIn(0, repo.pages.lastIndex)]

    private fun currentUnitSections(): List<SectionHotspot> {
        val p = currentPage()
        if (p.unitNumber <= 0) return emptyList()
        if (p.kind == "lesson") return p.sections
        return repo.pages.firstOrNull { it.unitNumber == p.unitNumber && it.kind == "lesson" }?.sections ?: emptyList()
    }

    /**
     * Minimalist symbolic Cambridge Splash Screen:
     * White bg + "ENGLISH GRAMMAR IN USE" -> Cambridge Blue (#3B82C4) bg, white "ENGLISH GRAMMAR",
     * black "IN USE", yellow bar, then smooth fade out -> optional first-launch Onboarding Tour.
     */
    private fun runAnimatedSplash() {
        val overlay = binding.splashOverlay
        overlay.visibility = View.VISIBLE

        val fadeTitle = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.splashEnglish, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.splashGrammar, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.splashInUse, "alpha", 0f, 1f)
            )
            duration = 420L
        }

        val bgAnim = ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.parseColor("#3B82C4")).apply {
            duration = 620L
            startDelay = 460L
            addUpdateListener { overlay.setBackgroundColor(it.animatedValue as Int) }
        }
        val titleWhiteAnim = ValueAnimator.ofObject(ArgbEvaluator(), Color.parseColor("#0F172A"), Color.WHITE).apply {
            duration = 620L
            startDelay = 460L
            addUpdateListener {
                val c = it.animatedValue as Int
                binding.splashEnglish.setTextColor(c)
                binding.splashGrammar.setTextColor(c)
            }
        }
        val inUseBlackAnim = ValueAnimator.ofObject(ArgbEvaluator(), Color.parseColor("#3B82C4"), Color.parseColor("#0F172A")).apply {
            duration = 620L
            startDelay = 460L
            addUpdateListener { binding.splashInUse.setTextColor(it.animatedValue as Int) }
        }
        val barAnim = ObjectAnimator.ofFloat(binding.splashYellowBar, "scaleX", 0f, 1f).apply {
            duration = 420L
            startDelay = 820L
        }
        val authorAnim = ObjectAnimator.ofFloat(binding.splashAuthor, "alpha", 0f, 1f).apply {
            duration = 380L
            startDelay = 950L
        }

        AnimatorSet().apply {
            playTogether(fadeTitle, bgAnim, titleWhiteAnim, inUseBlackAnim, barAnim, authorAnim)
            start()
        }

        uiHandler.postDelayed({
            overlay.animate()
                .alpha(0f)
                .setDuration(350L)
                .withEndAction {
                    overlay.visibility = View.GONE
                    if (!store.hasSeenOnboarding) {
                        Dialogs.showOnboarding(this, lang()) {
                            store.hasSeenOnboarding = true
                        }
                    }
                }
                .start()
        }, 1750L)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) {
                    toggleFullscreen(false)
                    return
                }
                if (isPenMode) {
                    setPenMode(false)
                    return
                }
                val l = lang()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(Strings.exitTitle(l))
                    .setMessage(Strings.exitMsg(l))
                    .setPositiveButton(Strings.exitYes(l)) { _, _ -> finish() }
                    .setNegativeButton(Strings.cancel(l), null)
                    .show()
            }
        })
    }

    private fun applyLanguageUI() {
        val l = lang()
        binding.rootLayout.layoutDirection = l.layoutDir
        binding.btnQuiz.text = Strings.quizBtn(l)
        binding.btnAnswerKey.text = Strings.keyBtn(l)
        binding.btnRetryDownload.text = Strings.retry(l)
        updateHeaderTexts()
    }

    private fun updateHeaderTexts() {
        val p = currentPage()
        binding.tvHeaderTitle.text = Strings.pageHeader(lang(), p.unitNumber, p.bookPage, p.kind)
        binding.tvHeaderSubtitle.text = p.unitTitle
    }

    private fun setupListeners() {
        binding.btnMenu.setOnClickListener { openChecklist() }
        binding.titleBlock.setOnClickListener { openChecklist() }

        binding.btnQuiz.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber <= 0) return@setOnClickListener
            val unitMcqs = repo.mcqs.filter { p.unitNumber in it.unitNumbers }
            val unitWrittens = repo.writtens.filter { p.unitNumber in it.unitNumbers }
            QuizDialog.show(this, lang(), p.unitNumber, p.unitTitle, unitMcqs, unitWrittens) { c, t ->
                store.setQuizScore(p.unitNumber, c, t)
            }
        }

        binding.btnAnswerKey.setOnClickListener {
            val p = currentPage()
            val summary = repo.units.firstOrNull { it.unitNumber == p.unitNumber }
            val keyFile = summary?.answerKeyImagePath?.takeIf { it.isNotBlank() }?.let { packManager.getFile(it) }
            Dialogs.showAnswerKey(this, lang(), p.unitNumber, keyFile, summary?.answerKey ?: "")
        }

        binding.btnBookmark.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber > 0) {
                store.toggleBookmark(p.unitNumber)
                updateHeaderChecksUI()
            }
        }

        binding.btnPen.setOnClickListener { setPenMode(!isPenMode) }
        binding.btnPenDone.setOnClickListener { setPenMode(false) }

        binding.toolPen.setOnClickListener { selectDrawTool(DrawTool.PEN) }
        binding.toolHighlighter.setOnClickListener { selectDrawTool(DrawTool.HIGHLIGHTER) }
        binding.toolTextNote.setOnClickListener {
            selectDrawTool(DrawTool.TEXT)
            Toast.makeText(this, Strings.tapToPlaceNote(lang()), Toast.LENGTH_SHORT).show()
        }
        binding.toolEraser.setOnClickListener { selectDrawTool(DrawTool.ERASER) }

        binding.btnPenUndo.setOnClickListener { binding.pageView.undoLastAnnotation() }
        binding.btnPenClear.setOnClickListener {
            val l = lang()
            AlertDialog.Builder(this)
                .setMessage(Strings.clearDrawingsConfirm(l))
                .setPositiveButton(Strings.clear(l)) { _, _ -> binding.pageView.clearAllAnnotations() }
                .setNegativeButton(Strings.cancel(l), null)
                .show()
        }

        binding.btnFullscreen.setOnClickListener { toggleFullscreen(true) }
        binding.btnExitFullscreen.setOnClickListener { toggleFullscreen(false) }

        binding.btnSettings.setOnClickListener {
            Dialogs.showSettings(
                context = this,
                store = store,
                onLanguageChanged = { applyLanguageUI() },
                onWarmToggled = { applyWarmModeUI() },
                onReplayTour = { Dialogs.showOnboarding(this, lang()) {} },
                onOpenDownloads = {
                    Dialogs.showDownloadManager(this, lang(), packManager) { renderCurrentPage() }
                }
            )
        }

        binding.btnCheckPage.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber > 0) {
                val next = !store.isPageDone(p.id)
                store.setPageDone(p.id, next)
                val summary = repo.units.firstOrNull { it.unitNumber == p.unitNumber }
                if (summary != null && store.isPageDone(summary.lessonPageId) && store.isPageDone(summary.exercisePageId)) {
                    store.setUnitDone(p.unitNumber, true)
                }
                updateHeaderChecksUI()
            }
        }

        binding.btnCheckUnit.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber > 0) {
                store.setUnitDone(p.unitNumber, !store.isUnitDone(p.unitNumber))
                updateHeaderChecksUI()
            }
        }

        binding.btnPrevPage.setOnClickListener { goToPage(pageIndex - 1) }
        binding.btnNextPage.setOnClickListener { goToPage(pageIndex + 1) }

        binding.pageView.onSwipeNext = { goToPage(pageIndex + 1) }
        binding.pageView.onSwipePrev = { goToPage(pageIndex - 1) }
        binding.pageView.onSectionTapped = { sec -> playSection(sec) }
        binding.pageView.onAnnotationsChanged = { ann ->
            store.savePageAnnotations(currentPage().id, ann)
            updateHeaderChecksUI()
        }
        binding.pageView.onRequestAddTextNote = { nx, ny ->
            Dialogs.showTextNoteEditor(this, lang(), "", false, onSave = { txt ->
                val note = TextAnnotation(
                    id = UUID.randomUUID().toString(),
                    x = nx,
                    y = ny,
                    text = txt,
                    color = penColors[selectedColorIdx]
                )
                binding.pageView.addOrUpdateTextNote(note)
            })
        }
        binding.pageView.onRequestEditTextNote = { note ->
            Dialogs.showTextNoteEditor(
                this,
                lang(),
                note.text,
                true,
                onSave = { txt ->
                    note.text = txt
                    binding.pageView.addOrUpdateTextNote(note)
                },
                onDelete = { binding.pageView.deleteTextNote(note.id) }
            )
        }
        binding.pageView.onZoomSettled = { scale ->
            // Dynamically re-render PDF page at higher DPI when user zooms in!
            val p = currentPage()
            val pdfFile = currentPdfFile
            if (pdfFile != null && p.pdfPageIndex >= 0) {
                val baseW = resources.displayMetrics.widthPixels.coerceAtLeast(1080)
                val desiredW = (baseW * scale.coerceIn(1f, 2.3f)).toInt().coerceIn(1200, 2500)
                if (desiredW > lastRenderedWidth + 280) {
                    val targetPageId = p.id
                    val pIdx = p.pdfPageIndex
                    ioExecutor.execute {
                        val hiBmp = pdfRenderer.renderPage(pdfFile, pIdx, desiredW)
                        if (hiBmp != null) {
                            uiHandler.post {
                                if (currentPage().id == targetPageId) {
                                    lastRenderedWidth = desiredW
                                    binding.pageView.updateBitmapPreservingView(hiBmp)
                                }
                            }
                        }
                    }
                }
            }
        }

        binding.btnRetryDownload.setOnClickListener { renderCurrentPage() }

        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnRew5.setOnClickListener { seekRelative(-5000) }
        binding.btnFwd5.setOnClickListener { seekRelative(5000) }
        binding.btnPrevSec.setOnClickListener { jumpSection(-1) }
        binding.btnNextSec.setOnClickListener { jumpSection(1) }
        binding.btnSpeed.setOnClickListener { cycleSpeed() }
        binding.btnRepeat.setOnClickListener { toggleRepeat() }

        binding.audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvPosTime.text = formatTime(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                mediaPlayer?.seekTo(sb?.progress ?: 0)
            }
        })
    }

    private fun setPenMode(enable: Boolean) {
        isPenMode = enable
        binding.penToolbar.visibility = if (enable) View.VISIBLE else View.GONE
        binding.pageView.drawTool = if (enable) activePenTool else DrawTool.NONE
        binding.btnPen.setBackgroundResource(if (enable) R.drawable.bg_pill_primary else R.drawable.bg_sharp_btn)
        binding.btnPen.setTextColor(if (enable) Color.WHITE else Color.parseColor(if (store.hasPageAnnotations(currentPage().id)) "#0F766E" else "#64748B"))
        if (enable) updatePenToolButtons()
    }

    private fun selectDrawTool(tool: DrawTool) {
        activePenTool = tool
        if (isPenMode) {
            binding.pageView.drawTool = tool
            updatePenToolButtons()
        }
    }

    private fun updatePenToolButtons() {
        val map = listOf(
            binding.toolPen to DrawTool.PEN,
            binding.toolHighlighter to DrawTool.HIGHLIGHTER,
            binding.toolTextNote to DrawTool.TEXT,
            binding.toolEraser to DrawTool.ERASER
        )
        for ((v, t) in map) {
            val sel = activePenTool == t
            v.setBackgroundResource(if (sel) R.drawable.bg_pill_primary else R.drawable.bg_sharp_btn)
            if (t != DrawTool.HIGHLIGHTER) {
                v.setTextColor(if (sel) Color.WHITE else Color.parseColor("#0F172A"))
            }
        }
    }

    private fun buildPenStyleRow() {
        val row = binding.penStyleRow
        row.removeAllViews()
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        // 5 Color Swatches
        penColors.forEachIndexed { idx, col ->
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(col)
                    setStroke(px(if (idx == selectedColorIdx) 2 else 1), Color.parseColor(if (idx == selectedColorIdx) "#0F766E" else "#CBD5E1"))
                }
                setOnClickListener {
                    selectedColorIdx = idx
                    binding.pageView.strokeColor = col
                    buildPenStyleRow()
                }
            }
            row.addView(swatch, LinearLayout.LayoutParams(px(20), px(20)).apply { marginEnd = px(6) })
        }

        // Divider
        row.addView(View(this).apply { setBackgroundColor(Color.parseColor("#CBD5E1")) },
            LinearLayout.LayoutParams(px(1), px(16)).apply { marginStart = px(2); marginEnd = px(8) })

        // 3 Stroke Width Dots
        penWidths.forEachIndexed { idx, wDp ->
            val dotSize = px(6 + idx * 4)
            val holder = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (idx == selectedWidthIdx) "#CCFBF1" else "#F8FAFC"))
                    setStroke(px(1), Color.parseColor(if (idx == selectedWidthIdx) "#0F766E" else "#CBD5E1"))
                    cornerRadius = 999f
                }
                setOnClickListener {
                    selectedWidthIdx = idx
                    binding.pageView.strokeWidthDp = wDp
                    buildPenStyleRow()
                }
            }
            holder.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#0F172A"))
                }
            }, LinearLayout.LayoutParams(dotSize, dotSize))
            row.addView(holder, LinearLayout.LayoutParams(px(24), px(24)).apply { marginEnd = px(5) })
        }
        binding.pageView.strokeColor = penColors[selectedColorIdx]
        binding.pageView.strokeWidthDp = penWidths[selectedWidthIdx]
    }

    private fun openChecklist() {
        Dialogs.showChecklist(this, lang(), repo, store, pageIndex, { idx -> goToPage(idx) }, { updateHeaderChecksUI() })
    }

    private fun toggleFullscreen(enable: Boolean) {
        isFullscreen = enable
        binding.topBar.visibility = if (enable) View.GONE else View.VISIBLE
        binding.bookProgressBar.visibility = if (enable) View.GONE else View.VISIBLE
        binding.btnExitFullscreen.visibility = if (enable) View.VISIBLE else View.GONE
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enable) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        ViewCompat.requestApplyInsets(binding.rootLayout)
    }

    private fun applyWarmModeUI() {
        val warm = store.warmMode
        binding.pageView.setWarmMode(warm)
        binding.pageViewport.setBackgroundColor(Color.parseColor(if (warm) "#E8DEC8" else "#E2E8F0"))
    }

    private fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, repo.pages.lastIndex)
        if (clamped == pageIndex) return
        if (isPenMode) setPenMode(false)
        pageIndex = clamped
        store.lastPageIndex = pageIndex
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val p = currentPage()
        if (lastLoadedUnit != -1 && lastLoadedUnit != p.unitNumber) {
            releaseAudio()
        }
        lastLoadedUnit = p.unitNumber

        val isUnit = p.unitNumber > 0
        updateHeaderTexts()

        binding.btnQuiz.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnAnswerKey.visibility = if (p.kind == "exercise") View.VISIBLE else View.GONE
        binding.btnBookmark.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnCheckPage.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnCheckUnit.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.audioControlsBox.visibility = if (isUnit) View.VISIBLE else View.GONE

        binding.btnPrevPage.alpha = if (pageIndex > 0) 1f else 0.35f
        binding.btnNextPage.alpha = if (pageIndex < repo.pages.lastIndex) 1f else 0.35f
        binding.btnSpeed.text = "${store.playbackRate.toString().removeSuffix(".0")}x"

        updateHeaderChecksUI()
        rebuildSectionPills()

        // Try native PDF pack first, with optional JPEG fallback
        val pdfFile = if (p.pdfPath.isNotBlank()) packManager.getFile(p.pdfPath) else null
        val imgFile = if (pdfFile == null && p.imagePath.isNotBlank()) packManager.getFile(p.imagePath) else null

        if (pdfFile != null && p.pdfPageIndex >= 0) {
            displayPdfPage(pdfFile, p)
        } else if (imgFile != null) {
            currentPdfFile = null
            displayFallbackImage(imgFile, p)
        } else {
            val pack = ArvanConfig.packForPage(p)
            val l = lang()
            val packTitle = pack.title(l)
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.packSpinner.visibility = View.VISIBLE
            binding.btnRetryDownload.visibility = View.GONE
            binding.tvLoadingStatus.text = Strings.downloadingPack(l, packTitle, 0)

            ioExecutor.execute {
                val ok = packManager.downloadAndExtract(pack) { pct, ext ->
                    uiHandler.post {
                        binding.tvLoadingStatus.text =
                            if (ext) Strings.extractingPack(l, packTitle)
                            else Strings.downloadingPack(l, packTitle, pct)
                    }
                }
                uiHandler.post {
                    if (ok && currentPage().id == p.id) {
                        val readyPdf = if (p.pdfPath.isNotBlank()) packManager.getFile(p.pdfPath) else null
                        val readyImg = if (readyPdf == null && p.imagePath.isNotBlank()) packManager.getFile(p.imagePath) else null
                        if (readyPdf != null && p.pdfPageIndex >= 0) {
                            displayPdfPage(readyPdf, p)
                        } else if (readyImg != null) {
                            currentPdfFile = null
                            displayFallbackImage(readyImg, p)
                        } else {
                            showDownloadError()
                        }
                    } else if (!ok && currentPage().id == p.id) {
                        showDownloadError()
                    }
                }
            }
        }
    }

    private fun displayPdfPage(pdfFile: File, page: BookPage) {
        currentPdfFile = pdfFile
        val targetW = (resources.displayMetrics.widthPixels * 1.35f).toInt().coerceIn(1150, 1750)
        val targetId = page.id
        ioExecutor.execute {
            val bmp = pdfRenderer.renderPage(pdfFile, page.pdfPageIndex, targetW)
            uiHandler.post {
                if (currentPage().id != targetId) return@post
                binding.loadingOverlay.visibility = View.GONE
                lastRenderedWidth = targetW
                val savedAnn = store.getPageAnnotations(page.id)
                binding.pageView.setPage(bmp, if (page.kind == "lesson") page.sections else emptyList(), savedAnn)
                binding.pageView.setActiveSection(if (page.kind == "lesson") activeSectionLabel else null)
            }
        }
    }

    private fun displayFallbackImage(file: File, page: BookPage) {
        val targetId = page.id
        ioExecutor.execute {
            val bmp: Bitmap? = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
            uiHandler.post {
                if (currentPage().id != targetId) return@post
                binding.loadingOverlay.visibility = View.GONE
                val savedAnn = store.getPageAnnotations(page.id)
                binding.pageView.setPage(bmp, if (page.kind == "lesson") page.sections else emptyList(), savedAnn)
                binding.pageView.setActiveSection(if (page.kind == "lesson") activeSectionLabel else null)
            }
        }
    }

    private fun showDownloadError() {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.packSpinner.visibility = View.GONE
        binding.btnRetryDownload.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = Strings.downloadFailed(lang())
    }

    private fun updateHeaderChecksUI() {
        val p = currentPage()
        val pDone = store.isPageDone(p.id)
        val uDone = store.isUnitDone(p.unitNumber)
        val star = store.isBookmarked(p.unitNumber)
        val hasAnn = store.hasPageAnnotations(p.id)

        binding.btnBookmark.text = if (star) "★" else "☆"
        binding.btnBookmark.setTextColor(Color.parseColor(if (star) "#F59E0B" else "#64748B"))
        if (!isPenMode) {
            binding.btnPen.setTextColor(Color.parseColor(if (hasAnn) "#0F766E" else "#64748B"))
        }

        // Vector icon tinting for Page Check & Unit Check
        binding.btnCheckPage.setColorFilter(Color.parseColor(if (pDone) "#16A34A" else "#64748B"))
        binding.btnCheckUnit.setColorFilter(Color.parseColor(if (uDone) "#0F766E" else "#64748B"))

        val donePages = store.countCompletedPages(repo.pages)
        val totalUnitPages = (repo.units.size * 2).coerceAtLeast(1)
        binding.bookProgressBar.progress = ((donePages * 100) / totalUnitPages).coerceIn(0, 100)
    }

    private fun rebuildSectionPills() {
        val row = binding.sectionPillsRow
        row.removeAllViews()
        val secs = currentUnitSections()
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        for (sec in secs) {
            val active = activeSectionLabel == sec.label
            val pill = TextView(this).apply {
                text = sec.label
                gravity = Gravity.CENTER
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (active) Color.WHITE else Color.parseColor("#334155"))
                setBackgroundResource(if (active) R.drawable.bg_pill_primary else R.drawable.bg_pill_neutral)
                setPadding(px(10), 0, px(10), 0)
                alpha = if (sec.audioPath != null) 1f else 0.4f
                setOnClickListener { playSection(sec) }
            }
            row.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(26)).apply {
                marginEnd = px(4)
            })
        }

        if (secs.isNotEmpty()) {
            val auto = store.autoPlayNext
            val btnAuto = TextView(this).apply {
                text = "A→B"
                gravity = Gravity.CENTER
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (auto) Color.WHITE else Color.parseColor("#475569"))
                setBackgroundResource(if (auto) R.drawable.bg_pill_primary else R.drawable.bg_sharp_btn)
                setPadding(px(7), 0, px(7), 0)
                setOnClickListener {
                    store.autoPlayNext = !store.autoPlayNext
                    rebuildSectionPills()
                }
            }
            row.addView(btnAuto, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(26)).apply {
                marginStart = px(4)
                marginEnd = px(4)
            })

            val abActive = loopStartMs != null
            val abLabel = when {
                loopStartMs == null -> "A-B"
                loopEndMs == null -> "A✓"
                else -> "A↺B"
            }
            val btnAb = TextView(this).apply {
                text = abLabel
                gravity = Gravity.CENTER
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (abActive) Color.WHITE else Color.parseColor("#475569"))
                setBackgroundResource(if (abActive) R.drawable.bg_pill_primary else R.drawable.bg_sharp_btn)
                setPadding(px(7), 0, px(7), 0)
                setOnClickListener { handleAbLoopTap() }
            }
            row.addView(btnAb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(26)))
        }
    }

    private fun handleAbLoopTap() {
        val mp = mediaPlayer ?: return
        val pos = mp.currentPosition
        when {
            loopStartMs == null -> {
                loopStartMs = pos
                loopEndMs = null
            }
            loopEndMs == null -> {
                if (pos > (loopStartMs ?: 0) + 400) {
                    loopEndMs = pos
                    mp.seekTo(loopStartMs!!)
                } else {
                    loopStartMs = null
                }
            }
            else -> {
                loopStartMs = null
                loopEndMs = null
            }
        }
        rebuildSectionPills()
    }

    private fun playSection(sec: SectionHotspot) {
        val audioRel = sec.audioPath ?: return
        val p = currentPage()
        val key = "${p.unitNumber}_${sec.label}"

        if (activeAudioKey == key && mediaPlayer != null) {
            togglePlayPause()
            return
        }

        val audioFile = packManager.getFile(audioRel)
        if (audioFile == null) {
            binding.tvAudioStatus.visibility = View.VISIBLE
            binding.tvAudioStatus.text = Strings.t(lang(), "Download this pack first to play audio.", "برای پخش صوت ابتدا بسته این یونیت را دانلود کنید.")
            return
        }

        releaseAudio()
        activeSectionLabel = sec.label
        activeAudioKey = key
        loopStartMs = null
        loopEndMs = null
        binding.tvAudioStatus.visibility = View.GONE
        binding.pageView.setActiveSection(if (p.kind == "lesson") sec.label else null)
        rebuildSectionPills()

        try {
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                isLooping = this@MainActivity.isLooping
                applySpeed(this, store.playbackRate)
                setOnCompletionListener {
                    uiHandler.removeCallbacks(progressTicker)
                    updatePlayPauseUI()
                    if (!this@MainActivity.isLooping && store.autoPlayNext) {
                        jumpSection(1)
                    }
                }
                start()
            }
            mediaPlayer = mp
            binding.audioSeekBar.max = mp.duration.coerceAtLeast(1)
            binding.tvDurTime.text = formatTime(mp.duration)
            updatePlayPauseUI()
            uiHandler.post(progressTicker)
        } catch (_: Exception) {
            releaseAudio()
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            val first = currentUnitSections().firstOrNull { it.audioPath != null }
            if (first != null) playSection(first)
            return
        }
        if (mp.isPlaying) {
            mp.pause()
            uiHandler.removeCallbacks(progressTicker)
        } else {
            mp.start()
            uiHandler.post(progressTicker)
        }
        updatePlayPauseUI()
    }

    private fun seekRelative(deltaMs: Int) {
        val mp = mediaPlayer ?: return
        val target = (mp.currentPosition + deltaMs).coerceIn(0, mp.duration.coerceAtLeast(1))
        mp.seekTo(target)
        binding.audioSeekBar.progress = target
        binding.tvPosTime.text = formatTime(target)
    }

    private fun jumpSection(delta: Int) {
        val secs = currentUnitSections().filter { it.audioPath != null }
        if (secs.isEmpty()) return
        val idx = secs.indexOfFirst { it.label == activeSectionLabel }
        val next = if (idx < 0) 0 else (idx + delta).coerceIn(0, secs.lastIndex)
        if (idx >= 0 && next == idx && delta > 0) return
        playSection(secs[next])
    }

    private fun cycleSpeed() {
        val idx = speedSteps.indexOfFirst { it == store.playbackRate }
        val next = speedSteps[(idx + 1) % speedSteps.size]
        store.playbackRate = next
        binding.btnSpeed.text = "${next.toString().removeSuffix(".0")}x"
        mediaPlayer?.let { applySpeed(it, next) }
    }

    private fun applySpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val wasPlaying = mp.isPlaying
                mp.playbackParams = PlaybackParams().setSpeed(speed)
                if (!wasPlaying && mp.isPlaying) mp.pause()
            } catch (_: Exception) {
            }
        }
    }

    private fun toggleRepeat() {
        isLooping = !isLooping
        mediaPlayer?.isLooping = isLooping
        binding.btnRepeat.setBackgroundResource(if (isLooping) R.drawable.bg_pill_primary else R.drawable.bg_sharp_btn)
        binding.btnRepeat.setTextColor(if (isLooping) Color.WHITE else Color.parseColor("#64748B"))
    }

    private fun updatePlayPauseUI() {
        binding.btnPlayPause.text = if (mediaPlayer?.isPlaying == true) "❚❚" else "▶"
    }

    private fun releaseAudio() {
        uiHandler.removeCallbacks(progressTicker)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        activeSectionLabel = null
        activeAudioKey = null
        loopStartMs = null
        loopEndMs = null
        binding.audioSeekBar.progress = 0
        binding.tvPosTime.text = "0:00"
        binding.tvDurTime.text = "0:00"
        binding.pageView.setActiveSection(null)
        updatePlayPauseUI()
    }

    private fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
