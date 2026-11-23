package app.awesom.root

import android.content.Context
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream

/**
 * The privileged execution channel. Every loader binary in the cloned repos is
 * exec'd as the shell user (uid 2000, `u:r:shell:s0`) through one of these.
 *
 * Order matches upstream usage: Shizuku, then a live `su` (device already
 * rooted), then a local ADB shell on the device itself.
 */
interface Transport {
    /** Short label for the UI. */
    val label: String

    /** True when the transport is usable right now. */
    fun available(): Boolean

    /**
     * Starts [cmd] with [env] in [dir] and returns a live handle.
     * Implementations must not throw; they return null on failure.
     */
    fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess?
}

interface PrivProcess {
    val stdout: InputStream
    val stderr: InputStream
    fun waitFor(): Int
    fun destroy()
}

/** Picks the best transport currently available. */
class TransportSelector(private val context: Context) {

    private val transports: List<Transport> = listOf(
        ShizukuTransport(),
        SuTransport(),
        LocalAdbTransport(),
    )

    fun pick(): Transport? = transports.firstOrNull { runCatching { it.available() }.getOrDefault(false) }

    fun all(): List<Transport> = transports

    /** True when a KernelSU-compatible manager is installed with libksud.so. */
    fun findManager(): String? {
        val pm = context.packageManager
        for (pkg in MANAGER_PACKAGES) {
            val app = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: continue
            val dir = app.nativeLibraryDir ?: continue
            if (File(dir, "libksud.so").isFile) return pkg
        }
        return null
    }

    fun managerHasKsud(pkg: String): Boolean = runCatching {
        val app = context.packageManager.getApplicationInfo(pkg, 0)
        val dir = app.nativeLibraryDir ?: return false
        File(dir, "libksud.so").isFile
    }.getOrDefault(false)

    /**
     * Runs [script] as the shell user and returns (exitCode, stdout+stderr).
     * Used for the read-only device facts, never for a loader run.
     */
    fun shell(script: String): Pair<Int, String> {
        val t = pick() ?: return -1 to "no transport"
        val p = t.start(
            listOf("/system/bin/sh", "-c", script),
            listOf("PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/"), "/"
        ) ?: return -1 to "transport refused"
        val out = runCatching { p.stdout.bufferedReader().readText() }.getOrDefault("")
        val err = runCatching { p.stderr.bufferedReader().readText() }.getOrDefault("")
        val rc = p.waitFor()
        return rc to (out + err)
    }

    companion object {
        /** KernelSU-family packages, most common first. */
        val MANAGER_PACKAGES = listOf(
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "kqvdie.zeykvw.hpbcyd",
            "com.resukisu.resukisu",
            "com.sukisu.ultra",
            "me.yuki.fina",
        )

        /**
         * Shizuku: the shell-uid process API. `newProcess` returns an
         * `IRemoteProcess` whose streams are ParcelFileDescriptors.
         */
        class ShizukuTransport : Transport {
            override val label = "Shizuku"

            override fun available(): Boolean = runCatching {
                Shizuku.pingBinder() && Shizuku.getUid() == android.os.Process.SHELL_UID
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val binder = Shizuku.getBinder() ?: return null
                    val svc = moe.shizuku.server.IShizukuService.Stub.asInterface(binder) ?: return null
                    val p = svc.newProcess(cmd.toTypedArray(), env.toTypedArray(), dir) ?: return null
                    ShizukuProcess(p)
                }.getOrNull()
        }

        /** uid 0 through an already-running root daemon. */
        class SuTransport : Transport {
            override val label = "su"

            override fun available(): Boolean = runCatching {
                val p = ProcessBuilder("/system/bin/su", "-c", "id").start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor() == 0 && out.contains("uid=0")
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val script = "cd ${dir.shq()} && ${env.joinToString(" ")} ${cmd.joinToString(" ") { it.shq() }}"
                    val pb = ProcessBuilder("/system/bin/su", "-c", script)
                    pb.redirectErrorStream(true)
                    SbProcess(pb.start())
                }.getOrNull()
        }

        /**
         * A local adb daemon on the device itself (the TimerRoot auto-root path
         * uses `adb` over localhost). Only usable when port 5555 answers.
         */
        class LocalAdbTransport : Transport {
            override val label = "Local ADB"

            override fun available(): Boolean = runCatching {
                val p = ProcessBuilder("/system/bin/sh", "-c", "echo > /dev/tcp/127.0.0.1/5555")
                    .redirectErrorStream(true).start()
                p.waitFor() == 0
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val pb = ProcessBuilder("/system/bin/sh", "-c", cmd.joinToString(" ") { it.shq() })
                    pb.directory(File(dir))
                    pb.redirectErrorStream(true)
                    SbProcess(pb.start())
                }.getOrNull()
        }

        private class SbProcess(private val p: Process) : PrivProcess {
            override val stdout: InputStream get() = p.inputStream
            override val stderr: InputStream get() = p.errorStream
            override fun waitFor(): Int = runCatching { p.waitFor() }.getOrDefault(-1)
            override fun destroy() { runCatching { p.destroy() } }
        }

        private class ShizukuProcess(private val p: moe.shizuku.server.IRemoteProcess) : PrivProcess {
            override val stdout: InputStream
                get() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    runCatching { p.inputStream }.getOrNull() ?: android.os.ParcelFileDescriptor.fromFd(-1)
                )
            override val stderr: InputStream
                get() = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    runCatching { p.errorStream }.getOrNull() ?: android.os.ParcelFileDescriptor.fromFd(-1)
                )
            override fun waitFor(): Int = runCatching { p.waitFor() }.getOrDefault(-1)
            override fun destroy() { runCatching { p.destroy() } }
        }

        /** Single-quote a token for /system/bin/sh. */
        private fun String.shq(): String = "'" + replace("'", "'\\''") + "'"
    }
}
