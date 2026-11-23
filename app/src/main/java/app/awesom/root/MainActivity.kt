package app.awesom.root

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.awesom.root.ui.MainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Hosts [MainScreen]: detects the device, ranks the loaders and runs the one the
 * user picks, through a privileged transport or directly.
 */
class MainActivity : ComponentActivity(), MainScreen.Callbacks {

    private lateinit var screen: MainScreen
    private var selector: TransportSelector? = null
    private var running = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { triggerRefresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = MainScreen(this, this)
        setContentView(MainScreen.scrolled(screen))

        Shizuku.addRequestPermissionResultListener { _, _ -> triggerRefresh() }
        Shizuku.addBinderReceivedListenerSticky { triggerRefresh() }
        Shizuku.addBinderDeadListener { triggerRefresh() }

        ensureNotificationPermission()
        triggerRefresh()
    }

    private fun triggerRefresh() {
        lifecycleScope.launch(Dispatchers.IO) { refresh() }
    }

    companion object {
        private const val REQ_SHIZUKU = 1001
    }

    // ------------------------------------------------------------- MainScreen.Callbacks

    override fun onRun(loader: Loader, target: Target) = runOne(loader, target)

    override fun onTestAll() {
        val list = screen.candidates
        if (list.size < 2) {
            screen.log("only one candidate — nothing to test-all.", MainScreen.LogLineKind.STEP)
            return
        }
        screen.log("=== test-all: ${list.size} candidates, best first ===", MainScreen.LogLineKind.STEP)
        list.forEachIndexed { i, (l, t) ->
            screen.log("  ${i + 1}. ${l.name}  (${t.matchKind})")
        }
        screen.log("Running the best match; the rest are listed for reference.")
        runOne(list.first().first, list.first().second)
    }

    override fun onRefresh() {
        lifecycleScope.launch(Dispatchers.IO) { refresh() }
    }

    override fun onPickManager() {
        val installed = TransportSelector.MANAGER_PACKAGES.filter {
            runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess
        }
        screen.log(
            if (installed.isEmpty()) "no KernelSU-family manager installed."
            else "installed managers: " + installed.joinToString(", ")
        )
    }

    override fun onGrantShizuku() = ensureShizukuGrant()

    // ------------------------------------------------------------- detection

    private suspend fun refresh() {
        val s = TransportSelector(this)
        selector = s
        val transport = s.pick()
        val manager = s.findManager()
        val profile = DeviceProbe.withShell(DeviceProbe.local(), transport)

        val ok = IndexStore.load(this)
        val candidates = IndexStore.exactCandidates(profile)

        withContext(Dispatchers.Main) {
            screen.setDevice(profile, transport?.label ?: "NONE", manager)
            screen.setCandidates(candidates)
            screen.log(
                "index ${IndexStore.loaders().size} loaders / ${IndexStore.targets().size} targets (ok=$ok)",
                MainScreen.LogLineKind.STEP
            )
            screen.log(
                "transport: ${transport?.label ?: "none"}  →  " +
                    if (transport != null) "privileged run" else "direct run only"
            )
            screen.log(
                "kernel ${profile.kernelRelease}  ·  kmi ${IndexStore.kmiFor(profile.kernelRelease) ?: "?"}  ·  page ${profile.pageSize}"
            )
            if (candidates.isEmpty()) {
                screen.log("no loader matches this kernel; nothing will run.", MainScreen.LogLineKind.FAIL)
            } else {
                val (l, t) = candidates.first()
                screen.log("best match: ${l.name} (${t.matchKind}, ${l.cve})", MainScreen.LogLineKind.OK)
            }
            if (transport == null && manager == null) {
                screen.log(
                    "no Shizuku/Stellar and no root manager — direct run only. " +
                        "Enable Shizuku over adb for the full loader set.",
                    MainScreen.LogLineKind.FAIL
                )
            }
            // Shizuku may be running without a grant for this app; ask for it
            // rather than leaving the transport silently unusable.
            ensureShizukuGrant()
        }
    }

    /**
     * Asks for the Shizuku grant when the server is up but the app has not been
     * permitted yet. Shizuku only shows a dialog for a client it has never seen,
     * so the first call is the one that matters.
     */
    private fun ensureShizukuGrant() {
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ||
                // checkSelfPermission() can report DENIED before the manager UI has
                // shown this client once, while the service already accepts the
                // binder. Probe the real capability instead of trusting that flag.
                runCatching {
                    val b = Shizuku.getBinder() ?: return@runCatching false
                    val svc = moe.shizuku.server.IShizukuService.Stub.asInterface(b) ?: return@runCatching false
                    svc.newProcess(
                        arrayOf("/system/bin/sh", "-c", "true"), arrayOf("PATH=/system/bin"), "/"
                    ) != null
                }.getOrDefault(false)
        }.getOrDefault(false)

