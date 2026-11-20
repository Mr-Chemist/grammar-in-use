package ir.hiwa.grammarinuse

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Full Interactive Quiz Dialog (MCQ + Smart-Graded Written) per Unit.
 */
object QuizDialog {

    private val contractions = listOf(
        Regex("\\bi'm\\b") to "i am",
        Regex("\\byou're\\b") to "you are",
        Regex("\\bhe's\\b") to "he is",
        Regex("\\bshe's\\b") to "she is",
        Regex("\\bit's\\b") to "it is",
        Regex("\\bwe're\\b") to "we are",
        Regex("\\bthey're\\b") to "they are",
        Regex("\\bdon't\\b") to "do not",
        Regex("\\bdoesn't\\b") to "does not",
        Regex("\\bdidn't\\b") to "did not",
        Regex("\\bisn't\\b") to "is not",
        Regex("\\baren't\\b") to "are not",
        Regex("\\bwasn't\\b") to "was not",
        Regex("\\bweren't\\b") to "were not",
        Regex("\\bhaven't\\b") to "have not",
        Regex("\\bhasn't\\b") to "has not",
        Regex("\\bhadn't\\b") to "had not",
        Regex("\\bwon't\\b") to "will not",
        Regex("\\bcan't\\b") to "cannot",
        Regex("\\bcouldn't\\b") to "could not",
        Regex("\\bwouldn't\\b") to "would not",
        Regex("\\bshouldn't\\b") to "should not"
    )

