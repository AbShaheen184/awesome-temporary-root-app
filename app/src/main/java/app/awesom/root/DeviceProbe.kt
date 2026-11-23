package app.awesom.root

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File

/**
 * Reads the device facts. Cheap fields come from [Build]; everything that needs
 * the shell (kernel banner, page size, SELinux, root state) is read through the
 * [Transport] and only when one is available.
 *
 * The kernel release is the one field the whole matching rule depends on, so
 * if the transport read comes back empty it is retried as a plain local exec —
 * an unreadable `uname -r` makes every candidate list meaningless.
 */
object DeviceProbe {

    /** Fields readable with no privilege at all. */
    fun local(): DeviceProfile {
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            buildDisplay = prop("ro.build.display.id"),
            buildIncremental = prop("ro.build.version.incremental"),
            buildDateUtc = prop("ro.build.date.utc"),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        )
    }

    /** Adds the shell-only fields. Safe to call when no transport is present. */
    fun withShell(base: DeviceProfile, transport: Transport?): DeviceProfile {
        if (transport == null) return base
        val proc = ShellReader(transport)
        var release = proc.first("uname -r")?.trim().orEmpty()
        if (release.isBlank()) release = readKernelDirect()
        val banner = proc.first("cat /proc/version")?.trim().orEmpty()
        val page = proc.first("getconf PAGESIZE")?.trim()?.toIntOrNull() ?: 4096
        val se = proc.first("getenforce")?.trim().orEmpty()
        val rooted = proc.first(
            "if grep -q '^kernelsu .* Live ' /proc/modules; then echo YES; " +
                "elif su -c id 2>/dev/null | grep -q uid=0; then echo YES; else echo NO; fi"
        )?.trim() == "YES"
        return base.copy(
            kernelRelease = release,
            kernelVersion = banner,
            pageSize = page,
            selinux = se,
            rootActive = rooted,
        )
    }

    /**
     * `uname -r` with no privilege at all. An app can exec /system/bin/uname
     * for itself, which covers the case where Shizuku answers but its
     * newProcess call is refused.
     */
    private fun readKernelDirect(): String = runCatching {
        val p = ProcessBuilder("/system/bin/uname", "-r").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out
    }.getOrDefault("")

    private fun prop(key: String): String = runCatching {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out
    }.getOrDefault("")

    /** Runs read-only shell commands and keeps the output. */
    private class ShellReader(private val transport: Transport) {
        private val cache = mutableMapOf<String, String>()

        fun first(cmd: String): String? {
            cache[cmd]?.let { return it }
            val proc = transport.start(
                listOf("/system/bin/sh", "-c", cmd),
                listOf("PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/"),
                "/",
            ) ?: return null
            val out = runCatching { proc.stdout.bufferedReader().readText().trim() }.getOrDefault("")
            proc.waitFor()
            if (out.isNotEmpty()) cache[cmd] = out
            return out.ifBlank { null }
        }
    }

    /** True when a KernelSU module is currently loaded (post-run verification). */
    fun kernelSuLive(transport: Transport?): Boolean {
        if (transport == null) return false
        val proc = transport.start(
            listOf("/system/bin/sh", "-c", "grep -m1 '^kernelsu ' /proc/modules"),
            listOf("PATH=/system/bin:/system/xbin", "HOME=/"), "/"
        ) ?: return false
        val out = runCatching { proc.stdout.bufferedReader().readText() }.getOrDefault("")
        proc.waitFor()
        return out.contains(" Live ") || out.contains("kernelsu")
    }
}
