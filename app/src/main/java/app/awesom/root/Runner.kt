package app.awesom.root

import android.content.Context
import java.io.File

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

    /** Stages a payload into the loader work dir by piping its bytes through
     * the transport as base64. Returns the staged path, or null.
     *
     * [assetName] is the name (or, for the small configs still bundled, the
     * asset path) the payload is known by; the payloads themselves now live in
     * `payloads/` in the repo and are downloaded on first use by [PayloadStore].
     */
    private fun stageAsset(
        transport: Transport,
        assetName: String,
        destName: String,
        onLine: (LogLine) -> Unit,
    ): String? {
        val bytes = PayloadStore.bytes(context, assetName) { msg ->
            onLine(LogLine(msg, LogLine.Kind.STEP))
        } ?: return null
        val dest = "$workRoot/$destName"
        val part = "$dest.b64"
        val dir = dest.substringBeforeLast('/')
        /*
         * The bytes go through the transport as base64 in several appends:
         * MAX_ARG_STRLEN is 128 KiB per argv entry, so the 2.1 MB `timer`
         * binary and the per-device exploit .so files cannot go in one echo.
         * The app's own uid cannot read /data/local/tmp back, so the staged
         * path is returned as soon as the shell steps succeed.
         *
         * Each slice is encoded on its own (RAW_CHUNK is a multiple of 3, so no
         * padding in the middle): encoding the 12.6 MB ksud payload in one go
         * would hold ~80 MB of byte array, base64 string and chunks at once.
         */
        if (bytes.size > 1 shl 20) {
            onLine(LogLine(
                "staging ${assetName.substringAfterLast('/')} " +
                    "(${(bytes.size + 1023) / 1024} KiB) through the transport…",
                LogLine.Kind.STEP
            ))
        }
        var off = 0
        var first = true
        if (bytes.isEmpty()) {
            if (!shOk(transport, "mkdir -p $dir && : > $part")) return null
        }
        while (off < bytes.size) {
            val end = minOf(off + RAW_CHUNK, bytes.size)
            val chunk = android.util.Base64.encodeToString(
                bytes, off, end - off, android.util.Base64.NO_WRAP
            )
            val op = if (first) ">" else ">>"
            if (!shOk(transport, "mkdir -p $dir && printf %s '$chunk' $op $part")) return null
            first = false
            off = end
        }
        return if (shOk(transport, "base64 -d $part > $dest && chmod 755 $dest && rm -f $part")) dest
        else null
    }

    private fun shOk(transport: Transport, script: String): Boolean {
        val proc = transport.start(
            listOf("/system/bin/sh", "-c", script),
            listOf("PATH=/system/bin:/system/xbin", "HOME=/"), "/"
        ) ?: return false
        runCatching { proc.stdout.bufferedReader().readText() }
        return proc.waitFor() == 0
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
        if (managerPkg.isNotBlank() && !context.packageManagerHas(managerPkg)) {
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
            -1, "no runner for engine '$engine'"
        )
        // The runner and the universal ksud payload used to sit in jniLibs.
        // Neither is shipped in the APK any more, and API 29+ cannot exec a
        // file in the app's home directory, so both are downloaded (if needed)
        // and staged into the shell-writable work dir before the run.
        val bin = stageAsset(transport, lib, lib, onLine)
            ?: return RunResult.Failure(
                -1,
                "cannot get payloads/$lib (not cached and the download failed)"
            )
        val kind = managerRuntime(managerPkg)
        val universal = kind in UNIVERSAL_KINDS
        val universalKsud = if (universal) {
            stageAsset(transport, "libksud_universal.so", "libksud_universal.so", onLine)
                ?: return RunResult.Failure(
                    -1,
                    "cannot get payloads/libksud_universal.so for engine '$engine'"
                )
        } else "$workRoot/libksud_universal.so"

        // The runner stages ksud straight out of the installed manager's APK.
        // No manager copy on disk means the late-load can never happen, so say
        // that instead of running an exploit that will do nothing.
        val ksud = ksudPath(managerPkg)
            ?: return RunResult.Failure(
                -1,
                "$managerPkg does not ship nativeLibraryDir/libksud.so — " +
                    "install/enable KernelSU first; without ksud the loader cannot late-load."
            )

        val gate = isGateKernel(engine, p.kernelRelease)
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
            "PYB110_UNIVERSAL_KSUD=$universalKsud",
            "PYB110_FILES_DIR=$workRoot",
            "PYB110_SAFE_MODE=0",
        )
        onLine(LogLine("runner  $lib  kmi=$kmi  manager=$kind  ksud=$ksud", LogLine.Kind.STEP))
        onLine(LogLine("gate=$gate universal=$universal chase=$chase", LogLine.Kind.STEP))
        return exec(bin, listOf("run"), env, workRoot, transport, onLine,
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
     * TimerRoot: the exploit is the `timer` binary plus a HOCON
     * `conf/<release>.conf` profile. Its own CLI is
     *
     *     timer --conf <conf> [--protocol N] [--samsung] [shell|fake|…]
     *
     * and the app talks to it at protocol 3 (`APP_PROTOCOL` in
     * `p7a/timer_p7a.c`). The generic stage is `shell`; success is the binary
     * printing `ROOT_SHELL_OK` or a `uid=0(root) … context=…` line. It parks
     * after success instead of exiting, exactly like upstream, which is why
     * this branch waits for the marker instead of for the process to end.
     */
    private fun runTimerRoot(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val confName = target.runner ?: return RunResult.Failure(-1, "no profile in index")
        val base = confName.substringAfterLast('/')
        val confAsset = "timerroot/conf/$base"
        val samsung = runCatching {
            context.assets.open(confAsset).bufferedReader().use { it.readText() }
        }.getOrNull()?.let { SAMSUNG_MODE.containsMatchIn(it) } == true
        if (samsung) {
            return RunResult.Failure(
                -1,
                "'$base' is a Samsung profile: it needs the diabl0w/KernelSU Samsung " +
                    "build and the --samsung chain, which is not wired yet."
            )
        }
        val conf = stageAsset(transport, confAsset, "conf/$base", onLine)
            ?: return RunResult.Failure(-1, "missing $confAsset in the APK assets")
        val bin = stageAsset(transport, "timerroot/timer", "timer", onLine)
            ?: return RunResult.Failure(
                -1, "cannot get payloads/timer (not cached and the download failed)"
            )
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/", "TIMER_KMI=$kmi",
        )
        onLine(LogLine("runner  timer  conf=$base  kmi=$kmi", LogLine.Kind.STEP))
        return execUntilMarker(
            "/system/bin/sh",
            listOf("-c", "$bin --conf $conf --protocol $TIMER_PROTOCOL shell 2>&1"),
            env, workRoot, transport, onLine,
            markers = listOf("ROOT_SHELL_OK", "uid=0(root)"),
            timeoutMs = TIMER_TIMEOUT_MS,
        )
    }

    /**
     * Root My Pixel: the per-build `exploits/<codename>-<build>.so` is not
     * exec'd directly. Upstream hands its bytes to a small loader binary
     * (`libcve43499root.so`, shipped in that app's jniLibs) which re-execs the
     * payload:
     *
     *     libcve43499root.so --run-payload <exploit.so> <helper> <log path>
     *
     * with `RMG_CLIENT_UID` (the uid running it, 2000 through the transport)
     * and `RMG_APP_UID` (the caller's uid) in the environment. Success is its
     * wire result line: `RMP_PAYLOAD_RESULT:v1 … success=1 reason=OK`, or the
     * legacy `RUN_END … done=1 root=1`.
     */
    private fun runRootMyPixel(
        loader: Loader, target: Target, p: DeviceProfile, transport: Transport,
        managerPkg: String, kmi: String, onLine: (LogLine) -> Unit,
    ): RunResult {
        val asset = target.runner ?: return RunResult.Failure(-1, "no exploit asset in index")
        // The helper used to be a jniLibs entry; it is a downloaded payload now
        // and is staged next to the exploit so the shell uid can exec it.
        val helperName = "libcve43499root.so"
        val helper = stageAsset(transport, helperName, helperName, onLine)
            ?: return RunResult.Failure(
                -1, "cannot get payloads/$helperName (not cached and the download failed)"
            )
        val exploit = stageAsset(transport, asset, "rmp-exploit.so", onLine)
            ?: return RunResult.Failure(-1, "missing $asset in the APK assets")
        val env = listOf(
            "PATH=/system/bin:/system/xbin:/data/adb/ksu/bin", "HOME=/",
            "RMG_CLIENT_UID=2000", "RMG_APP_UID=${context.applicationInfo.uid}",
        )
        onLine(LogLine("runner  ${asset.substringAfterLast('/')}  kmi=$kmi", LogLine.Kind.STEP))
        return exec(
            helper,
            listOf("--run-payload", exploit, helper, "$workRoot/rmp-exploit.log"),
            env, workRoot, transport, onLine,
            successMarker = "RMP_PAYLOAD_RESULT:v1",
            aliasMarkers = listOf("success=1", "root=1"),
        )
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
        aliasMarkers: List<String> = emptyList(),
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
        val hit = (listOf(successMarker) + aliasMarkers).firstOrNull { markerPresent(it, onLine) }
        return if (code == 0 && hit != null) RunResult.Success(code, hit)
        else RunResult.Failure(
            code,
            "exit=$code marker='$successMarker' present=${hit != null}" +
                if (captured.containsKey("LATE_LOAD_SKIP"))
                    " — the runner skipped the late-load (env-missing)"
                else ""
        )
    }

    /** Holds parked runner processes (Shizuku/Process handles) so they stay alive. */
    private val liveShells = mutableListOf<PrivProcess>()

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
        val t = pump(proc, onLine)
        val code = proc.waitFor()
        runCatching { t.join(3000) }
        return code
    }

    /**
     * Starts [bin] and returns as soon as one of [markers] appears in its
     * output. Used for runners that park after success (`timer shell`) instead
     * of exiting: waiting for the exit code would hang the run for minutes.
     * The live process is kept referenced so its Shizuku descriptors stay open.
     */
    private fun execUntilMarker(
        bin: String,
        args: List<String>,
        env: List<String>,
        dir: String,
        transport: Transport,
        onLine: (LogLine) -> Unit,
        markers: List<String>,
        timeoutMs: Long,
    ): RunResult {
        ensureWorkDir(dir, transport)
        captured.clear()
        val proc = transport.start(listOf(bin) + args, env, dir)
            ?: return RunResult.NeedsTransport("transport refused to start $bin")
        pump(proc, onLine)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            markers.firstOrNull { captured.containsKey(it) }?.let {
                synchronized(liveShells) { liveShells += proc }
                onLine(LogLine(
                    "root shell left running (kill or reboot to clean up); marker=$it",
                    LogLine.Kind.OK
                ))
                return RunResult.Success(0, it)
            }
            Thread.sleep(200)
        }
        runCatching { proc.destroy() }
        return RunResult.Failure(-1, "no ${markers.joinToString("/")} within ${timeoutMs / 1000}s")
    }

    private fun pump(proc: PrivProcess, onLine: (LogLine) -> Unit): Thread {
        val out = Thread {
            runCatching {
                proc.stdout.bufferedReader().forEachLine { raw ->
                    val kind = when {
                        raw.contains("FAIL") || raw.startsWith("ERROR") -> LogLine.Kind.FAIL
                        raw.contains("SKIP=") || raw.contains("KSUD_MISSING") ||
                            Regex("""LATE_LOAD_RC=[1-9]""").containsMatchIn(raw) -> LogLine.Kind.FAIL
                        raw.contains("LATE_LOAD_RC=0") || raw.contains("KSU_LOADED") ||
                            raw.contains("ROOT_SHELL_OK") || raw.contains("uid=0(root)") -> LogLine.Kind.OK
                        raw.startsWith("<s>") || raw.startsWith("<b>") -> LogLine.Kind.STEP
                        else -> LogLine.Kind.OUT
                    }
                    for (m in MARKERS) if (raw.contains(m)) captured[m] = true
                    if (raw.contains("LATE_LOAD_SKIP")) captured["LATE_LOAD_SKIP"] = true
                    onLine(LogLine(raw, kind))
                }
            }
        }.apply { isDaemon = true; start() }
        Thread {
            runCatching {
                proc.stderr.bufferedReader().forEachLine { onLine(LogLine(it, LogLine.Kind.ERR)) }
            }
        }.apply { isDaemon = true; start() }
        return out
    }

    companion object {
        /** Success markers used by the upstream runners. */
        val MARKERS = listOf(
            "LATE_LOAD_RC=0", "KSU_LOADED", "ROOT_OK", "GHOSTLOCK_OK",
            "DF_ROOT_OK", "PLANT_CRED_OK", "MODULE_STATE=Live",
            "ROOT_SHELL_OK", "uid=0(root)", "success=1", "root=1",
            "RMP_PAYLOAD_RESULT:v1",
        )

        /**
         * Raw bytes per staged base64 append: a multiple of 3, so every slice
         * encodes to exactly 60_000 chars — under `MAX_ARG_STRLEN` (128 KiB).
         */
        private const val RAW_CHUNK = 45_000

        /** `APP_PROTOCOL` from TimerRoot's `p7a/timer_p7a.c`. */
        private const val TIMER_PROTOCOL = 3

        /** The generic `shell` stage runs up to three plant attempts. */
        private const val TIMER_TIMEOUT_MS = 420_000L

        private val SAMSUNG_MODE = Regex("""(?m)^\s*samsung_mode\s*=\s*1\s*$""")

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
