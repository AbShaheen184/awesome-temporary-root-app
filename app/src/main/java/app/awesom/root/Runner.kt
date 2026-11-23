package app.awesom.root

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** A single line of output from a running loader. */
data class LogLine(val text: String, val kind: Kind) {
    enum class Kind { OUT, ERR, OK, FAIL, STEP }
}

sealed class RunResult {
    data class Success(val code: Int, val marker: String) : RunResult()
    data class Failure(val code: Int, val reason: String) : RunResult()
    data class NeedsTransport(val reason: String) : RunResult()
    data class NeedsManager(val pkg: String) : RunResult()
}

/**
 * Executes one loader's own prebuilt binary/profile.
 *
 * The per-loader knowledge (env vars, argv, success marker) is deliberately a
 * table here rather than a guess: each upstream runner has its own contract and
 * the strings below are taken from the runners' own source/strings.
 */
class Runner(private val context: Context) {

    private val workRoot = "/data/local/tmp/awesomroot"

    /**
     * Stages a bundled asset into the loader work dir by piping its bytes through
     * the transport as base64. Returns the staged path, or null.
     */
    private fun stageAsset(transport: Transport, assetName: String, destName: String): String? {
        val bytes = runCatching { context.assets.open(assetName).use { it.readBytes() } }.getOrNull()
            ?: return null
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val tmp = "$workRoot/$destName.stage"
        val sh = "mkdir -p $workRoot && echo $b64 | base64 -d > $tmp && chmod 755 $tmp && mv -f $tmp $workRoot/$destName"
        val proc = transport.start(
            listOf("/system/bin/sh", "-c", sh),
            listOf("PATH=/system/bin:/system/xbin", "HOME=/"), "/"
        ) ?: return null
        proc.stdout.bufferedReader().readText()
        proc.waitFor()
        return if (File(workRoot, destName).exists()) "$workRoot/$destName" else null
    }

    /**
     * Runs [target] against the device [profile].
     *
     * @param onLine streamed to the UI as it arrives.
     */
    fun run(
        loader: Loader,
        target: Target,
        profile: DeviceProfile,
        transport: Transport,
        managerPkg: String,
        onLine: (LogLine) -> Unit,
    ): RunResult {
        val kmi = target.kmi ?: IndexStore.kmiFor(profile.kernelRelease)
        if (kmi == null) {
            return RunResult.Failure(-1, "cannot derive KMI from '${profile.kernelRelease}'")
        }
        if (!context.packageManagerHas(managerPkg)) {
            return RunResult.NeedsManager(managerPkg)
        }

        return when (loader.id) {
            "shark" -> runShark(loader, target, profile, transport, managerPkg, kmi, onLine)
            "timerroot" -> runTimerRoot(loader, target, profile, transport, managerPkg, kmi, onLine)
            "root-my-pixel" -> runRootMyPixel(loader, target, profile, transport, managerPkg, kmi, onLine)
            "ghostlock-app", "ghostkey" -> runGhostlockConf(loader, target, profile, transport, managerPkg, kmi, onLine)
            "dirtyfrag", "dirtyfalg-vr", "dfroot", "galaxycrash" -> runDirtyFrag(loader, target, profile, transport, managerPkg, kmi, onLine)
            else -> runGeneric(loader, target, profile, transport, managerPkg, kmi, onLine)
        }
    }

    // ------------------------------------------------------------------ per loader

