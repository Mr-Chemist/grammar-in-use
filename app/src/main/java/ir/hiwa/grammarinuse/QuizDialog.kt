package ir.hiwa.grammarinuse

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Redesigned Unit Quiz Dialog (v2):
 * - Clean title: "Unit X Quiz" / "آزمون یونیت X" (no "Interactive", no unit number beside prompt)
 * - Bilingual EN / FA with dynamic LTR / RTL
 * - Study Guide MCQs with restored ________ blanks
 * - Fill-in-the-Blank written exercises with live-filled highlighted blank preview + smart contraction grading
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
        Regex("\\bi'll\\b") to "i will",
        Regex("\\byou'll\\b") to "you will",
        Regex("\\bhe'll\\b") to "he will",
        Regex("\\bshe'll\\b") to "she will",
        Regex("\\bit'll\\b") to "it will",
        Regex("\\bwe'll\\b") to "we will",
        Regex("\\bthey'll\\b") to "they will",
        Regex("\\bi've\\b") to "i have",
        Regex("\\byou've\\b") to "you have",
        Regex("\\bwe've\\b") to "we have",
        Regex("\\bthey've\\b") to "they have",
        Regex("\\bi'd\\b") to "i would",
        Regex("\\byou'd\\b") to "you would",
        Regex("\\bhe'd\\b") to "he would",
        Regex("\\bshe'd\\b") to "she would",
        Regex("\\bwe'd\\b") to "we would",
        Regex("\\bthey'd\\b") to "they would",
        Regex("\\bthere's\\b") to "there is",
        Regex("\\bthat's\\b") to "that is",
        Regex("\\bwhat's\\b") to "what is",
        Regex("\\bwho's\\b") to "who is",
        Regex("\\blet's\\b") to "let us",
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
        Regex("\\bcan not\\b") to "cannot",
        Regex("\\bcouldn't\\b") to "could not",
        Regex("\\bwouldn't\\b") to "would not",
        Regex("\\bshouldn't\\b") to "should not",
        Regex("\\bmustn't\\b") to "must not",
        Regex("\\bmightn't\\b") to "might not"
    )

    private fun normalize(raw: String): String {
        var s = raw.lowercase()
            .replace(Regex("[’‘`]"), "'")
            .replace(Regex("[.,!?;:\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        for ((rx, rep) in contractions) s = s.replace(rx, rep)
        // Also normalize generic "'s" -> " is" for comparisons like "Sarah's working" vs "Sarah is working"
        s = s.replace(Regex("'s\\b"), " is")
        return s.replace(Regex("[()]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun isCorrectWritten(input: String, accepted: List<String>): Boolean {
        val u = normalize(input)
        if (u.isEmpty()) return false
        for (acc in accepted) {
            if (normalize(acc) == u) return true
            // Also accept omitting optional parenthetical words in answer key like "(that)" or "(it)"
            val withoutParens = acc.replace(Regex("\\([^)]*\\)"), " ")
            if (normalize(withoutParens) == u) return true
        }
        return false
    }

    private fun buildLiveBlankSpan(prompt: String, userText: String, checked: Boolean, isCorrect: Boolean): CharSequence {
        val blankRegex = Regex("_{3,}|\\.{4,}")
        val match = blankRegex.find(prompt) ?: return prompt
        val ssb = SpannableStringBuilder()
        ssb.append(prompt.substring(0, match.range.first))
        val fill = if (userText.isBlank()) "  ________  " else "  $userText  "
        val start = ssb.length
        ssb.append(fill)
        val end = ssb.length

        val bg = when {
            checked && isCorrect -> Color.parseColor("#DCFCE7")
            checked && !isCorrect -> Color.parseColor("#FEE2E2")
            userText.isNotBlank() -> Color.parseColor("#CCFBF1")
            else -> Color.parseColor("#E2E8F0")
        }
        val fg = when {
            checked && isCorrect -> Color.parseColor("#15803D")
            checked && !isCorrect -> Color.parseColor("#B91C1C")
            userText.isNotBlank() -> Color.parseColor("#0F766E")
            else -> Color.parseColor("#64748B")
        }
        ssb.setSpan(BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(prompt.substring(match.range.last + 1))
        return ssb
    }

    fun show(
        context: Context,
        lang: AppLang,
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
            layoutDirection = lang.layoutDir
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
            text = Strings.quizTitle(lang, unitNumber)
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
            text = Strings.tabMcq(lang, mcqs.size)
            gravity = Gravity.CENTER
            setPadding(0, px(11), 0, px(11))
            textSize = 13f
        }
        val tabWritten = TextView(context).apply {
            text = Strings.tabWritten(lang, writtens.size)
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
                        text = Strings.noMcq(lang)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 13f
                    })
                } else {
                    card.addView(TextView(context).apply {
                        text = Strings.questionCounter(lang, mcqIdx + 1, mcqs.size, q.correctKeys.size > 1)
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F766E"))
                    })
                    card.addView(TextView(context).apply {
                        text = q.prompt
                        textSize = 15.5f
                        setLineSpacing(0f, 1.25f)
                        setTextColor(Color.parseColor("#0F172A"))
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        setPadding(0, px(10), 0, px(14))
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
                            text = Strings.correctAnswerLabel(lang, q.correctKeys.joinToString(" , "))
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#0F766E"))
                            background = boxBg("#F0FDFA", "#5EEAD4")
                            setPadding(px(12), px(10), px(12), px(10))
                        })
                    } else {
                        val btnCheck = TextView(context).apply {
                            text = Strings.checkAnswer(lang)
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

                    val nav = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, px(12), 0, 0)
                    }
                    val btnPrev = TextView(context).apply {
                        text = Strings.prevQ(lang)
                        gravity = Gravity.CENTER
                        background = boxBg("#F1F5F9", "#CBD5E1")
                        setPadding(0, px(9), 0, px(9))
                        isEnabled = mcqIdx > 0
                        alpha = if (mcqIdx > 0) 1f else 0.4f
                        setOnClickListener { if (mcqIdx > 0) { mcqIdx--; render() } }
                    }
                    val btnNext = TextView(context).apply {
                        text = Strings.nextQ(lang)
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
                        text = Strings.noWritten(lang)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#64748B"))
                        textSize = 13f
                    })
                } else {
                    card.addView(TextView(context).apply {
                        text = Strings.exerciseCounter(lang, writtenIdx + 1, writtens.size, q.sourceLabel)
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F766E"))
                    })

                    if (q.instruction.isNotBlank()) {
                        card.addView(TextView(context).apply {
                            text = q.instruction
                            textSize = 12.5f
                            setTypeface(null, Typeface.ITALIC)
                            setTextColor(Color.parseColor("#475569"))
                            layoutDirection = View.LAYOUT_DIRECTION_LTR
                            setPadding(0, px(6), 0, px(2))
                        })
                    }

                    val currentVal = writtenInputs[q.id] ?: ""
                    val isChecked = q.id in writtenChecked
                    val isRight = isChecked && isCorrectWritten(currentVal, q.acceptedAnswers)

                    val tvPrompt = TextView(context).apply {
                        text = buildLiveBlankSpan(q.prompt, currentVal, isChecked, isRight)
                        textSize = 15.5f
                        setLineSpacing(0f, 1.3f)
                        setTextColor(Color.parseColor("#0F172A"))
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        setPadding(0, px(10), 0, px(12))
                    }
                    card.addView(tvPrompt)

                    val edit = EditText(context).apply {
                        setText(currentVal)
                        hint = Strings.typeBlankHint(lang)
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        layoutDirection = View.LAYOUT_DIRECTION_LTR
                        background = boxBg("#F8FAFC", "#94A3B8")
                        setPadding(px(12), px(10), px(12), px(10))
                        textSize = 15f
                        addTextChangedListener(object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                val txt = s?.toString() ?: ""
                                writtenInputs[q.id] = txt
                                tvPrompt.text = buildLiveBlankSpan(q.prompt, txt, false, false)
                            }
                            override fun afterTextChanged(s: Editable?) {}
                        })
                    }
                    card.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = px(10)
                    })

                    val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    val btnCheck = TextView(context).apply {
                        text = Strings.checkAnswer(lang)
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
                        text = Strings.showAnswer(lang)
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

                    if (isChecked || q.id in writtenRevealed) {
                        val fill = if (!isChecked) "#F1F5F9" else if (isRight) "#DCFCE7" else "#FEE2E2"
                        val stroke = if (!isChecked) "#CBD5E1" else if (isRight) "#16A34A" else "#DC2626"
                        val msg = buildString {
                            if (isChecked) {
                                append(if (isRight) Strings.writtenCorrect(lang) else Strings.writtenWrong(lang))
                                append("\n")
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
                        text = Strings.prevQ(lang)
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
                        text = Strings.nextQ(lang)
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
