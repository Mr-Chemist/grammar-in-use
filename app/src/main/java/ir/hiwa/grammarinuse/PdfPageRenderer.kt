package ir.hiwa.grammarinuse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Thread-safe native Android PdfRenderer wrapper.
 * Keeps one open PdfRenderer for the active pack PDF and renders crisp high-DPI bitmaps
 * (supporting dynamic re-rendering when the user zooms in).
 */
class PdfPageRenderer {
    private val lock = Any()
    private var currentFilePath: String? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    fun renderPage(pdfFile: File, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        synchronized(lock) {
            try {
                if (currentFilePath != pdfFile.absolutePath || renderer == null) {
                    closeInternal()
                    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd = fd
                    renderer = PdfRenderer(fd)
                    currentFilePath = pdfFile.absolutePath
                }
                val r = renderer ?: return null
                if (pageIndex < 0 || pageIndex >= r.pageCount) return null

                r.openPage(pageIndex).use { page ->
                    val safeW = targetWidthPx.coerceIn(720, 2600)
                    val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                    val safeH = (safeW * ratio).toInt().coerceIn(720, 3600)
                    val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                }
            } catch (_: Exception) {
                closeInternal()
                return null
            }
        }
    }

    fun close() {
        synchronized(lock) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        try {
            renderer?.close()
        } catch (_: Exception) {
        }
        try {
            pfd?.close()
        } catch (_: Exception) {
        }
        renderer = null
        pfd = null
        currentFilePath = null
    }
}