    /**
     * Shark: `libk<model>.so run` with the `PYB110_*` environment. The runner
     * does plant → cred → late-load itself and prints `LATE_LOAD_RC=`.
     */
    private fun runShark(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val lib = "lib${target.runner ?: return RunResult.Failure(-1, "no runner in index")}.so"
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, lib)
        if (!bin.isFile) return RunResult.Failure(-1, "missing runner $lib in APK")

        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin",
            "HOME=/",
            "PYB110_DIR=$workRoot",
            "PYB110_KMI=$kmi",
            "PYB110_MANAGER=kernelsu",
            "PYB110_MANAGER_PKG=$managerPkg",
            "PYB110_LATE_LOAD=1",
            "PYB110_GATE=0",
            "PYB110_SUSFS=0",
            "PYB110_VFS=0",
            "PYB110_CHASE=0",
        )
        return exec(bin.absolutePath, listOf("run"), env, workRoot, transport, onLine,
            successMarker = "LATE_LOAD_RC=0")
    }

    /**
     * TimerRoot: the exploit binary is staged at `/data/local/tmp/timer` and the
     * profile is a HOCON `conf/<release>.conf` selected by `uname -r` + page size.
     */
    private fun runTimerRoot(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val conf = target.runner ?: return RunResult.Failure(-1, "no profile in index")
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/",
            "TIMER_KMI=$kmi", "TIMER_MANAGER=$managerPkg",
        )
        val cmd = "/data/local/tmp/timer $conf"
        return exec("/system/bin/sh", listOf("-c", cmd), env, workRoot, transport, onLine,
            successMarker = "KSU_LOADED")
    }

    /** Root My Pixel: per-build `.so` from assets + `ksud late-load --kmi`. */
    private fun runRootMyPixel(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val asset = target.runner ?: return RunResult.Failure(-1, "no exploit asset in index")
        val staged = stageAsset(transport, asset, "rmp-${asset.substringAfterLast('/')}") ?: run {
            // asset may not be bundled; try the native lib dir first
            val n = File(context.applicationInfo.nativeLibraryDir, asset.substringAfterLast('/'))
            if (n.isFile) n.absolutePath else return RunResult.Failure(-1, "missing $asset")
        }
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/", "RMY_KMI=$kmi",
        )
        return exec(staged, emptyList(), env, workRoot, transport, onLine,
            successMarker = "ROOT_OK")
    }

    /** GhostLock family: native binary + a HOCON profile keyed on `uname -r`. */
    private fun runGhostlockConf(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, "libghostlock.so")
        if (!bin.isFile) return RunResult.Failure(-1, "missing libghostlock.so in APK")
        val conf = target.runner ?: return RunResult.Failure(-1, "no profile in index")
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/",
            "GHOSTLOCK_HOME=$workRoot", "GHOSTLOCK_KMI=$kmi",
        )
        return exec(bin.absolutePath, listOf("--ghostlock-app-call"), env, workRoot, transport, onLine,
            successMarker = "GHOSTLOCK_OK")
    }

    /** DirtyFrag family: helper + per-KMI `.ko`; the app picks the KMI module. */
    private fun runDirtyFrag(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val ko = "dfroot-$kmi.ko"
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, "libdfroot.so")
        if (!bin.isFile) return RunResult.Failure(-1, "missing libdfroot.so in APK")
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/",
            "DF_KMI=$kmi", "DF_KO=$ko", "DF_MANAGER=$managerPkg",
        )
        return exec(bin.absolutePath, emptyList(), env, workRoot, transport, onLine,
            successMarker = "DF_ROOT_OK")
    }

    /** Catch-all for loaders whose exact contract is not yet wired. */
    private fun runGeneric(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult = RunResult.Failure(
        -1,
        "${loader.name} is indexed but its runner contract is not wired yet " +
            "(runner='${target.runner ?: "?"}'). See HANDOFF.md §7 step 3."
    )

    // ------------------------------------------------------------------ plumbing

    private fun stageAsset(transport: Transport, assetName: String, destName: String): String? {
        val bytes = runCatching { context.assets.open(assetName).use { it.readBytes() } }.getOrNull()
            ?: return null
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val tmp = "$workRoot/$destName.stage"
        val sh = "mkdir -p $workRoot && echo $b64 | base64 -d > $tmp && chmod 755 $tmp && mv -f $tmp $workRoot/$destName"
        val proc = transport.start(
            listOf("/system/bin/sh", "-c", sh),
            listOf("PATH=/system/bin:/system/xbin", "HOME=/"), "/"
        ) ?: return null
        proc.stdout.bufferedReader().readText()
        proc.waitFor()
        return if (File(workRoot, destName).exists()) "$workRoot/$destName" else null
    }

    private fun exec(
        bin: String,
        args: List<String>,
        env: List<String>,
        dir: String,
        transport: Transport,
        onLine: (LogLine) -> Unit,
        successMarker: String,
    ): RunResult {
        val cmd = listOf(bin) + args
        val proc = transport.start(cmd, env, dir)
            ?: return RunResult.NeedsTransport("transport refused to start ${cmd.first()}")
        val code = stream(proc, onLine)
        val ok = markerPresent(successMarker, onLine)
        return if (code == 0 && ok) RunResult.Success(code, successMarker)
        else RunResult.Failure(code, "exit=$code marker='$successMarker' present=$ok")
    }

    private fun markerPresent(marker: String, onLine: (LogLine) -> Unit): Boolean {
        // The marker is read from the captured log, not from a second run.
        return captured.getOrDefault(marker, false)
    }

    private val captured = mutableMapOf<String, Boolean>()

    private fun stream(proc: PrivProcess, onLine: (LogLine) -> Unit): Int {
        val seen = mutableSetOf<String>()
        val t = Thread {
            runCatching {
                proc.stdout.bufferedReader().forEachLine { raw ->
                    val kind = when {
                        raw.contains("FAIL") || raw.startsWith("ERROR") -> LogLine.Kind.FAIL
                        raw.contains("LATE_LOAD_RC=0") || raw.contains("KSU_LOADED") -> LogLine.Kind.OK
                        raw.startsWith("<s>") || raw.startsWith("<b>") -> LogLine.Kind.STEP
                        else -> LogLine.Kind.OUT
                    }
                    for (m in MARKERS) if (raw.contains(m)) captured[m] = true
                    onLine(LogLine(raw, kind))
                }
            }
        }.apply { isDaemon = true; start() }
        runCatching {
            proc.stderr.bufferedReader().forEachLine { onLine(LogLine(it, LogLine.Kind.ERR)) }
        }
        val code = proc.waitFor()
        runCatching { t.join(3000) }
        return code
    }

    companion object {
        /** Success markers used by the upstream runners. */
        val MARKERS = listOf(
            "LATE_LOAD_RC=0", "KSU_LOADED", "ROOT_OK", "GHOSTLOCK_OK",
            "DF_ROOT_OK", "PLANT_CRED_OK", "MODULE_STATE=Live",
        )
    }
}

private fun Context.packageManagerHas(pkg: String): Boolean = runCatching {
    packageManager.getPackageInfo(pkg, 0); true
}.getOrDefault(false)
