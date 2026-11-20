package ir.hiwa.grammarinuse

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Native streaming ArvanCloud ZIP pack downloader & extractor.
 * Uses HttpURLConnection + ZipInputStream (zero RAM spikes, max 6 requests for the entire book!).
 */
class PackManager(context: Context) {
    private val rootDir = File(context.filesDir, "book_packs")
    private val markersDir = File(rootDir, "_markers")
    private val locks = ConcurrentHashMap<String, Any>()

    init {
        rootDir.mkdirs()
        markersDir.mkdirs()
    }

    fun isInstalled(packId: String): Boolean = File(markersDir, "$packId.ready").exists()

    fun getFile(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val f = File(rootDir, relativePath.trimStart('/'))
        return if (f.exists() && f.length() > 100) f else null
    }

    fun downloadAndExtract(
        pack: PackDef,
        onProgress: (pct: Int, extracting: Boolean) -> Unit
    ): Boolean {
        if (isInstalled(pack.id)) return true
        val lock = locks.getOrPut(pack.id) { Any() }
        synchronized(lock) {
            if (isInstalled(pack.id)) return true
            val urlStr = "${ArvanConfig.BASE_URL.trimEnd('/')}/${pack.remotePath.trimStart('/')}"
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                }
                if (conn.responseCode !in 200..299) return false

                val totalBytes = conn.contentLengthLong.coerceAtLeast(1L)
                val tempZip = File(rootDir, "tmp_${pack.id}.zip")
                conn.inputStream.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var written = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            written += read
                            val pct = ((written * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct, false)
                            }
                        }
                    }
                }

                onProgress(100, true)
                ZipInputStream(BufferedInputStream(tempZip.inputStream())).use { zis ->
                    val buf = ByteArray(16 * 1024)
                    val canonicalRoot = rootDir.canonicalPath
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (!entry.isDirectory) {
                            val outFile = File(rootDir, entry.name.trimStart('/'))
                            if (outFile.canonicalPath.startsWith(canonicalRoot)) {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    while (true) {
                                        val r = zis.read(buf)
                                        if (r <= 0) break
                                        fos.write(buf, 0, r)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                }
                tempZip.delete()
                File(markersDir, "${pack.id}.ready").writeText("ok")
                return true
            } catch (_: Exception) {
                File(rootDir, "tmp_${pack.id}.zip").delete()
                return false
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun getStorageStats(): Pair<Double, Int> {
        var bytes = 0L
        var files = 0
        rootDir.walkTopDown().forEach {
            if (it.isFile && !it.name.endsWith(".ready")) {
                bytes += it.length()
                files += 1
            }
        }
        val mb = Math.round((bytes / (1024.0 * 1024.0)) * 10.0) / 10.0
        return Pair(mb, files)
    }

    fun clearAll() {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        markersDir.mkdirs()
    }
}
