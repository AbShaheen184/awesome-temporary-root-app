package app.awesom.root

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Exploit payloads, fetched on demand instead of shipped in the APK.
 *
 * The app used to bundle 43 `.so` files plus the TimerRoot `timer` binary
 * (~23 MB) in `assets/` and `jniLibs/`. They now live in `payloads/` at the
 * root of this repo, are downloaded from GitHub's raw host the first time a
 * loader needs one, and are cached under `filesDir/payloads/`.
 *
 * Downloading is enough for execution because [Runner.stageAsset] streams every
 * payload into `/data/local/tmp` through the transport: apps targeting API 29+
 * cannot `execve()` a file inside their own home directory (the W^X rule), so
 * nothing here is ever executed straight out of the cache.
 */
object PayloadStore {

    /** Raw host for the `payloads/` folder. Change it if the repo moves. */
    private const val BASE =
        "https://raw.githubusercontent.com/AbShaheen184/awesome-temporary-root-app/master/payloads"

    private const val HTTP_OK = 200

    private fun dir(context: Context): File =
        File(context.filesDir, "payloads").apply { mkdirs() }

    /** Cached copy of [name] (it may not exist yet). */
    fun cached(context: Context, name: String): File = File(dir(context), name)

    /**
     * Bytes of [name] — a payload name (`timer`, `libk66127.so`,
     * `akita-CP2A.260805.005.so`) or a small profile still bundled in assets
     * (`timerroot/conf/<release>.conf`).
     *
     * Order: cached download, bundled asset, download. [onStatus] receives the
     * one-line progress and failure messages that end up in the run log.
     */
    fun bytes(context: Context, name: String, onStatus: (String) -> Unit): ByteArray? {
        val base = name.substringAfterLast('/')
        val f = cached(context, base)
        if (f.isFile && f.length() > 0) return runCatching { f.readBytes() }.getOrNull()
        runCatching { context.assets.open(name).use { it.readBytes() } }
            .getOrNull()?.let { return it }
        return download(context, base, onStatus)
    }

    private fun download(context: Context, name: String, onStatus: (String) -> Unit): ByteArray? {
        val url = "$BASE/$name"
        onStatus("payload $name not cached — downloading…")
        var conn: HttpURLConnection? = null
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "awesomroot")
            }
            conn = c
            val code = c.responseCode
            if (code != HTTP_OK) {
                onStatus(
                    "payload download failed: HTTP $code — $name must exist in a public " +
                        "payloads/ folder ($url)"
                )
                return null
            }
            val expect = c.contentLengthLong
            val bytes = c.inputStream.use { it.readBytes() }
            if (expect > 0 && bytes.size.toLong() != expect) {
                onStatus("payload download truncated: ${bytes.size}/$expect bytes ($url)")
                return null
            }
            val f = cached(context, name)
            val part = File(f.parentFile, "$name.part")
            part.writeBytes(bytes)
            if (!part.renameTo(f)) {
                f.writeBytes(bytes)
                part.delete()
            }
            onStatus("payload $name ready (${(bytes.size + 1023) / 1024} KiB)")
            bytes
        } catch (t: Throwable) {
            onStatus("payload download failed: ${t.message ?: t.javaClass.simpleName} ($url)")
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
