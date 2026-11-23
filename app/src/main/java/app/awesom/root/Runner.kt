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

    private fun nativeDirList(dir: String): String = runCatching {
         File(dir).listFiles()?.filter { it.name.endsWith(".so") }?.joinToString(", ") { it.name }
             ?.takeIf { it.isNotBlank() } ?: "(none)"
     }.getOrDefault("(unreadable)")

     /** Stages a bundled asset into the loader work dir by piping its bytes through
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
     *
     * The env list mirrors `JailbreakCore.buildEnv()` in the Shark fork except
     * for two deliberate differences:
     *  - `PYB110_KSUD` is always set (upstream leaves it to its own resolver).
     *    Without it the runner prints `LATE_LOAD_SKIP=env-missing` and returns 0
     *    **without loading anything**, which reads as success.
     *  - `PYB110_FILES_DIR` points at the shell-writable work dir, not the app's
     *    private `filesDir` (mode 0700, unwritable by the shell uid the runner
     *    executes as).
     */
    private fun runShark(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        // The index stores the runner engine ("CVE66127"); the shipped file is
        // lib<MODEL>.so, where MODEL is the digits of the CVE id (libk66127.so),
        // or the full name for the 6.18 plant-core variants (libk61821p.so).
        // Mirrors JailbreakCore.runnerLib() in the Shark fork.
        val engine = target.runner
            ?: return RunResult.Failure(-1, "no runner in index")
        val lib = sharkRunnerLib(engine, p) ?: return RunResult.Failure(
            -1, "no bundled runner for engine '$engine'"
        )
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, lib)
        if (!bin.isFile) {
            return RunResult.Failure(
                -1,
                "$lib is not bundled in this APK. Available runners in this build: " +
                    nativeDirList(nativeDir)
            )
        }

        // The runner stages ksud straight out of the installed manager's APK.
        // No manager copy on disk means the late-load can never happen, so say
        // that instead of running an exploit that will do nothing.
        val ksud = ksudPath(managerPkg)
            ?: return RunResult.Failure(
                -1,
                "$managerPkg does not ship nativeLibraryDir/libksud.so — " +
                    "install/enable KernelSU first; without ksud the loader cannot late-load."
            )

        val kind = managerRuntime(managerPkg)
        val gate = isGateKernel(engine, p.kernelRelease)
        val universal = kind in UNIVERSAL_KINDS
        val chase = p.isVivoOem() || engine.equals("CVE66127", true) ||
            engine.equals("CVE61223", true)

        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin",
            "HOME=/",
            "PYB110_DIR=$workRoot",
            "PYB110_KMI=$kmi",
            "PYB110_MANAGER=$kind",
            "PYB110_MANAGER_PKG=$managerPkg",
            "PYB110_KSUD=$ksud",
            "PYB110_LATE_LOAD=1",
            "PYB110_GATE=${if (gate) 1 else 0}",
            "PYB110_UNLOAD_GUARD=0",
            "PYB110_DISABLE_ANTIROOT=0",
            "PYB110_SUSFS=0",
            "PYB110_VFS=0",
            "PYB110_CHASE=${if (chase) 1 else 0}",
            "PYB110_UNIVERSAL=${if (universal) 1 else 0}",
            "PYB110_UNIVERSAL_KSUD=$nativeDir/libksud_universal.so",
            "PYB110_FILES_DIR=$workRoot",
            "PYB110_SAFE_MODE=0",
        )
        onLine(LogLine("runner  $lib  kmi=$kmi  manager=$kind  ksud=$ksud", LogLine.Kind.STEP))
        onLine(LogLine("gate=$gate universal=$universal chase=$chase", LogLine.Kind.STEP))
        return exec(bin.absolutePath, listOf("run"), env, workRoot, transport, onLine,
            successMarker = "LATE_LOAD_RC=0")
    }

    /**
     * Engine id -> the .so name in jniLibs. Mirrors
     * `JailbreakCore.runnerLib()`: `libk<digits>.so`, with a dedicated
     * plant-core variant for 6.18.21 builds and an x-variant for 6.12.58.
     */
    private fun sharkRunnerLib(engine: String, p: DeviceProfile): String? {
        val digits = Regex("""(?i)CVE0*(\d+)""").find(engine)?.groupValues?.get(1)
            ?: engine.removePrefix("lib").removeSuffix(".so")
        val e = engine.uppercase()
        return when {
            e == "CVE61821" ->
                if (p.kernelRelease in PLANT_CORE_61821) {
                    if (p.isVivoOem()) "libk61821p.so" else "libk61821x.so"
                } else "libk61821.so"
            e == "CVE61258" -> if (p.isVivoOem()) "libk61258.so" else "libk61258x.so"
            else -> "libk${digits.lowercase()}.so"
        }
    }

    /** The manager's own `libksud.so`, which the runner executes as ksud. */
    private fun ksudPath(managerPkg: String): String? = runCatching {
        val app = context.packageManager.getApplicationInfo(managerPkg, 0)
        val f = File(app.nativeLibraryDir ?: return null, "libksud.so")
        if (f.isFile) f.absolutePath else null
    }.getOrNull()

    /** Package -> the `PYB110_MANAGER` value the runner understands. */
    private fun managerRuntime(pkg: String): String = when (pkg) {
        "com.rifsxd.ksunext" -> "next"
        "kqvdie.zeykvw.hpbcyd" -> "next_spoofed"
        "com.resukisu.resukisu" -> "bakasu"
        "com.sukisu.ultra" -> "sukisu"
        "me.yuki.fina" -> "finasu"
        else -> "kernelsu"
    }

    /** Mirrors `JailbreakCore.isGateKernel()`. */
    private fun isGateKernel(engine: String, release: String): Boolean =
        engine.equals("CVE66127", true) ||
            release == "6.18.21-android17-5-g3977fbeb3462-abogki563184321-4k" ||
            (engine.equals("CVE61258", true) &&
                (release.contains("gdd6fd5c98918") || release.contains("g690108013dcc")))

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
        val staged = stageAsset(transport, asset, "rmp-" + asset.substringAfterLast('/'))
            ?: File(context.applicationInfo.nativeLibraryDir, asset.substringAfterLast('/'))
                .takeIf { it.isFile }?.absolutePath
            ?: return RunResult.Failure(-1, "missing $asset")
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

    private fun exec(
        bin: String,
        args: List<String>,
        env: List<String>,
        dir: String,
        transport: Transport,
        onLine: (LogLine) -> Unit,
        successMarker: String,
    ): RunResult {
        // The transport passes `dir` as the child's cwd; a missing directory
        // makes the start fail outright, and /data/local/tmp is not writable by
        // the app's own uid, so it has to be created through the transport.
        ensureWorkDir(dir, transport)
        captured.clear()
        val cmd = listOf(bin) + args
        val proc = transport.start(cmd, env, dir)
            ?: return RunResult.NeedsTransport("transport refused to start ${cmd.first()}")
        val code = stream(proc, onLine)
        val ok = markerPresent(successMarker, onLine)
        return if (code == 0 && ok) RunResult.Success(code, successMarker)
        else RunResult.Failure(
            code,
            "exit=$code marker='$successMarker' present=$ok" +
                if (captured.containsKey("LATE_LOAD_SKIP"))
                    " — the runner skipped the late-load (env-missing)"
                else ""
        )
    }

    private var workDirReady = false

    private fun ensureWorkDir(dir: String, transport: Transport) {
        if (workDirReady) return
        val p = transport.start(
            listOf("/system/bin/sh", "-c", "mkdir -p $dir && chmod 700 $dir"),
            listOf("PATH=/system/bin:/system/xbin", "HOME=/"), "/"
        ) ?: return
        runCatching { p.stdout.bufferedReader().readText() }
        p.waitFor()
        workDirReady = true
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
                        raw.contains("SKIP=") || raw.contains("KSUD_MISSING") ||
                            Regex("""LATE_LOAD_RC=[1-9]""").containsMatchIn(raw) -> LogLine.Kind.FAIL
                        raw.contains("LATE_LOAD_RC=0") || raw.contains("KSU_LOADED") -> LogLine.Kind.OK
                        raw.startsWith("<s>") || raw.startsWith("<b>") -> LogLine.Kind.STEP
                        else -> LogLine.Kind.OUT
                    }
                    for (m in MARKERS) if (raw.contains(m)) captured[m] = true
                    if (raw.contains("LATE_LOAD_SKIP")) captured["LATE_LOAD_SKIP"] = true
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

        /** Engines that can late-load the universal ksud payload.
         *  Mirrors `JailbreakCore.supportsUniversal()`. */
        val UNIVERSAL_KINDS = setOf(
            "CVE61821", "CVE61269", "CVE61238", "CVE61230", "CVE61258", "CVE61223",
        )

        /** 6.18.21 builds that need the dedicated plant-core runner. */
        val PLANT_CORE_61821 = setOf(
            "6.18.21-android17-5-g284297031c53-abogki555938958-4k",
            "6.18.21-android17-5-g3977fbeb3462-abogki563184321-4k",
            "6.18.21-android17-5-g1d099fcb35e0-abogki540753930-4k",
        )
    }
}

private fun DeviceProfile.isVivoOem(): Boolean =
    manufacturer.equals("vivo", true) || manufacturer.equals("iQOO", true) ||
        brand.equals("vivo", true) || brand.equals("iQOO", true)

private fun Context.packageManagerHas(pkg: String): Boolean = runCatching {
    packageManager.getPackageInfo(pkg, 0); true
}.getOrDefault(false)
