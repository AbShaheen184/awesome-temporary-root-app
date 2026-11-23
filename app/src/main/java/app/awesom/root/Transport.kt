package app.awesom.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The privileged execution channel. Every loader binary in the cloned repos is
 * exec'd as the shell user (uid 2000, `u:r:shell:s0`) through one of these.
 *
 * Order matters and matches upstream: Shizuku, then Stellar, then a live ADB
 * shell, then a real `su` if the device is already rooted.
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
        SuTransport(),
        ShizukuTransport(),
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

        /** Runs as uid 0 only if a root daemon already exists. */
        class SuTransport : Transport {
            override val label = "su"
            override fun available(): Boolean = runCatching {
                val p = ProcessBuilder("/system/bin/su", "-c", "id").start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor() == 0 && out.contains("uid=0")
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val pb = ProcessBuilder("/system/bin/su", "-c", cmd.joinToString(" ").replace("\"", "\\\""))
                    pb.directory(File(dir))
                    pb.environment().putAll(env.associate { it.substringBefore('=') to it.substringAfter('=') })
                    pb.redirectErrorStream(true)
                    SbProcess(pb.start())
                }.getOrNull()
        }

        class ShizukuTransport : Transport {
            override val label = "Shizuku"
            override fun available(): Boolean = runCatching {
                Shizuku.pingBinder() && Shizuku.getUid() == Process.SHELL_UID
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val binder = Shizuku.getBinder() ?: return null
                    val svc = rikka.shizuku.ShizukuServiceInterface.asInterface(binder) ?: return null
                    val p = svc.newProcess(cmd.toTypedArray(), env.toTypedArray(), dir)
                    ShizukuProcess(p)
                }.getOrNull()
        }

        /**
         * A local adb daemon on the device itself (the TimerRoot auto-root path
         * uses `adb` over localhost). Only usable when it answers.
         */
        class LocalAdbTransport : Transport {
            override val label = "Local ADB"
            override fun available(): Boolean = runCatching {
                val p = ProcessBuilder("/system/bin/sh", "-c", "echo >/dev/tcp/127.0.0.1/5555 && echo up")
                    .redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().contains("up") && p.waitFor() == 0
            }.getOrDefault(false)

            override fun start(cmd: List<String>, env: List<String>, dir: String): PrivProcess? =
                runCatching {
                    val pb = ProcessBuilder("/system/bin/sh", "-c", cmd.joinToString(" "))
                    pb.directory(File(dir))
                    pb.redirectErrorStream(true)
                    SbProcess(pb.start())
                }.getOrNull()
        }

        private class SbProcess(private val p: Process) : PrivProcess {
            override val stdout: InputStream get() = p.inputStream
            override val stderr: InputStream get() = p.errorStream
            override fun waitFor(): Int = runCatching { p.waitFor() }.getOrDefault(-1)
            override fun destroy() = runCatching { p.destroy() }
        }

        private class ShizukuProcess(
            private val p: rikka.shizuku.ShizukuProcess,
        ) : PrivProcess {
            override val stdout: InputStream
                get() = android.os.ParcelFileDescriptor.AutoCloseInputStream(p.inputStream)
            override val stderr: InputStream
                get() = android.os.ParcelFileDescriptor.AutoCloseInputStream(p.errorStream)
            override fun waitFor(): Int = runCatching { p.waitFor() }.getOrDefault(-1)
            override fun destroy() = runCatching { p.destroy() }
        }
    }
}
