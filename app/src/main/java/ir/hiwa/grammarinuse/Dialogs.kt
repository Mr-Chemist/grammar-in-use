package ir.hiwa.grammarinuse

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/**
 * Clean Sharp+Soft Dialogs (v2):
 * - Exact Cambridge Answer Key Zoomable Image Viewer (with formatted text fallback)
 * - Download Manager (clean naming, bilingual EN/FA)
 * - Checklist & Back-Matter Resources (145 Units + Appendices / Additional / Study Guide, no streak badge)
 * - Settings Dialog (English / Persian toggle, Eye-Comfort mode, Quick Guide replay, Download Manager)
 * - Minimal 4-step First-Launch Onboarding Tour (with Skip / Next)
 * - Sticky Text Note Editor (Add / Edit / Delete)
 */
object Dialogs {
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private fun boxBg(context: Context, fill: String, stroke: String, radiusDp: Float = 3f): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke((1 * dp).toInt(), Color.parseColor(stroke))
            cornerRadius = radiusDp * dp
        }
    }

    fun showAnswerKey(
        context: Context,
        lang: AppLang,
        unitNumber: Int,
        keyImageFile: File?,
        fallbackText: String
    ) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val bmp = if (keyImageFile != null && keyImageFile.exists()) {
            try {
                BitmapFactory.decodeFile(keyImageFile.absolutePath)
            } catch (_: Exception) {
                null
            }
        } else null

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = lang.layoutDir
            fitsSystemWindows = true
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(px(12), px(10), px(12), px(10))
        }
        val btnClose = TextView(context).apply {
            text = "✕"
            textSize = 15f
            gravity = Gravity.CENTER
            background = boxBg(context, "#F1F5F9", "#CBD5E1")
            setOnClickListener { dialog.dismiss() }
        }
        val title = TextView(context).apply {
            text = Strings.answerKeyTitle(lang, unitNumber)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(px(10), 0, px(10), 0)
        }
        header.addView(btnClose, LinearLayout.LayoutParams(px(34), px(34)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        val divider = View(context).apply { setBackgroundColor(Color.parseColor("#CBD5E1")) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1)))

        if (bmp != null) {
            val zoomView = ZoomablePageView(context).apply {
                setBackgroundColor(Color.WHITE)
                setPage(bmp, emptyList())
            }
            root.addView(zoomView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            val scroll = ScrollView(context).apply { setBackgroundColor(Color.WHITE) }
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setPadding(px(16), px(14), px(16), px(20))
            }
            container.addView(TextView(context).apply {
                text = "UNIT $unitNumber"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F766E"))
                setPadding(0, 0, 0, px(8))
            })
            val body = fallbackText.ifBlank { Strings.answerKeyMissing(lang) }
            for (rawLine in body.lines()) {
                val ln = rawLine.trim()
                if (ln.isEmpty()) continue
                val isExHeader = Regex("^$unitNumber\\.\\d+\\b").containsMatchIn(ln)
                container.addView(TextView(context).apply {
                    text = ln
                    textSize = if (isExHeader) 14.5f else 13.5f
                    setTypeface(null, if (isExHeader) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(Color.parseColor(if (isExHeader) "#0F172A" else "#1E293B"))
                    setPadding(if (isExHeader) 0 else px(10), if (isExHeader) px(10) else px(3), 0, px(3))
                })
            }
            scroll.addView(container)
            root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        dialog.setContentView(root)
        dialog.show()
    }

    fun showTextNoteEditor(
        context: Context,
        lang: AppLang,
        initialText: String,
        isEdit: Boolean,
        onSave: (String) -> Unit,
        onDelete: (() -> Unit)? = null
    ) {
        val dp = context.resources.displayMetrics.density
        val edit = EditText(context).apply {
            setText(initialText)
            hint = Strings.addNoteHint(lang)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            layoutDirection = lang.layoutDir
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(Strings.addNoteTitle(lang))
            .setView(edit)
            .setPositiveButton(Strings.save(lang)) { _, _ ->
                val txt = edit.text.toString().trim()
                if (txt.isNotEmpty()) onSave(txt) else onDelete?.invoke()
            }
            .setNegativeButton(Strings.cancel(lang), null)
        if (isEdit && onDelete != null) {
            builder.setNeutralButton(Strings.delete(lang)) { _, _ -> onDelete() }
        }
        builder.show()
    }

    fun showDownloadManager(context: Context, lang: AppLang, packManager: PackManager, onChanged: () -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = lang.layoutDir
            fitsSystemWindows = true
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            setPadding(px(14), px(14), px(14), px(14))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnClose = TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            background = boxBg(context, "#F1F5F9", "#CBD5E1")
            setOnClickListener { dialog.dismiss() }
        }
        val title = TextView(context).apply {
            text = Strings.downloadManagerTitle(lang)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(px(10), 0, px(10), 0)
        }
        header.addView(btnClose, LinearLayout.LayoutParams(px(34), px(34)))
        header.addView(title)
        root.addView(header)

        val tvStats = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, px(10), 0, px(10))
        }
        root.addView(tvStats)

        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(listContainer) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        lateinit var refresh: () -> Unit
        refresh = {
            val (mb, files) = packManager.getStorageStats()
            tvStats.text = Strings.storageUsed(lang, mb, files)
            listContainer.removeAllViews()

            for (pack in ArvanConfig.PACKS) {
                val installed = packManager.isInstalled(pack.id)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = boxBg(context, "#FFFFFF", "#CBD5E1")
                    setPadding(px(12), px(11), px(12), px(11))
                }
                val tvName = TextView(context).apply {
                    text = pack.title(lang)
                    textSize = 13f
                    setTextColor(Color.parseColor("#0F172A"))
                }
                val btnAction = TextView(context).apply {
                    text = if (installed) Strings.installed(lang) else Strings.download(lang)
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(if (installed) "#15803D" else "#FFFFFF"))
                    background = boxBg(
                        context,
                        if (installed) "#DCFCE7" else "#0F766E",
                        if (installed) "#86EFAC" else "#0F766E",
                        999f
                    )
                    setPadding(px(12), px(6), px(12), px(6))
                    if (!installed) {
                        setOnClickListener { v ->
                            val btn = v as TextView
                            btn.isEnabled = false
                            btn.text = "0%"
                            bgExecutor.execute {
                                packManager.downloadAndExtract(pack) { pct, ext ->
                                    btn.post { btn.text = if (ext) Strings.extracting(lang) else "$pct%" }
                                }
                                btn.post {
                                    refresh()
                                    onChanged()
                                }
                            }
                        }
                    }
                }
                row.addView(tvName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(btnAction)
                listContainer.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = px(8)
                })
            }
        }

        val btnClear = TextView(context).apply {
            text = Strings.clearCache(lang)
            gravity = Gravity.CENTER
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#DC2626"))
            background = boxBg(context, "#FEF2F2", "#FECACA")
            setPadding(0, px(11), 0, px(11))
            setOnClickListener {
                packManager.clearAll()
                refresh()
                onChanged()
            }
        }
        root.addView(btnClear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(10)
        })

        refresh()
        dialog.setContentView(root)
        dialog.show()
    }

    fun showSettings(
        context: Context,
        store: ProgressStore,
        onLanguageChanged: (AppLang) -> Unit,
        onWarmToggled: (Boolean) -> Unit,
        onReplayTour: () -> Unit,
        onOpenDownloads: () -> Unit
    ) {
        val lang = store.language
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = lang.layoutDir
            background = boxBg(context, "#FFFFFF", "#CBD5E1", 6f)
            setPadding(px(18), px(16), px(18), px(16))
        }

        val hdr = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        hdr.addView(TextView(context).apply {
            text = Strings.settingsTitle(lang)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hdr.addView(TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            background = boxBg(context, "#F1F5F9", "#CBD5E1")
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(px(30), px(30)))
        root.addView(hdr)

        // Language selector
        root.addView(TextView(context).apply {
            text = Strings.languageLabel(lang)
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, px(14), 0, px(6))
        })
        val langRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val btnEn = TextView(context).apply {
            text = "English"
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            val sel = lang == AppLang.EN
            background = boxBg(context, if (sel) "#0F766E" else "#F8FAFC", if (sel) "#0F766E" else "#CBD5E1", 999f)
            setTextColor(Color.parseColor(if (sel) "#FFFFFF" else "#334155"))
            setPadding(0, px(9), 0, px(9))
            setOnClickListener {
                if (store.language != AppLang.EN) {
                    store.language = AppLang.EN
                    onLanguageChanged(AppLang.EN)
                }
                dialog.dismiss()
            }
        }
        val btnFa = TextView(context).apply {
            text = "فارسی"
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            val sel = lang == AppLang.FA
            background = boxBg(context, if (sel) "#0F766E" else "#F8FAFC", if (sel) "#0F766E" else "#CBD5E1", 999f)
            setTextColor(Color.parseColor(if (sel) "#FFFFFF" else "#334155"))
            setPadding(0, px(9), 0, px(9))
            setOnClickListener {
                if (store.language != AppLang.FA) {
                    store.language = AppLang.FA
                    onLanguageChanged(AppLang.FA)
                }
                dialog.dismiss()
            }
        }
        langRow.addView(btnEn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8) })
        langRow.addView(btnFa, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(langRow)

        // Action rows
        fun makeActionRow(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#0F172A"))
            background = boxBg(context, "#F8FAFC", "#CBD5E1", 4f)
            setPadding(px(12), px(11), px(12), px(11))
            setOnClickListener {
                dialog.dismiss()
                onClick()
            }
        }

        val warmState = if (store.warmMode) "  ✓" else ""
        root.addView(makeActionRow(Strings.warmModeLabel(lang) + warmState) {
            store.warmMode = !store.warmMode
            onWarmToggled(store.warmMode)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) })

        root.addView(makeActionRow(Strings.openDownloads(lang)) {
            onOpenDownloads()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8) })

        root.addView(makeActionRow(Strings.replayTour(lang)) {
            onReplayTour()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8) })

        dialog.setContentView(root)
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showOnboarding(context: Context, lang: AppLang, onDone: () -> Unit) {
        val steps = Strings.onboardingSteps(lang)
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        var idx = 0
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar).apply {
            setCancelable(false)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = lang.layoutDir
            gravity = Gravity.CENTER_HORIZONTAL
            background = boxBg(context, "#FFFFFF", "#CBD5E1", 8f)
            setPadding(px(22), px(22), px(22), px(18))
        }

        val tvIcon = TextView(context).apply {
            textSize = 36f
            gravity = Gravity.CENTER
            background = boxBg(context, "#F0FDFA", "#99F6E4", 999f)
        }
        val tvTitle = TextView(context).apply {
            textSize = 16.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            setPadding(0, px(14), 0, px(8))
        }
        val tvBody = TextView(context).apply {
            textSize = 13.5f
            setLineSpacing(0f, 1.3f)
            setTextColor(Color.parseColor("#475569"))
            gravity = Gravity.CENTER
            setPadding(px(4), 0, px(4), px(16))
        }
        val dotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, px(16))
        }
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val btnSkip = TextView(context).apply {
            text = Strings.skip(lang)
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(px(12), px(10), px(12), px(10))
            setOnClickListener {
                dialog.dismiss()
                onDone()
            }
        }
        val btnNext = TextView(context).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = boxBg(context, "#0F766E", "#0F766E", 999f)
            setPadding(px(20), px(10), px(20), px(10))
        }

        btnRow.addView(btnSkip)
        btnRow.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        btnRow.addView(btnNext)

        root.addView(tvIcon, LinearLayout.LayoutParams(px(68), px(68)))
        root.addView(tvTitle)
        root.addView(tvBody)
        root.addView(dotsRow)
        root.addView(btnRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun renderStep() {
            val (icon, title, body) = steps[idx]
            tvIcon.text = icon
            tvTitle.text = title
            tvBody.text = body
            btnNext.text = if (idx == steps.lastIndex) Strings.getStarted(lang) else Strings.nextQ(lang)
            btnSkip.visibility = if (idx == steps.lastIndex) View.INVISIBLE else View.VISIBLE

            dotsRow.removeAllViews()
            for (i in steps.indices) {
                val dot = View(context).apply {
                    background = boxBg(context, if (i == idx) "#0F766E" else "#CBD5E1", if (i == idx) "#0F766E" else "#CBD5E1", 999f)
                }
                dotsRow.addView(dot, LinearLayout.LayoutParams(if (i == idx) px(18) else px(7), px(7)).apply {
                    marginStart = px(3)
                    marginEnd = px(3)
                })
            }
        }

        btnNext.setOnClickListener {
            if (idx < steps.lastIndex) {
                idx++
                renderStep()
            } else {
                dialog.dismiss()
                onDone()
            }
        }

        renderStep()
        dialog.setContentView(root)
        dialog.window?.setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun showChecklist(
        context: Context,
        lang: AppLang,
        repo: BookRepository,
        store: ProgressStore,
        currentPageIndex: Int,
        onJump: (Int) -> Unit,
        onStateChanged: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        var activeTab = "units"
        var filter = "all"
        var searchQuery = ""

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = lang.layoutDir
            fitsSystemWindows = true
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        // Clean Header (NO fire/streak icon)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(px(12), px(10), px(12), px(10))
        }
        val btnClose = TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            background = boxBg(context, "#F1F5F9", "#CBD5E1")
            setOnClickListener { dialog.dismiss() }
        }
        val tvTitle = TextView(context).apply {
            text = Strings.menuTitle(lang)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(px(10), 0, px(10), 0)
        }
        header.addView(btnClose, LinearLayout.LayoutParams(px(34), px(34)))
        header.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        // Main Tabs
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }
        val tabUnits = TextView(context).apply {
            text = Strings.tabUnits(lang)
            gravity = Gravity.CENTER
            setPadding(0, px(10), 0, px(10))
            textSize = 12.5f
        }
        val tabResources = TextView(context).apply {
            text = Strings.tabResources(lang)
            gravity = Gravity.CENTER
            setPadding(0, px(10), 0, px(10))
            textSize = 12.5f
        }
        tabsRow.addView(tabUnits, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabsRow.addView(tabResources, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabsRow)

        val statsTv = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#0F172A"))
            background = boxBg(context, "#FFFFFF", "#CBD5E1")
            setPadding(px(12), px(9), px(12), px(9))
        }
        root.addView(statsTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(12), px(8), px(12), px(4))
        })

        val searchBox = EditText(context).apply {
            hint = Strings.searchHint(lang)
            textSize = 13f
            background = boxBg(context, "#FFFFFF", "#94A3B8")
            setPadding(px(12), px(8), px(12), px(8))
        }
        root.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(12), px(4), px(12), px(6))
        })

        val filterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(12), 0, px(12), px(6))
        }
        val filterSpecs = listOf(
            "all" to Strings.filterAll(lang),
            "todo" to Strings.filterTodo(lang),
            "done" to Strings.filterDone(lang),
            "starred" to Strings.filterStarred(lang)
        )
        val filterBtns = mutableMapOf<String, TextView>()
        for ((k, lbl) in filterSpecs) {
            val b = TextView(context).apply {
                text = lbl
                textSize = 11.5f
                setPadding(px(11), px(5), px(11), px(5))
            }
            filterBtns[k] = b
            filterRow.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = px(6)
            })
        }
        root.addView(filterRow)

        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(px(12), px(4), px(12), px(16))
            clipToPadding = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val resScroll = ScrollView(context).apply { visibility = View.GONE }
        val resBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(10), px(12), px(16))
        }
        for (res in repo.resources) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg(context, "#FFFFFF", "#0F766E")
                setPadding(px(14), px(12), px(14), px(12))
                setOnClickListener {
                    onJump(res.startPageIndex)
                    dialog.dismiss()
                }
            }
            card.addView(TextView(context).apply {
                text = "${res.title}  (${Strings.pagesCount(lang, res.pageCount)})"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
            })
            card.addView(TextView(context).apply {
                text = res.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, px(4), 0, 0)
            })
            resBox.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = px(10)
            })
        }
        resScroll.addView(resBox)
        root.addView(resScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun updateStats() {
            val du = store.countCompletedUnits(repo.units)
            val dpDone = store.countCompletedPages(repo.pages)
            statsTv.text = Strings.statsLine(lang, du, repo.units.size, dpDone, repo.units.size * 2)
        }

        val displayed = mutableListOf<UnitSummary>()

        class UnitAdapter : RecyclerView.Adapter<UnitAdapter.VH>() {
            inner class VH(val card: LinearLayout) : RecyclerView.ViewHolder(card)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = px(8)
                    }
                    setPadding(px(10), px(9), px(10), px(9))
                }
                return VH(card)
            }

            override fun getItemCount() = displayed.size

            override fun onBindViewHolder(holder: VH, position: Int) {
                val u = displayed[position]
                val uDone = store.isUnitDone(u.unitNumber)
                val lDone = store.isPageDone(u.lessonPageId)
                val eDone = store.isPageDone(u.exercisePageId)
                val star = store.isBookmarked(u.unitNumber)
                val isCurr = currentPageIndex == u.lessonPageIndex || currentPageIndex == u.exercisePageIndex

                holder.card.removeAllViews()
                holder.card.background = boxBg(context, if (isCurr) "#F0FDFA" else "#FFFFFF", if (isCurr) "#0F766E" else "#CBD5E1")

                val top = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val cbUnit = TextView(context).apply {
                    text = if (uDone) "☑" else "☐"
                    textSize = 18f
                    setTextColor(Color.parseColor(if (uDone) "#0F766E" else "#94A3B8"))
                    setPadding(px(4), 0, px(6), 0)
                    setOnClickListener {
                        store.setUnitDone(u.unitNumber, !uDone)
                        updateStats()
                        onStateChanged()
                        notifyItemChanged(holder.bindingAdapterPosition)
                    }
                }
                val titleCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setOnClickListener { onJump(u.lessonPageIndex); dialog.dismiss() }
                }
                val qScore = store.getQuizScore(u.unitNumber)
                val scoreSuffix = if (qScore != null && qScore.second > 0) "  ·  ${Strings.quizBtn(lang)} ${qScore.first}/${qScore.second}" else ""
                titleCol.addView(TextView(context).apply {
                    text = "${Strings.unitLabel(lang, u.unitNumber)}$scoreSuffix"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F766E"))
                })
                titleCol.addView(TextView(context).apply {
                    text = u.title
                    textSize = 13f
                    setTextColor(Color.parseColor("#0F172A"))
                })
                val btnStar = TextView(context).apply {
                    text = if (star) "★" else "☆"
                    textSize = 18f
                    setTextColor(Color.parseColor(if (star) "#F59E0B" else "#94A3B8"))
                    setPadding(px(6), 0, px(4), 0)
                    setOnClickListener {
                        store.toggleBookmark(u.unitNumber)
                        onStateChanged()
                        notifyItemChanged(holder.bindingAdapterPosition)
                    }
                }
                top.addView(cbUnit)
                top.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                top.addView(btnStar)
                holder.card.addView(top)

                val pRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(px(26), px(6), 0, 0)
                }
                fun makePageChip(done: Boolean, label: String, pageId: String, pageIdx: Int): LinearLayout {
                    val chip = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = boxBg(context, if (currentPageIndex == pageIdx) "#CCFBF1" else "#F8FAFC", "#CBD5E1")
                        setPadding(px(8), px(5), px(8), px(5))
                    }
                    val tick = TextView(context).apply {
                        text = if (done) "●" else "○"
                        textSize = 14f
                        setTextColor(Color.parseColor(if (done) "#16A34A" else "#94A3B8"))
                        setPadding(0, 0, px(6), 0)
                        setOnClickListener {
                            val next = !done
                            store.setPageDone(pageId, next)
                            if (store.isPageDone(u.lessonPageId) && store.isPageDone(u.exercisePageId)) {
                                store.setUnitDone(u.unitNumber, true)
                            }
                            updateStats()
                            onStateChanged()
                            notifyItemChanged(holder.bindingAdapterPosition)
                        }
                    }
                    val lbl = TextView(context).apply {
                        text = label
                        textSize = 11.5f
                        setTextColor(Color.parseColor("#334155"))
                        setOnClickListener { onJump(pageIdx); dialog.dismiss() }
                    }
                    chip.addView(tick)
                    chip.addView(lbl, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    return chip
                }
                pRow.addView(makePageChip(lDone, Strings.lessonChip(lang, u.lessonBookPage), u.lessonPageId, u.lessonPageIndex), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(6) })
                pRow.addView(makePageChip(eDone, Strings.exerciseChip(lang, u.exerciseBookPage), u.exercisePageId, u.exercisePageIndex), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                holder.card.addView(pRow)
            }
        }

        val adapter = UnitAdapter()
        recycler.adapter = adapter

        fun refreshList() {
            updateStats()
            val q = searchQuery.trim().lowercase()
            displayed.clear()
            for (u in repo.units) {
                val uDone = store.isUnitDone(u.unitNumber)
                val star = store.isBookmarked(u.unitNumber)
                if (filter == "done" && !uDone) continue
                if (filter == "todo" && uDone) continue
                if (filter == "starred" && !star) continue
                if (q.isNotEmpty() && !u.unitNumber.toString().contains(q) &&
                    !u.lessonBookPage.toString().contains(q) &&
                    !u.exerciseBookPage.toString().contains(q) &&
                    !u.title.lowercase().contains(q)
                ) continue
                displayed.add(u)
            }
            adapter.notifyDataSetChanged()

            for ((k, b) in filterBtns) {
                val sel = k == filter
                b.background = boxBg(context, if (sel) "#0F766E" else "#E2E8F0", if (sel) "#0F766E" else "#E2E8F0", 999f)
                b.setTextColor(Color.parseColor(if (sel) "#FFFFFF" else "#334155"))
            }
            val inUnits = activeTab == "units"
            tabUnits.setBackgroundColor(Color.parseColor(if (inUnits) "#F0FDFA" else "#FFFFFF"))
            tabUnits.setTextColor(Color.parseColor(if (inUnits) "#0F766E" else "#64748B"))
            tabResources.setBackgroundColor(Color.parseColor(if (!inUnits) "#F0FDFA" else "#FFFFFF"))
            tabResources.setTextColor(Color.parseColor(if (!inUnits) "#0F766E" else "#64748B"))

            statsTv.visibility = if (inUnits) View.VISIBLE else View.GONE
            searchBox.visibility = if (inUnits) View.VISIBLE else View.GONE
            filterRow.visibility = if (inUnits) View.VISIBLE else View.GONE
            recycler.visibility = if (inUnits) View.VISIBLE else View.GONE
            resScroll.visibility = if (inUnits) View.GONE else View.VISIBLE
        }

        tabUnits.setOnClickListener { activeTab = "units"; refreshList() }
        tabResources.setOnClickListener { activeTab = "resources"; refreshList() }
        for ((k, b) in filterBtns) b.setOnClickListener { filter = k; refreshList() }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                refreshList()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshList()
        dialog.setContentView(root)
        dialog.show()
    }
}
