package ir.hiwa.grammarinuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.hypot

enum class DrawTool { NONE, PEN, HIGHLIGHTER, TEXT, ERASER }

/**
 * High-DPI PDF/Image Page Viewer with:
 * - Pinch-to-Zoom (up to 3.5x) + Double-Tap Zoom + smooth Pan + Dynamic Hi-DPI zoom callback
 * - Horizontal swipe to turn pages at 1x zoom (when not in annotation mode)
 * - Exact percentage-aligned clickable section hotspots (A-F)
 * - Full Paint-style AnnotationLayer: Pen, Highlighter, Eraser, and Movable/Editable Text Notes
 *   (1-finger draw/place/drag, 2-finger pinch-zoom & pan simultaneously!)
 * - Optional Warm eye-comfort tint
 */
class ZoomablePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var sections: List<SectionHotspot> = emptyList()
    private var activeSection: String? = null
    private var warmMode: Boolean = false

    // Annotation state
    var drawTool: DrawTool = DrawTool.NONE
        set(value) {
            field = value
            activeStroke = null
            draggingNote = null
            invalidate()
        }
    var strokeColor: Int = Color.parseColor("#0F172A")
    var strokeWidthDp: Float = 3.0f
    private var annotations: PageAnnotations = PageAnnotations()
    private var activeStroke: AnnotationStroke? = null
    private var draggingNote: TextAnnotation? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var noteMoved = false
    private var isMultiTouch = false
    private var lastTwoFingerX = 0f
    private var lastTwoFingerY = 0f

    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    private val baseRect = RectF()
    private val drawRect = RectF()
    private val dp = context.resources.displayMetrics.density

    var onSectionTapped: ((SectionHotspot) -> Unit)? = null
    var onSwipeNext: (() -> Unit)? = null
    var onSwipePrev: (() -> Unit)? = null
    var onAnnotationsChanged: ((PageAnnotations) -> Unit)? = null
    var onRequestAddTextNote: ((normX: Float, normY: Float) -> Unit)? = null
    var onRequestEditTextNote: ((TextAnnotation) -> Unit)? = null
    var onZoomSettled: ((Float) -> Unit)? = null

    private val zoomNotifyRunnable = Runnable { onZoomSettled?.invoke(scaleFactor) }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CBD5E1")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(34, 15, 118, 110)
        style = Paint.Style.FILL
    }
    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F766E")
        style = Paint.Style.FILL
    }
    private val warmPaint = Paint().apply {
        color = Color.argb(44, 245, 198, 118)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val noteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FEF9C3")
        style = Paint.Style.FILL
    }
    private val noteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CA8A04")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scratchPath = Path()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1f, 3.5f)
            clampPan()
            invalidate()
            scheduleZoomNotify()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (drawTool != DrawTool.NONE) return false
            // Check if user tapped an existing sticky text note first
            val hitNote = findNoteAt(e.x, e.y)
            if (hitNote != null) {
                onRequestEditTextNote?.invoke(hitNote)
                return true
            }
            if (sections.isEmpty() || drawRect.height() <= 0f) return false
            if (!drawRect.contains(e.x, e.y)) return false
            val relY = (e.y - drawRect.top) / drawRect.height()
            val hit = sections.firstOrNull { relY >= it.topPct && relY <= (it.topPct + it.heightPct) }
            if (hit != null) {
                onSectionTapped?.invoke(hit)
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (drawTool != DrawTool.NONE) return false
            if (scaleFactor > 1.15f) {
                scaleFactor = 1f
                panX = 0f
                panY = 0f
            } else {
                scaleFactor = 2.0f
                panX = (width / 2f - e.x) * (scaleFactor - 1f)
                panY = (height / 2f - e.y) * (scaleFactor - 1f)
                clampPan()
            }
            invalidate()
            scheduleZoomNotify()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
            if (drawTool != DrawTool.NONE) return false
            if (scaleFactor > 1.02f) {
                panX -= distX
                panY -= distY
                clampPan()
                invalidate()
                return true
            }
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (drawTool != DrawTool.NONE) return false
            if (scaleFactor <= 1.05f && e1 != null) {
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > 90 && abs(dx) > abs(dy) * 1.4f && abs(velocityX) > 250) {
                    if (dx < 0) onSwipeNext?.invoke() else onSwipePrev?.invoke()
                    return true
                }
            }
            return false
        }
    })

    private fun scheduleZoomNotify() {
        removeCallbacks(zoomNotifyRunnable)
        postDelayed(zoomNotifyRunnable, 220L)
    }

    fun setPage(bmp: Bitmap?, pageSections: List<SectionHotspot>, pageAnnotations: PageAnnotations = PageAnnotations()) {
        bitmap = bmp
        sections = pageSections
        annotations = pageAnnotations
        activeStroke = null
        draggingNote = null
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        computeBaseRect()
        invalidate()
    }

    /** Replaces bitmap in-place without resetting zoom, pan, or annotations (for dynamic high-DPI PDF zoom). */
    fun updateBitmapPreservingView(bmp: Bitmap) {
        bitmap = bmp
        computeBaseRect()
        clampPan()
        invalidate()
    }

    fun getCurrentAnnotations(): PageAnnotations = annotations

    fun addOrUpdateTextNote(note: TextAnnotation) {
        val idx = annotations.notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) {
            annotations.notes[idx] = note
        } else {
            annotations.notes.add(note)
        }
        invalidate()
        onAnnotationsChanged?.invoke(annotations)
    }

    fun deleteTextNote(noteId: String) {
        annotations.notes.removeAll { it.id == noteId }
        invalidate()
        onAnnotationsChanged?.invoke(annotations)
    }

    fun undoLastAnnotation(): Boolean {
        if (annotations.strokes.isNotEmpty()) {
            annotations.strokes.removeAt(annotations.strokes.lastIndex)
            invalidate()
            onAnnotationsChanged?.invoke(annotations)
            return true
        }
        if (annotations.notes.isNotEmpty()) {
            annotations.notes.removeAt(annotations.notes.lastIndex)
            invalidate()
            onAnnotationsChanged?.invoke(annotations)
            return true
        }
        return false
    }

    fun clearAllAnnotations() {
        annotations.strokes.clear()
        annotations.notes.clear()
        activeStroke = null
        draggingNote = null
        invalidate()
        onAnnotationsChanged?.invoke(annotations)
    }

    fun setActiveSection(label: String?) {
        activeSection = label
        invalidate()
    }

    fun setWarmMode(enabled: Boolean) {
        warmMode = enabled
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeBaseRect()
        clampPan()
    }

    private fun computeBaseRect() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
        var targetH = height.toFloat()
        var targetW = targetH * ratio
        if (targetW > width.toFloat()) {
            targetW = width.toFloat()
            targetH = targetW / ratio
        }
        val left = (width - targetW) / 2f
        val top = (height - targetH) / 2f
        baseRect.set(left, top, left + targetW, top + targetH)
    }

    private fun clampPan() {
        val scaledW = baseRect.width() * scaleFactor
        val scaledH = baseRect.height() * scaleFactor
        val maxPanX = ((scaledW - width) / 2f).coerceAtLeast(0f)
        val maxPanY = ((scaledH - height) / 2f).coerceAtLeast(0f)
        panX = panX.coerceIn(-maxPanX, maxPanX)
        panY = panY.coerceIn(-maxPanY, maxPanY)
    }

    private fun screenToNorm(sx: Float, sy: Float): Pair<Float, Float>? {
        if (drawRect.width() <= 0f || drawRect.height() <= 0f) return null
        val nx = ((sx - drawRect.left) / drawRect.width()).coerceIn(0f, 1f)
        val ny = ((sy - drawRect.top) / drawRect.height()).coerceIn(0f, 1f)
        return Pair(nx, ny)
    }

    private fun noteRectOnScreen(note: TextAnnotation): RectF {
        val sx = drawRect.left + note.x * drawRect.width()
        val sy = drawRect.top + note.y * drawRect.height()
        noteTextPaint.textSize = (11.5f * dp * scaleFactor).coerceIn(10f * dp, 26f * dp)
        val lines = note.text.lines().ifEmpty { listOf("") }
        var maxW = 0f
        for (l in lines) maxW = maxOf(maxW, noteTextPaint.measureText(l))
        val padH = 7f * dp * scaleFactor.coerceAtMost(1.8f)
        val padV = 5f * dp * scaleFactor.coerceAtMost(1.8f)
        val lineH = noteTextPaint.fontSpacing
        val boxW = (maxW + padH * 2).coerceAtLeast(36f * dp)
        val boxH = lines.size * lineH + padV * 2
        return RectF(sx - boxW / 2f, sy - boxH / 2f, sx + boxW / 2f, sy + boxH / 2f)
    }

    private fun findNoteAt(sx: Float, sy: Float): TextAnnotation? {
        for (i in annotations.notes.indices.reversed()) {
            val nt = annotations.notes[i]
            val r = noteRectOnScreen(nt)
            r.inset(-8f * dp, -8f * dp)
            if (r.contains(sx, sy)) return nt
        }
        return null
    }

    private fun eraseAt(nx: Float, ny: Float) {
        val radius = 0.032f / scaleFactor.coerceAtLeast(1f)
        val removed = annotations.strokes.removeAll { st ->
            st.points.any { pt -> hypot(pt.x - nx, pt.y - ny) <= radius }
        }
        if (removed) {
            invalidate()
            onAnnotationsChanged?.invoke(annotations)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (baseRect.isEmpty) computeBaseRect()

        val cx = baseRect.centerX() + panX
        val cy = baseRect.centerY() + panY
        val halfW = (baseRect.width() * scaleFactor) / 2f
        val halfH = (baseRect.height() * scaleFactor) / 2f
        drawRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        canvas.drawBitmap(bmp, null, drawRect, null)
        canvas.drawRect(drawRect, borderPaint)

        // Highlight currently active audio section
        val active = activeSection
        if (active != null) {
            val sec = sections.firstOrNull { it.label == active }
            if (sec != null) {
                val top = drawRect.top + sec.topPct * drawRect.height()
                val bottom = top + sec.heightPct * drawRect.height()
                canvas.drawRect(drawRect.left, top, drawRect.right, bottom, activeFillPaint)
                canvas.drawRect(drawRect.left, top, drawRect.left + (8f * scaleFactor), bottom, activeBarPaint)
            }
        }

        // Draw saved & active freehand strokes
        canvas.save()
        canvas.clipRect(drawRect)
        for (st in annotations.strokes) drawStroke(canvas, st)
        activeStroke?.let { drawStroke(canvas, it) }

        // Draw movable sticky text notes
        for (nt in annotations.notes) {
            val r = noteRectOnScreen(nt)
            val corner = 4f * dp
            canvas.drawRoundRect(r, corner, corner, noteBgPaint)
            canvas.drawRoundRect(r, corner, corner, noteBorderPaint)
            noteTextPaint.color = nt.color
            val lines = nt.text.lines()
            val padH = 7f * dp * scaleFactor.coerceAtMost(1.8f)
            val padV = 5f * dp * scaleFactor.coerceAtMost(1.8f)
            var ty = r.top + padV - noteTextPaint.ascent()
            for (l in lines) {
                canvas.drawText(l, r.left + padH, ty, noteTextPaint)
                ty += noteTextPaint.fontSpacing
            }
        }
        canvas.restore()

        if (warmMode) {
            canvas.drawRect(drawRect, warmPaint)
        }
    }

    private fun drawStroke(canvas: Canvas, st: AnnotationStroke) {
        if (st.points.isEmpty()) return
        strokePaint.color = st.color
        strokePaint.alpha = if (st.isHighlighter) 92 else 255
        strokePaint.strokeWidth = st.widthDp * dp * scaleFactor * (if (st.isHighlighter) 2.6f else 1f)

        if (st.points.size == 1) {
            val px = drawRect.left + st.points[0].x * drawRect.width()
            val py = drawRect.top + st.points[0].y * drawRect.height()
            canvas.drawPoint(px, py, strokePaint)
            return
        }
        scratchPath.reset()
        val p0 = st.points[0]
        scratchPath.moveTo(drawRect.left + p0.x * drawRect.width(), drawRect.top + p0.y * drawRect.height())
        for (i in 1 until st.points.size) {
            val pt = st.points[i]
            scratchPath.lineTo(drawRect.left + pt.x * drawRect.width(), drawRect.top + pt.y * drawRect.height())
        }
        canvas.drawPath(scratchPath, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawTool == DrawTool.NONE) {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }

        // In Annotation Mode: 2 fingers = Pinch-Zoom & Pan, 1 finger = Draw / Erase / Sticky Note
        if (event.pointerCount >= 2) {
            if (!isMultiTouch) {
                isMultiTouch = true
                activeStroke = null
                draggingNote = null
                lastTwoFingerX = (event.getX(0) + event.getX(1)) / 2f
                lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val mx = (event.getX(0) + event.getX(1)) / 2f
                val my = (event.getY(0) + event.getY(1)) / 2f
                if (scaleFactor > 1.02f) {
                    panX += (mx - lastTwoFingerX)
                    panY += (my - lastTwoFingerY)
                    clampPan()
                    invalidate()
                }
                lastTwoFingerX = mx
                lastTwoFingerY = my
            }
            scaleDetector.onTouchEvent(event)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isMultiTouch = false
                if (!drawRect.contains(event.x, event.y)) return true
                val (nx, ny) = screenToNorm(event.x, event.y) ?: return true
                when (drawTool) {
                    DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                        activeStroke = AnnotationStroke(
                            color = strokeColor,
                            widthDp = strokeWidthDp,
                            isHighlighter = (drawTool == DrawTool.HIGHLIGHTER),
                            points = mutableListOf(StrokePoint(nx, ny))
                        )
                        invalidate()
                    }
                    DrawTool.ERASER -> eraseAt(nx, ny)
                    DrawTool.TEXT -> {
                        val hit = findNoteAt(event.x, event.y)
                        if (hit != null) {
                            draggingNote = hit
                            dragStartX = event.x
                            dragStartY = event.y
                            noteMoved = false
                        } else {
                            onRequestAddTextNote?.invoke(nx, ny)
                        }
                    }
                    DrawTool.NONE -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMultiTouch) return true
                val (nx, ny) = screenToNorm(event.x, event.y) ?: return true
                when (drawTool) {
                    DrawTool.PEN, DrawTool.HIGHLIGHTER -> {
                        val st = activeStroke ?: return true
                        val last = st.points.lastOrNull()
                        if (last == null || hypot(nx - last.x, ny - last.y) > 0.0025f) {
                            st.points.add(StrokePoint(nx, ny))
                            invalidate()
                        }
                    }
                    DrawTool.ERASER -> eraseAt(nx, ny)
                    DrawTool.TEXT -> {
                        val dn = draggingNote ?: return true
                        if (hypot(event.x - dragStartX, event.y - dragStartY) > 8f * dp) {
                            noteMoved = true
                        }
                        if (noteMoved) {
                            dn.x = nx
                            dn.y = ny
                            invalidate()
                        }
                    }
                    DrawTool.NONE -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMultiTouch) {
                    isMultiTouch = false
                    return true
                }
                activeStroke?.let { st ->
                    if (st.points.isNotEmpty()) {
                        annotations.strokes.add(st)
                        onAnnotationsChanged?.invoke(annotations)
                    }
                    activeStroke = null
                    invalidate()
                }
                draggingNote?.let { dn ->
                    if (noteMoved) {
                        onAnnotationsChanged?.invoke(annotations)
                    } else {
                        onRequestEditTextNote?.invoke(dn)
                    }
                    draggingNote = null
                    noteMoved = false
                }
            }
        }
        return true
    }
}