    private fun normalize(raw: String): String {
        var s = raw.lowercase()
            .replace(Regex("[’‘`]"), "'")
            .replace(Regex("[.,!?;:\"()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        for ((rx, rep) in contractions) s = s.replace(rx, rep)
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun isCorrectWritten(input: String, accepted: List<String>): Boolean {
        val u = normalize(input)
        if (u.isEmpty()) return false
        return accepted.any { normalize(it) == u }
    }

    fun show(
        context: Context,
        unitNumber: Int,
        unitTitle: String,
        mcqs: List<McqQuestion>,
        writtens: List<WrittenQuestion>,
        onScoreUpdate: (correct: Int, total: Int) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        fun boxBg(fill: String, stroke: String, radiusDp: Float = 3f) = GradientDrawable().apply {
            setColor(Color.parseColor(fill))
            setStroke(px(1), Color.parseColor(stroke))
            cornerRadius = radiusDp * dp
        }

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        var activeTab = if (mcqs.isNotEmpty()) "mcq" else "written"
        var mcqIdx = 0
        var writtenIdx = 0

        val mcqSelected = mutableMapOf<String, MutableSet<String>>()
        val mcqChecked = mutableSetOf<String>()
        val writtenInputs = mutableMapOf<String, String>()
        val writtenChecked = mutableSetOf<String>()
        val writtenRevealed = mutableSetOf<String>()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            textSize = 15f
            gravity = Gravity.CENTER
            background = boxBg("#F1F5F9", "#CBD5E1")
            setOnClickListener { dialog.dismiss() }
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(10), 0, px(10), 0)
        }
        titleCol.addView(TextView(context).apply {
            text = "آزمون تعاملی یونیت $unitNumber"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
        })
        titleCol.addView(TextView(context).apply {
            text = unitTitle
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
        })
        val tvScore = TextView(context).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(px(10), px(5), px(10), px(5))
            background = boxBg("#0F766E", "#0F766E", 999f)
        }
        header.addView(btnClose, LinearLayout.LayoutParams(px(34), px(34)))
        header.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(tvScore)
        root.addView(header)

        // Tabs
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
        }
        val tabMcq = TextView(context).apply {
            text = "تستی (${mcqs.size})"
            gravity = Gravity.CENTER
            setPadding(0, px(11), 0, px(11))
            textSize = 13f
        }
        val tabWritten = TextView(context).apply {
            text = "تشریحی / تایپی (${writtens.size})"
            gravity = Gravity.CENTER
            setPadding(0, px(11), 0, px(11))
            textSize = 13f
        }
        tabsRow.addView(tabMcq, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tabsRow.addView(tabWritten, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabsRow)

        val scroll = ScrollView(context)
        val contentBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(14), px(14), px(20))
        }
        scroll.addView(contentBox)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun computeScore(): Pair<Int, Int> {
            var c = 0
            for (q in mcqs) {
                if (q.id in mcqChecked) {
                    val sel = mcqSelected[q.id]?.sorted() ?: emptyList()
                    if (sel == q.correctKeys.sorted()) c++
                }
            }
            for (q in writtens) {
                if (q.id in writtenChecked && isCorrectWritten(writtenInputs[q.id] ?: "", q.acceptedAnswers)) {
                    c++
                }
            }
            return Pair(c, mcqs.size + writtens.size)
        }

        lateinit var render: () -> Unit
        render = {
            val (c, t) = computeScore()
            tvScore.text = "$c/$t"

            tabMcq.setBackgroundColor(if (activeTab == "mcq") Color.parseColor("#F0FDFA") else Color.WHITE)
            tabMcq.setTextColor(Color.parseColor(if (activeTab == "mcq") "#0F766E" else "#64748B"))
            tabMcq.setTypeface(null, if (activeTab == "mcq") Typeface.BOLD else Typeface.NORMAL)

            tabWritten.setBackgroundColor(if (activeTab == "written") Color.parseColor("#F0FDFA") else Color.WHITE)
            tabWritten.setTextColor(Color.parseColor(if (activeTab == "written") "#0F766E" else "#64748B"))
            tabWritten.setTypeface(null, if (activeTab == "written") Typeface.BOLD else Typeface.NORMAL)

            contentBox.removeAllViews()
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = boxBg("#FFFFFF", "#CBD5E1")
                setPadding(px(14), px(14), px(14), px(14))
            }

            if (activeTab == "mcq") {
                val q = mcqs.getOrNull(mcqIdx)
                if (q == null) {
                    card.addView(TextView(context).apply {
                        text = "سوال تستی اختصاصی از Study Guide برای این یونیت یافت نشد؛ تب «تشریحی» را امتحان کنید."
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 13f
                    })
                } else {
                    card.addView(TextView(context).apply {
                        text = "سوال ${mcqIdx + 1} از ${mcqs.size}" + if (q.correctKeys.size > 1) "  (چندگزینه‌ای)" else ""
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F766E"))
                    })
                    card.addView(TextView(context).apply {
                        text = q.prompt
                        textSize = 15f
                        setTextColor(Color.parseColor("#0F172A"))
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        setPadding(0, px(10), 0, px(12))
                    })

                    val checked = q.id in mcqChecked
                    val chosen = mcqSelected.getOrPut(q.id) { mutableSetOf() }

                    for (opt in q.options) {
                        val isSel = opt.key in chosen
                        val isRight = opt.key in q.correctKeys
                        val fill = when {
                            checked && isRight -> "#DCFCE7"
                            checked && isSel && !isRight -> "#FEE2E2"
                            isSel -> "#F0FDFA"
                            else -> "#F8FAFC"
                        }
                        val stroke = when {
                            checked && isRight -> "#16A34A"
                            checked && isSel && !isRight -> "#DC2626"
                            isSel -> "#0F766E"
                            else -> "#CBD5E1"
                        }
                        val row = TextView(context).apply {
                            text = "${opt.key})  ${opt.text}"
                            textSize = 14f
                            setTextColor(Color.parseColor("#0F172A"))
                            layoutDirection = View.LAYOUT_DIRECTION_LTR
                            background = boxBg(fill, stroke)
                            setPadding(px(12), px(11), px(12), px(11))
                            setOnClickListener {
                                if (checked) return@setOnClickListener
                                if (q.correctKeys.size <= 1) {
                                    chosen.clear()
                                    chosen.add(opt.key)
                                } else {
                                    if (!chosen.add(opt.key)) chosen.remove(opt.key)
                                }
                                render()
                            }
                        }
                        card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = px(8)
                        })
                    }

                    if (checked) {
                        card.addView(TextView(context).apply {
                            text = "پاسخ صحیح: ${q.correctKeys.joinToString(" , ")}"
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F766E"))
                            background = boxBg("#F0FDFA", "#5EEAD4")
                            setPadding(px(12), px(10), px(12), px(10))
                        })
                    } else {
                        val btnCheck = TextView(context).apply {
                            text = "بررسی پاسخ"
                            gravity = Gravity.CENTER
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            background = boxBg("#0F766E", "#0F766E", 999f)
                            setPadding(0, px(11), 0, px(11))
                            setOnClickListener {
                                if (chosen.isNotEmpty()) {
                                    mcqChecked.add(q.id)
                                    val (nc, nt) = computeScore()
                                    onScoreUpdate(nc, nt)
                                    render()
                                }
                            }
                        }
                        card.addView(btnCheck, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = px(6)
                        })
                    }

                    // Prev / Next buttons
                    val nav = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, px(12), 0, 0)
                    }
                    val btnPrev = TextView(context).apply {
                        text = "سوال قبلی"
                        gravity = Gravity.CENTER
                        background = boxBg("#F1F5F9", "#CBD5E1")
                        setPadding(0, px(9), 0, px(9))
                        isEnabled = mcqIdx > 0
                        alpha = if (mcqIdx > 0) 1f else 0.4f
                        setOnClickListener { if (mcqIdx > 0) { mcqIdx--; render() } }
                    }
                    val btnNext = TextView(context).apply {
                        text = "سوال بعدی"
                        gravity = Gravity.CENTER
                        background = boxBg("#F1F5F9", "#CBD5E1")
                        setPadding(0, px(9), 0, px(9))
                        isEnabled = mcqIdx < mcqs.lastIndex
                        alpha = if (mcqIdx < mcqs.lastIndex) 1f else 0.4f
                        setOnClickListener { if (mcqIdx < mcqs.lastIndex) { mcqIdx++; render() } }
                    }
                    nav.addView(btnPrev, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(6) })
                    nav.addView(btnNext, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    card.addView(nav)
                }
            } else {
                val q = writtens.getOrNull(writtenIdx)
                if (q == null) {
                    card.addView(TextView(context).apply {
                        text = "تمرین تشریحی برای این یونیت موجود نیست."
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 13f
                    })
                } else {
                    card.addView(TextView(context).apply {
                        text = "تمرین ${writtenIdx + 1} از ${writtens.size}  ·  ${q.sourceLabel}"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F766E"))
                    })
                    card.addView(TextView(context).apply {
                        text = q.prompt
                        textSize = 14f
                        setTextColor(Color.parseColor("#0F172A"))
                        setPadding(0, px(10), 0, px(10))
                    })

                    val edit = EditText(context).apply {
                        setText(writtenInputs[q.id] ?: "")
                        hint = "پاسخ انگلیسی را اینجا تایپ کنید..."
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        background = boxBg("#F8FAFC", "#94A3B8")
                        setPadding(px(12), px(10), px(12), px(10))
                        textSize = 15f
                    }
                    card.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = px(10)
                    })

                    val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    val btnCheck = TextView(context).apply {
                        text = "بررسی پاسخ"
                        gravity = Gravity.CENTER
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        background = boxBg("#0F766E", "#0F766E", 999f)
                        setPadding(0, px(11), 0, px(11))
                        setOnClickListener {
                            writtenInputs[q.id] = edit.text.toString()
                            if (edit.text.toString().isNotBlank()) {
                                writtenChecked.add(q.id)
                                val (nc, nt) = computeScore()
                                onScoreUpdate(nc, nt)
                                render()
                            }
                        }
                    }
                    val btnReveal = TextView(context).apply {
                        text = "نمایش جواب"
                        gravity = Gravity.CENTER
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#334155"))
                        background = boxBg("#F8FAFC", "#94A3B8")
                        setPadding(px(14), px(11), px(14), px(11))
                        setOnClickListener {
                            writtenInputs[q.id] = edit.text.toString()
                            writtenRevealed.add(q.id)
                            render()
                        }
                    }
                    actions.addView(btnCheck, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8) })
                    actions.addView(btnReveal)
                    card.addView(actions)

                    if (q.id in writtenChecked || q.id in writtenRevealed) {
                        val ok = q.id in writtenChecked && isCorrectWritten(writtenInputs[q.id] ?: "", q.acceptedAnswers)
                        val fill = if (q.id !in writtenChecked) "#F1F5F9" else if (ok) "#DCFCE7" else "#FEE2E2"
                        val stroke = if (q.id !in writtenChecked) "#CBD5E1" else if (ok) "#16A34A" else "#DC2626"
                        val msg = buildString {
                            if (q.id in writtenChecked) {
                                append(if (ok) "آفرین! پاسخ شما کاملاً صحیح است.\n" else "پاسخ دقیق نبود؛ جواب صحیح:\n")
                            }
                            append(q.acceptedAnswers.joinToString("  /  "))
                        }
                        card.addView(TextView(context).apply {
                            text = msg
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F172A"))
                            background = boxBg(fill, stroke)
                            setPadding(px(12), px(10), px(12), px(10))
                        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = px(10)
                        })
                    }

                    val nav = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, px(12), 0, 0)
                    }
                    val btnPrev = TextView(context).apply {
                        text = "تمرین قبلی"
                        gravity = Gravity.CENTER
                        background = boxBg("#F1F5F9", "#CBD5E1")
                        setPadding(0, px(9), 0, px(9))
                        isEnabled = writtenIdx > 0
                        alpha = if (writtenIdx > 0) 1f else 0.4f
                        setOnClickListener {
                            writtenInputs[q.id] = edit.text.toString()
                            if (writtenIdx > 0) { writtenIdx--; render() }
                        }
                    }
                    val btnNext = TextView(context).apply {
                        text = "تمرین بعدی"
                        gravity = Gravity.CENTER
                        background = boxBg("#F1F5F9", "#CBD5E1")
                        setPadding(0, px(9), 0, px(9))
                        isEnabled = writtenIdx < writtens.lastIndex
                        alpha = if (writtenIdx < writtens.lastIndex) 1f else 0.4f
                        setOnClickListener {
                            writtenInputs[q.id] = edit.text.toString()
                            if (writtenIdx < writtens.lastIndex) { writtenIdx++; render() }
                        }
                    }
                    nav.addView(btnPrev, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(6) })
                    nav.addView(btnNext, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    card.addView(nav)
                }
            }

            contentBox.addView(card)
        }

        tabMcq.setOnClickListener { activeTab = "mcq"; render() }
        tabWritten.setOnClickListener { activeTab = "written"; render() }

        render()
        dialog.setContentView(root)
        dialog.show()
    }
}
