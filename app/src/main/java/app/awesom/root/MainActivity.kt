package app.awesom.root

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * One screen: device facts, the ranked candidate loaders, a run button and the
 * streamed log. Deliberately plain Views (no Compose) so the build has no
 * compose-plugin dependency to fail on.
 */
class MainActivity : ComponentActivity() {

    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var deviceText: TextView

    private var profile: DeviceProfile = DeviceProfile()
    private var transport: Transport? = null
    private var manager: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        log = TextView(this).apply {
            textSize = 11f
            setPadding(24, 24, 24, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scroll.addView(log)
        setContentView(scroll)

        status = TextView(this)
        deviceText = TextView(this)

        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }

        lifecycleScope.launch(Dispatchers.IO) { bootstrap() }
    }

    private suspend fun bootstrap() {
        val loaded = IndexStore.load(this)
        val selector = TransportSelector(this)
        transport = selector.pick()
        manager = selector.findManager()
        profile = DeviceProbe.local()
        profile = DeviceProbe.withShell(profile, transport)

        withContext(Dispatchers.Main) {
            append("index loaded: ${IndexStore.loaders().size} loaders, ${IndexStore.targets().size} targets (ok=$loaded)\n")
            append("transport: ${transport?.label ?: "NONE"}\n")
            append("manager: ${manager ?: "none installed"}\n")
            append("device: ${profile.marketName}\n")
            append("model=${profile.model} device=${profile.device}\n")
            append("kernel: ${profile.kernelRelease}\n")
            append("page=${profile.pageSize} selinux=${profile.selinux} root=${profile.rootActive}\n")
            append("kmi: ${IndexStore.kmiFor(profile.kernelRelease) ?: "?"}\n")
            append("\n")
            val cands = IndexStore.candidates(profile)
            if (cands.isEmpty()) {
                append("no candidate loader for this kernel in the index.\n")
                append("Kernel is '${profile.matchKey}'. Nothing will be run.\n")
                return@withContext
            }
            append("candidates (${cands.size}):\n")
            for ((l, t) in cands.take(8)) {
                append(
                    "  %4d  %-22s %-12s %s\n".format(
                        t.score(profile), l.id, t.matchKind,
                        t.note ?: t.kernel ?: t.matchKey
                    )
                )
            }
            append("\nBest match: ${cands.first().first.name} (${cands.first().second.matchKind})\n")
            append("(run button wiring: tap-to-run is in the next iteration; this build is the detector.)\n")
        }
    }

    private fun refresh() {
        lifecycleScope.launch(Dispatchers.IO) {
            transport = TransportSelector(this@MainActivity).pick()
            withContext(Dispatchers.Main) {
                append("[transport] now ${transport?.label ?: "NONE"}\n")
            }
        }
    }

    private fun append(s: String) {
        log.append(s)
    }
}
