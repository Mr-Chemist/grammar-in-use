package ir.hiwa.grammarinuse

import android.app.Dialog
import android.content.Context
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
import java.util.concurrent.Executors

/**
 * Clean Sharp+Soft Dialogs for:
 * - Checklist & Back-Matter Resources (145 Units + Appendices / Additional / Study Guide)
 * - Answer Key Popup
 * - Personal Unit Notes
 * - 30-Unit ArvanCloud Pack Manager
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

    fun showAnswerKey(context: Context, unitNumber: Int, answerKey: String) {
        val dp = context.resources.displayMetrics.density
        val scroll = ScrollView(context)
        val tv = TextView(context).apply {
            text = answerKey.ifBlank { "پاسخ‌نامه این یونیت استخراج نشده است." }
            textSize = 14f
            setTextColor(Color.parseColor("#0F172A"))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(tv)
        AlertDialog.Builder(context)
            .setTitle("پاسخ‌نامه تمرین‌های یونیت $unitNumber")
            .setView(scroll)
            .setPositiveButton("بستن", null)
            .show()
    }

    fun showUnitNote(context: Context, unitNumber: Int, store: ProgressStore, onSaved: () -> Unit) {
        val dp = context.resources.displayMetrics.density
        val edit = EditText(context).apply {
            setText(store.getNote(unitNumber))
            hint = "یادداشت‌ها و نکات کلیدی این یونیت..."
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            fitsSystemWindows = true
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
        }
        AlertDialog.Builder(context)
            .setTitle("یادداشت شخصی یونیت $unitNumber")
            .setView(edit)
            .setPositiveButton("ذخیره") { _, _ ->
                store.setNote(unitNumber, edit.text.toString())
                onSaved()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    fun showPackManager(context: Context, packManager: PackManager, onChanged: () -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            text = "مدیریت بسته‌های ۳۰-یونیتی (ابر آروان)"
            textSize = 15f
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
            tvStats.text = "حجم ذخیره‌شده روی گوشی: $mb مگابایت ($files فایل)"
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
                    text = pack.title
                    textSize = 12.5f
                    setTextColor(Color.parseColor("#0F172A"))
                }
                val btnAction = TextView(context).apply {
                    text = if (installed) "نصب‌شده" else "دانلود"
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
                            btn.text = "۰٪"
                            bgExecutor.execute {
                                packManager.downloadAndExtract(pack) { pct, ext ->
                                    btn.post { btn.text = if (ext) "استخراج..." else "$pct%" }
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
            text = "پاک‌سازی کامل حافظه کش"
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

    fun showChecklist(
        context: Context,
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            fitsSystemWindows = true
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        // Header
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
            text = "فهرست کتاب و چک‌لیست"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(px(10), 0, px(10), 0)
        }
        val tvStreak = TextView(context).apply {
            text = "🔥 ${store.streakCount} روز"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C2410C"))
            background = boxBg(context, "#FFF7ED", "#FDBA74", 999f)
            setPadding(px(9), px(4), px(9), px(4))
        }
        header.addView(btnClose, LinearLayout.LayoutParams(px(34), px(34)))
        header.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(tvStreak)
        root.addView(header)

        // Main Tabs
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }
        val tabUnits = TextView(context).apply {
            text = "۱۴۵ یونیت اصلی"
            gravity = Gravity.CENTER
            setPadding(0, px(10), 0, px(10))
            textSize = 12.5f
        }
        val tabResources = TextView(context).apply {
            text = "ضمائم، تمرین اضافه و Study Guide"
            gravity = Gravity.CENTER
            setPadding(0, px(10), 0, px(10))
            textSize = 12f
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
            hint = "جستجوی یونیت، شماره صفحه (مثلاً 44) یا موضوع..."
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
        val filterSpecs = listOf("all" to "همه", "todo" to "باقی‌مانده", "done" to "تکمیل‌شده", "starred" to "نشان‌دارها")
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
                text = "${res.title}  (${res.pageCount} صفحه)"
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
            statsTv.text = "یونیت‌های تکمیل‌شده: $du از ${repo.units.size}    |    صفحات تکمیل‌شده: $dpDone از ${repo.units.size * 2}"
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
                val scoreSuffix = if (qScore != null && qScore.second > 0) "  ·  آزمون ${qScore.first}/${qScore.second}" else ""
                titleCol.addView(TextView(context).apply {
                    text = "یونیت ${u.unitNumber}$scoreSuffix"
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
                    setPadding(0, px(6), px(26), 0)
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
                pRow.addView(makePageChip(lDone, "آموزش · ص ${u.lessonBookPage}", u.lessonPageId, u.lessonPageIndex), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(6) })
                pRow.addView(makePageChip(eDone, "تمرین · ص ${u.exerciseBookPage}", u.exercisePageId, u.exercisePageIndex), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