        if (granted) {
            screen.log("Shizuku grant is live (uid ${runCatching { Shizuku.getUid() }.getOrDefault(-1)}).")
            return
        }
        screen.log("Shizuku is running but this app is not granted — requesting permission…")
        screen.log("(tap Allow on the dialog; nothing runs until then)")
        runCatching { Shizuku.requestPermission(REQ_SHIZUKU) }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // ------------------------------------------------------------- execution

    private fun runOne(loader: Loader, target: Target) {
        if (running) {
            screen.log("a run is already in progress", MainScreen.LogLineKind.FAIL)
            return
        }
        val s = selector ?: TransportSelector(this).also { selector = it }
        val transport = s.pick()

        if (transport == null) {
            directRun(loader, target)
            return
        }
        if (Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            screen.log("requesting Shizuku permission…")
            runCatching { Shizuku.requestPermission(1001) }
            return
        }

        running = true
        screen.setBusy(true)
        screen.log("running ${loader.name}  (${target.matchKind})", MainScreen.LogLineKind.STEP)
        val manager = s.findManager() ?: ""
        val app = this@MainActivity
        lifecycleScope.launch(Dispatchers.IO) {
            val result = Runner(app).run(
                loader, target, screen.profile, transport, manager,
            ) { line ->
                // Runner.run is a blocking call, so the callback is not a
                // suspend context; hop to the main thread for the UI.
                mainHandler.post { screen.log(line.text, kindOf(line.kind)) }
            }
            withContext(Dispatchers.Main) {
                running = false
                screen.setBusy(false)
                report(result)
            }
        }
    }

    /**
     * The no-transport path. DirtyFrag's helper opens its own IpSec socket from
     * the app process, so it can be attempted unprivileged; every other loader is
     * reported as needing a transport rather than failing silently.
     */
    private fun directRun(loader: Loader, target: Target) {
        val s = selector ?: TransportSelector(this).also { selector = it }
        running = true
        screen.setBusy(true)
        screen.log("no privileged transport — direct run as the app uid", MainScreen.LogLineKind.STEP)
        val bin = "libdfroot.so"
        val native = applicationInfo.nativeLibraryDir
        lifecycleScope.launch(Dispatchers.IO) {
            val out = when (loader.id) {
                "dirtyfrag", "dirtyfalg-vr", "dfroot", "galaxycrash" ->
                    s.shellDirect("test -x $native/$bin && echo BIN_OK || echo BIN_MISSING")
                else -> -1 to "DIRECT_UNSUPPORTED"
            }
            withContext(Dispatchers.Main) {
                running = false
                screen.setBusy(false)
                screen.log("direct: ${out.second.trim()}", MainScreen.LogLineKind.STEP)
                when {
                    out.second.contains("BIN_MISSING") ->
                        screen.log("$bin is not bundled in this APK yet.", MainScreen.LogLineKind.FAIL)
                    out.second.contains("BIN_OK") ->
                        screen.log(
                            "helper present, but it needs the shell uid. Enable Shizuku over adb and retry.",
                            MainScreen.LogLineKind.FAIL
                        )
                    else ->
                        screen.log(
                            "${loader.name} needs the shell uid — enable Shizuku or run it from adb.",
                            MainScreen.LogLineKind.FAIL
                        )
                }
            }
        }
    }

    private fun report(r: RunResult) {
        when (r) {
            is RunResult.Success -> screen.log("SUCCESS (${r.marker})", MainScreen.LogLineKind.OK)
            is RunResult.Failure -> screen.log("FAILED: ${r.reason}", MainScreen.LogLineKind.FAIL)
            is RunResult.NeedsTransport ->
                screen.log("needs a privileged transport: ${r.reason}", MainScreen.LogLineKind.FAIL)
            is RunResult.NeedsManager ->
                screen.log("install a root manager first (${r.pkg})", MainScreen.LogLineKind.FAIL)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val s = selector ?: return@launch
            val after = DeviceProbe.withShell(DeviceProbe.local(), s.pick())
            withContext(Dispatchers.Main) {
                screen.setDevice(after, s.pick()?.label ?: "NONE", s.findManager())
                if (after.rootActive) {
                    screen.log("KernelSU is live — root active for this boot.", MainScreen.LogLineKind.OK)
                }
            }
        }
    }

    private fun kindOf(k: LogLine.Kind) = when (k) {
        LogLine.Kind.OK -> MainScreen.LogLineKind.OK
        LogLine.Kind.FAIL -> MainScreen.LogLineKind.FAIL
        LogLine.Kind.STEP -> MainScreen.LogLineKind.STEP
        else -> MainScreen.LogLineKind.OUT
    }
}
