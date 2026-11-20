package ir.hiwa.grammarinuse

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ir.hiwa.grammarinuse.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: BookRepository
    private lateinit var store: ProgressStore
    private lateinit var packManager: PackManager

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var pageIndex = 0
    private var isFullscreen = false

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

        repo = BookRepository(this)
        store = ProgressStore(this)
        packManager = PackManager(this)

        pageIndex = store.lastPageIndex.coerceIn(0, (repo.pages.size - 1).coerceAtLeast(0))

        setupListeners()
        applyWarmModeUI()
        renderCurrentPage()
    }

    override fun onPause() {
        super.onPause()
        // Per user preference: stop/pause audio whenever screen turns off or app goes background
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
    }

    private fun currentPage(): BookPage = repo.pages[pageIndex.coerceIn(0, repo.pages.lastIndex)]

    private fun currentUnitSections(): List<SectionHotspot> {
        val p = currentPage()
        if (p.unitNumber <= 0) return emptyList()
        if (p.kind == "lesson") return p.sections
        return repo.pages.firstOrNull { it.unitNumber == p.unitNumber && it.kind == "lesson" }?.sections ?: emptyList()
    }

    private fun setupListeners() {
        binding.btnMenu.setOnClickListener { openChecklist() }
        binding.titleBlock.setOnClickListener { openChecklist() }

        binding.btnQuiz.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber <= 0) return@setOnClickListener
            val unitMcqs = repo.mcqs.filter { p.unitNumber in it.unitNumbers }
            val unitWrittens = repo.writtens.filter { p.unitNumber in it.unitNumbers }
            QuizDialog.show(this, p.unitNumber, p.unitTitle, unitMcqs, unitWrittens) { c, t ->
                store.setQuizScore(p.unitNumber, c, t)
            }
        }

        binding.btnAnswerKey.setOnClickListener {
            val p = currentPage()
            val summary = repo.units.firstOrNull { it.unitNumber == p.unitNumber }
            Dialogs.showAnswerKey(this, p.unitNumber, summary?.answerKey ?: "")
        }

        binding.btnBookmark.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber > 0) {
                store.toggleBookmark(p.unitNumber)
                updateHeaderChecksUI()
            }
        }

        binding.btnNote.setOnClickListener {
            val p = currentPage()
            if (p.unitNumber > 0) {
                Dialogs.showUnitNote(this, p.unitNumber, store) { updateHeaderChecksUI() }
            }
        }

        binding.btnWarm.setOnClickListener {
            store.warmMode = !store.warmMode
            applyWarmModeUI()
        }

        binding.btnFullscreen.setOnClickListener { toggleFullscreen(true) }
        binding.btnExitFullscreen.setOnClickListener { toggleFullscreen(false) }

        binding.btnPacks.setOnClickListener {
            Dialogs.showPackManager(this, packManager) { renderCurrentPage() }
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

    private fun openChecklist() {
        Dialogs.showChecklist(this, repo, store, pageIndex, { idx -> goToPage(idx) }, { updateHeaderChecksUI() })
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
    }

    private fun applyWarmModeUI() {
        val warm = store.warmMode
        binding.pageView.setWarmMode(warm)
        binding.pageViewport.setBackgroundColor(Color.parseColor(if (warm) "#E8DEC8" else "#E2E8F0"))
        binding.btnWarm.setTextColor(Color.parseColor(if (warm) "#B45309" else "#64748B"))
    }

    private fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, repo.pages.lastIndex)
        if (clamped == pageIndex) return
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
        val kindFa = when (p.kind) {
            "lesson" -> "آموزش"
            "exercise" -> "تمرین"
            "appendix" -> "ضمیمه"
            "additional" -> "تمرین اضافه"
            else -> "Study Guide"
        }
        binding.tvHeaderTitle.text = if (isUnit) "یونیت ${p.unitNumber} · ص ${p.bookPage} ($kindFa)" else "ص ${p.bookPage} ($kindFa)"
        binding.tvHeaderSubtitle.text = p.unitTitle

        binding.btnQuiz.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnAnswerKey.visibility = if (p.kind == "exercise") View.VISIBLE else View.GONE
        binding.btnBookmark.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnNote.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnCheckPage.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.btnCheckUnit.visibility = if (isUnit) View.VISIBLE else View.GONE
        binding.audioControlsBox.visibility = if (isUnit) View.VISIBLE else View.GONE

        binding.btnPrevPage.alpha = if (pageIndex > 0) 1f else 0.35f
        binding.btnNextPage.alpha = if (pageIndex < repo.pages.lastIndex) 1f else 0.35f
        binding.btnSpeed.text = "${store.playbackRate.toString().removeSuffix(".0")}x"

        updateHeaderChecksUI()
        rebuildSectionPills()

        // Load page image from its 30-unit ZIP pack (downloads pack ONCE on demand!)
        val existingFile = packManager.getFile(p.imagePath)
        if (existingFile != null) {
            displayPageBitmap(existingFile, p)
        } else {
            val pack = ArvanConfig.packForPage(p)
            binding.loadingOverlay.visibility = View.VISIBLE
            binding.packSpinner.visibility = View.VISIBLE
            binding.btnRetryDownload.visibility = View.GONE
            binding.tvLoadingStatus.text = "در حال دریافت ${pack.title} از ابر آروان (فقط یک‌بار)..."

            ioExecutor.execute {
                val ok = packManager.downloadAndExtract(pack) { pct, ext ->
                    uiHandler.post {
                        binding.tvLoadingStatus.text =
                            if (ext) "در حال استخراج ${pack.title} روی حافظه گوشی..."
                            else "دریافت ${pack.title}: $pct%"
                    }
                }
                uiHandler.post {
                    if (currentPage().id != p.id) return@post
                    val loaded = if (ok) packManager.getFile(p.imagePath) else null
                    if (loaded != null) {
                        displayPageBitmap(loaded, p)
                    } else {
                        binding.packSpinner.visibility = View.GONE
                        binding.btnRetryDownload.visibility = View.VISIBLE
                        binding.tvLoadingStatus.text = "دریافت ${pack.title} ناموفق بود. اتصال اینترنت را بررسی کنید."
                    }
                }
            }
        }
    }

    private fun displayPageBitmap(file: File, page: BookPage) {
        ioExecutor.execute {
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            uiHandler.post {
                if (currentPage().id != page.id) return@post
                binding.loadingOverlay.visibility = View.GONE
                binding.pageView.setPage(bmp, if (page.kind == "lesson") page.sections else emptyList())
                binding.pageView.setActiveSection(activeSectionLabel)
            }
        }
    }

    private fun updateHeaderChecksUI() {
        val p = currentPage()
        val donePages = store.countCompletedPages(repo.pages)
        val totalUnitPages = (repo.units.size * 2).coerceAtLeast(1)
        binding.bookProgressBar.progress = ((donePages * 100) / totalUnitPages).coerceIn(0, 100)

        if (p.unitNumber <= 0) return
        val pDone = store.isPageDone(p.id)
        val uDone = store.isUnitDone(p.unitNumber)
        val star = store.isBookmarked(p.unitNumber)
        val hasNote = store.getNote(p.unitNumber).isNotBlank()

        binding.btnCheckPage.text = if (pDone) "● ص" else "○ ص"
        binding.btnCheckPage.setTextColor(Color.parseColor(if (pDone) "#15803D" else "#64748B"))
        binding.btnCheckUnit.text = if (uDone) "☑ ی" else "☐ ی"
        binding.btnCheckUnit.setTextColor(Color.parseColor(if (uDone) "#0F766E" else "#64748B"))
        binding.btnBookmark.text = if (star) "★" else "☆"
        binding.btnBookmark.setTextColor(Color.parseColor(if (star) "#F59E0B" else "#64748B"))
        binding.btnNote.setTextColor(Color.parseColor(if (hasNote) "#0F766E" else "#64748B"))
    }

    private fun rebuildSectionPills() {
        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val row = binding.sectionPillsRow
        row.removeAllViews()
        val sections = currentUnitSections()
        if (sections.isEmpty()) return

        for (sec in sections) {
            val isAct = sec.label == activeSectionLabel
            val pill = TextView(this).apply {
                text = if (isAct) "▶ ${sec.label}" else sec.label
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(if (isAct) Color.WHITE else Color.parseColor("#0F172A"))
                setBackgroundResource(if (isAct) R.drawable.bg_pill_primary else R.drawable.bg_pill_neutral)
                setPadding(px(11), 0, px(11), 0)
                setOnClickListener { playSection(sec) }
            }
            row.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(26)).apply {
                marginEnd = px(5)
            })
        }

        // Optional Auto-Next chip (OFF by default)
        val autoChip = TextView(this).apply {
            text = "خودکار"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (store.autoPlayNext) "#0F766E" else "#475569"))
            setBackgroundResource(R.drawable.bg_sharp_btn)
            setPadding(px(8), 0, px(8), 0)
            setOnClickListener {
                store.autoPlayNext = !store.autoPlayNext
                rebuildSectionPills()
            }
        }
        row.addView(autoChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(25)).apply {
            marginEnd = px(5)
        })

        // A-B Shadowing Loop chip
        val abLabel = when {
            loopStartMs == null -> "A-B"
            loopEndMs == null -> "A→؟"
            else -> "A↔B"
        }
        val abChip = TextView(this).apply {
            text = abLabel
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (loopStartMs != null) "#B45309" else "#475569"))
            setBackgroundResource(R.drawable.bg_sharp_btn)
            setPadding(px(8), 0, px(8), 0)
            setOnClickListener { cycleABLoop() }
        }
        row.addView(abChip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(25)))
    }

    private fun playSection(section: SectionHotspot) {
        val p = currentPage()
        val key = "u${p.unitNumber}_${section.label}"
        if (activeAudioKey == key && mediaPlayer != null) {
            togglePlayPause()
            return
        }

        val audioRel = section.audioPath
        if (audioRel.isNullOrBlank()) {
            showAudioStatus("فایل صوتی بخش ${section.label} موجود نیست.")
            return
        }

        val audioFile = packManager.getFile(audioRel)
        if (audioFile == null) {
            showAudioStatus("بسته صوتی این یونیت هنوز دانلود نشده است.")
            return
        }

        releaseAudio()
        loopStartMs = null
        loopEndMs = null
        activeSectionLabel = section.label
        activeAudioKey = key
        binding.pageView.setActiveSection(section.label)
        rebuildSectionPills()
        showAudioStatus(null)

        try {
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                isLooping = this@MainActivity.isLooping
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().setSpeed(store.playbackRate)
                }
                setOnCompletionListener {
                    uiHandler.removeCallbacks(progressTicker)
                    updatePlayPauseUI()
                    if (!isLooping && store.autoPlayNext) {
                        val list = currentUnitSections()
                        val idx = list.indexOfFirst { s -> s.label == activeSectionLabel }
                        if (idx in 0 until list.lastIndex) {
                            playSection(list[idx + 1])
                        }
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
            showAudioStatus("خطا در پخش صوت بخش ${section.label}.")
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            val sections = currentUnitSections()
            if (sections.isNotEmpty()) playSection(sections.first())
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
        val target = (mp.currentPosition + deltaMs).coerceIn(0, mp.duration)
        mp.seekTo(target)
        binding.audioSeekBar.progress = target
        binding.tvPosTime.text = formatTime(target)
    }

    private fun jumpSection(delta: Int) {
        val list = currentUnitSections()
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.label == activeSectionLabel }
        val nextIdx = if (idx < 0) 0 else (idx + delta).coerceIn(0, list.lastIndex)
        playSection(list[nextIdx])
    }

    private fun cycleSpeed() {
        val idx = speedSteps.indexOfFirst { it == store.playbackRate }
        val next = speedSteps[(idx + 1) % speedSteps.size]
        store.playbackRate = next
        binding.btnSpeed.text = "${next.toString().removeSuffix(".0")}x"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let {
                try {
                    it.playbackParams = PlaybackParams().setSpeed(next)
                } catch (_: Exception) {}
            }
        }
    }

    private fun toggleRepeat() {
        isLooping = !isLooping
        mediaPlayer?.isLooping = isLooping
        binding.btnRepeat.setTextColor(Color.parseColor(if (isLooping) "#0F766E" else "#64748B"))
    }

    private fun cycleABLoop() {
        val mp = mediaPlayer ?: return
        val pos = mp.currentPosition
        when {
            loopStartMs == null -> {
                loopStartMs = pos
                loopEndMs = null
                showAudioStatus("نقطه A ثبت شد؛ دوباره روی A-B بزنید تا نقطه B ثبت شود.")
            }
            loopEndMs == null -> {
                if (pos > (loopStartMs ?: 0) + 500) {
                    loopEndMs = pos
                    mp.seekTo(loopStartMs ?: 0)
                    showAudioStatus("تکرار بازه A-B فعال شد")
                } else {
                    loopStartMs = null
                    showAudioStatus(null)
                }
            }
            else -> {
                loopStartMs = null
                loopEndMs = null
                showAudioStatus(null)
            }
        }
        rebuildSectionPills()
    }

    private fun releaseAudio() {
        uiHandler.removeCallbacks(progressTicker)
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        activeAudioKey = null
        activeSectionLabel = null
        loopStartMs = null
        loopEndMs = null
        binding.audioSeekBar.progress = 0
        binding.tvPosTime.text = "0:00"
        binding.tvDurTime.text = "0:00"
        binding.pageView.setActiveSection(null)
        updatePlayPauseUI()
    }

    private fun updatePlayPauseUI() {
        binding.btnPlayPause.text = if (mediaPlayer?.isPlaying == true) "❚❚" else "▶"
    }

    private fun showAudioStatus(msg: String?) {
        if (msg.isNullOrBlank()) {
            binding.tvAudioStatus.visibility = View.GONE
        } else {
            binding.tvAudioStatus.text = msg
            binding.tvAudioStatus.visibility = View.VISIBLE
        }
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    }
}
