package ir.hiwa.grammarinuse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

/**
 * Custom high-performance vertical page viewer with:
 * - Exact vertical fill preserving aspect ratio
 * - Pinch-to-Zoom + Double-Tap Zoom (1x <-> 1.85x) + smooth Pan
 * - Horizontal swipe to turn pages at 1x zoom
 * - Exact percentage-aligned clickable section hotspots (A-F) that stay accurate when zoomed
 * - Optional Warm / Night eye-comfort tint
 */
class ZoomablePageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var sections: List<SectionHotspot> = emptyList()
    private var activeSection: String? = null
    private var warmMode: Boolean = false

    private var scaleFactor = 1f
    private var panX = 0f
    private var panY = 0f
    private val baseRect = RectF()
    private val drawRect = RectF()

    var onSectionTapped: ((SectionHotspot) -> Unit)? = null
    var onSwipeNext: (() -> Unit)? = null
    var onSwipePrev: (() -> Unit)? = null

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

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1f, 3.0f)
            clampPan()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
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
            if (scaleFactor > 1.15f) {
                scaleFactor = 1f
                panX = 0f
                panY = 0f
            } else {
                scaleFactor = 1.85f
                panX = (width / 2f - e.x) * (scaleFactor - 1f)
                panY = (height / 2f - e.y) * (scaleFactor - 1f)
                clampPan()
            }
            invalidate()
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distX: Float, distY: Float): Boolean {
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

    fun setPage(bmp: Bitmap?, pageSections: List<SectionHotspot>) {
        bitmap = bmp
        sections = pageSections
        scaleFactor = 1f
        panX = 0f
        panY = 0f
        computeBaseRect()
        invalidate()
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

        // Highlight currently active section
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

        if (warmMode) {
            canvas.drawRect(drawRect, warmPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
}
