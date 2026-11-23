package app.awesom.root.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.awesom.root.R
import app.awesom.root.DeviceProfile
import app.awesom.root.IndexStore
import app.awesom.root.Loader
import app.awesom.root.Target

/**
 * The single screen: device card, transport + manager row, candidate list,
 * run buttons and the streamed log.
 *
 * Built with plain Views and a small helper DSL so it stays readable without a
 * Compose dependency. Colours come from res/values/colors.xml.
 */
class MainScreen(
    private val context: Context,
    private val callbacks: Callbacks,
) : LinearLayout(context) {

    interface Callbacks {
        fun onRun(loader: Loader, target: Target)
        fun onTestAll()
        fun onRefresh()
        fun onPickManager()
        fun onGrantShizuku()
    }

    private val deviceCard: LinearLayout
    private val transportRow: LinearLayout
    private var managerRow: LinearLayout
    private val candidateList: LinearLayout
    private val logView: TextView
    private val logBox: LinearLayout
    private var logScroll: ScrollView
    private var statusDot: TextView = TextView(context)
    private val runButtons: LinearLayout

    var profile: DeviceProfile = DeviceProfile()
        private set
    var transportLabel: String = "—"
        private set
    var manager: String? = null
        private set
    var candidates: List<Pair<Loader, Target>> = emptyList()
        private set
    var busy: Boolean = false
        private set

    init {
        orientation = VERTICAL
        setBackgroundColor(color("bg"))
        setPadding(dp(16), dp(24), dp(16), dp(24))

        addView(titleRow())
        addView(statusCard())
        deviceCard = card().also { addView(it, spaced()) }
        addView(label("RUN METHOD"))
        transportRow = row().also { addView(it, spaced()) }
        addView(label("ROOT MANAGER"))
        managerRow = row()
        addView(managerRow, spaced())
        addView(label("DETECTED LOADERS"))
        candidateList = LinearLayout(context).apply { orientation = VERTICAL }
            .also { addView(it, spaced()) }
        runButtons = row().also { addView(it, spaced()) }
        addView(label("LOG"))
        logView = TextView(context).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(color("text"))
            background = context.getDrawable(logBg())
            setPadding(dp(12), dp(12), dp(12), dp(24))
            setTextIsSelectable(true)
            text = "ready.\n"
        }

        // The log grows with its content (up to 45% of the viewport) inside an
        // inline area; the whole page scrolls, so the tail can never be clipped
        // by a fixed-height box or by the bottom of the screen.
        logBox = LinearLayout(context).apply {
            orientation = VERTICAL
            background = context.getDrawable(logBg())
            setPadding(dp(12), dp(12), dp(12), dp(16))
            addView(
                logView,
                LayoutParams(MATCH_PARENT, WRAP)
            )
        }
        // The log grows with its content, then caps at 45% of the viewport and
        // scrolls inside itself. The whole page also scrolls, so the tail can
        // never be clipped by a fixed height or by the bottom of the screen.
        logScroll = ScrollView(context).apply {
            addView(logBox)
        }
        addView(
            logScroll,
            LayoutParams(MATCH_PARENT, WRAP).apply { topMargin = dp(8) }
        )
        val maxH = (resources.displayMetrics.heightPixels * 0.45f).toInt()
        logBox.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (logBox.height > maxH && logScroll.layoutParams.height != maxH) {
                        logScroll.layoutParams = logScroll.layoutParams.apply { height = maxH }
                    }
                }
            }
        )
        renderManagerRow()
        renderButtons()
    }

    // ------------------------------------------------------------------ data in

    fun setDevice(p: DeviceProfile, transportLabel: String, manager: String?) {
        profile = p
        this.transportLabel = transportLabel
        this.manager = manager
        renderDevice()
        renderTransportRow()
        renderManagerRow()
    }

    fun setCandidates(list: List<Pair<Loader, Target>>) {
        candidates = list
        renderCandidates()
        // The run buttons depend on the candidate list, so they must be rebuilt
        // here too. Without this the row kept the state it was first built with
        // (empty list) and stayed disabled forever.
        renderButtons()
    }

    fun setBusy(b: Boolean) {
        busy = b
        renderButtons()
    }

    fun log(line: String, kind: LogLineKind = LogLineKind.OUT) {
        val c = when (kind) {
            LogLineKind.OK -> color("ok")
            LogLineKind.FAIL -> color("bad")
            LogLineKind.STEP -> color("accent")
            else -> color("text")
        }
        logView.append(line + "\n")
        // Colour only the tail so the whole log is not re-rendered every line.
        val s = logView.text
        val start = s.length - (line.length + 1)
        if (start >= 0) {
            val sp = android.text.SpannableString(s)
            sp.setSpan(android.text.style.ForegroundColorSpan(c), start, s.length, 0)
            logView.text = sp
        }
        // Auto-scroll the capped box to the newest line.
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------------ renderers

    private fun renderDevice() {
        deviceCard.removeAllViews()
        val p = profile
        deviceCard.addView(big(p.marketName.ifBlank { p.model }))
        deviceCard.addView(sub("model ${p.model}  ·  codename ${p.device}"))
        deviceCard.addView(gap())
        deviceCard.addView(kv("Kernel (uname -r)", p.kernelRelease.ifBlank { "unknown" }))
        deviceCard.addView(kv("KMI", IndexStore.kmiFor(p.kernelRelease) ?: "—"))
        deviceCard.addView(kv("Page size", "${p.pageSize} B"))
        deviceCard.addView(kv("Build", p.buildDisplay.ifBlank { p.fingerprint }))
        deviceCard.addView(kv("Android", "${p.androidRelease}  ·  API ${p.sdkInt}  ·  SPL ${p.securityPatch}"))
        deviceCard.addView(kv("ABI", p.abi))
        deviceCard.addView(kv("SELinux", p.selinux.ifBlank { "unknown" }))
        deviceCard.addView(kv("Root now", if (p.rootActive) "active" else "not loaded"))
    }

    private fun renderManagerRow() {
        managerRow.removeAllViews()
        managerRow.addView(TextView(context).apply {
            text = manager?.let { "installed: $it" } ?: "no KernelSU manager detected — install one first"
            setTextColor(if (manager != null) color("ok") else color("warn"))
            textSize = 12f
        }, 0, LayoutParams(0, WRAP, 1f))
        managerRow.addView(smallButton("CHANGE") { callbacks.onPickManager() })
        val granted = runCatching {
            rikka.shizuku.Shizuku.pingBinder() &&
                (rikka.shizuku.Shizuku.checkSelfPermission() ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    // checkSelfPermission() returns DENIED until the Shizuku
                    // manager UI has shown this client once, even when the
                    // service itself would allow newProcess. The real test is
                    // the binder uid: shell uid means the grant is live.
                    rikka.shizuku.Shizuku.getUid() == android.os.Process.SHELL_UID)
        }.getOrDefault(false)
        managerRow.addView(
            smallButton(if (granted) "SHIZUKU OK" else "GRANT SHIZUKU") { callbacks.onGrantShizuku() }
        )
    }

    private fun renderTransportRow() {
        transportRow.removeAllViews()
        val priv = transportLabel != "—" && transportLabel != "NONE"
        transportRow.addView(pill(if (priv) "privileged" else "direct only", if (priv) "ok" else "warn"))
        transportRow.addView(pill(transportLabel, if (priv) "accent" else "warn"))
        if (manager != null) transportRow.addView(pill(manager!!, "ok"))
    }

    private fun renderCandidates() {
        candidateList.removeAllViews()
        if (candidates.isEmpty()) {
            candidateList.addView(sub("No loader matches this kernel. Nothing will be run."))
            return
        }
        candidates.take(6).forEachIndexed { i, (l, t) ->
            val row = LinearLayout(context).apply {
                orientation = VERTICAL
                background = context.getDrawable(cardBg())
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val top = LinearLayout(context).apply { orientation = HORIZONTAL }
            top.addView(TextView(context).apply {
                text = l.name
                setTextColor(color("text"))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            }, 0, LayoutParams(0, WRAP, 1f))
            top.addView(TextView(context).apply {
                text = t.matchKind
                setTextColor(color("text_dim"))
                textSize = 12f
            })
            row.addView(top)
            row.addView(TextView(context).apply {
                text = listOfNotNull(l.cve, t.matchKind, t.kernel ?: t.note).joinToString("  ·  ")
                setTextColor(color("text_dim"))
                textSize = 11f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (i == 0) {
                row.addView(TextView(context).apply {
                    text = "best match"
                    setTextColor(color("ok"))
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                })
            }
            candidateList.addView(row, LayoutParams(MATCH_PARENT, WRAP).apply { topMargin = dp(6) })
        }
    }

    private fun renderButtons() {
        runButtons.removeAllViews()
        val canRun = candidates.isNotEmpty() && !busy
        android.util.Log.d(
            "AwesomRoot",
            "renderButtons: candidates=${candidates.size} busy=$busy canRun=$canRun"
        )
        runButtons.addView(button("RUN BEST MATCH", primary = true, enabled = canRun) {
            candidates.firstOrNull()?.let { callbacks.onRun(it.first, it.second) }
        }, 0, LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        runButtons.addView(
            button("TEST ALL", primary = false, enabled = canRun && candidates.size > 1) { callbacks.onTestAll() },
            LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) })
        runButtons.addView(button("REFRESH", primary = false, enabled = !busy) { callbacks.onRefresh() })
    }

    // ------------------------------------------------------------------ widgets

    private fun titleRow(): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        val t = TextView(context).apply {
            text = "AwesomRoot"
            setTextColor(color("text"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(t, 0, LayoutParams(0, WRAP, 1f))
        statusDot = TextView(context).apply {
            text = "· idle"
            setTextColor(color("text_dim"))
            textSize = 12f
        }
        row.addView(statusDot)
        return row
    }

    private fun statusCard(): View {
        val c = card()
        c.orientation = HORIZONTAL
        c.addView(TextView(context).apply {
            text = "Temporary root on a locked bootloader, one boot at a time.\n" +
                "Match is on the exact kernel release — never a near match."
            setTextColor(color("text_dim"))
            textSize = 12f
        }, 0, LayoutParams(0, WRAP, 1f))
        return c
    }

    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        background = context.getDrawable(cardBg())
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun label(s: String) = TextView(context).apply {
        text = s
        setTextColor(color("text_dim"))
        textSize = 11f
        letterSpacing = 0.12f
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun big(s: String) = TextView(context).apply {
        text = s
        setTextColor(color("text"))
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun sub(s: String) = TextView(context).apply {
        text = s
        setTextColor(color("text_dim"))
        textSize = 12f
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun kv(k: String, v: String): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(TextView(context).apply {
            text = k
            setTextColor(color("text_dim"))
            textSize = 12f
        }, 0, LayoutParams(0, WRAP, 1f))
        row.addView(TextView(context).apply {
            text = v
            setTextColor(color("text"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
        }, LayoutParams(0, WRAP, 1f))
        return row
    }

    private fun pill(text: String, tone: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color(tone))
        textSize = 10f
        background = context.getDrawable(pillBg(tone))
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun button(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit): Button =
        Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(color(if (primary) "bg" else "text"))
            background = context.getDrawable(if (primary) btnPrimary() else btnSecondary())
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            setOnClickListener { onClick() }
        }

    private fun smallButton(text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 11f
        setTextColor(color("accent"))
        background = context.getDrawable(btnSecondary())
        setOnClickListener { onClick() }
    }

    private fun gap() = View(context).apply { minimumHeight = dp(4) }

    private fun spaced() = LayoutParams(MATCH_PARENT, WRAP).apply { topMargin = dp(10) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /**
     * Colours and drawables are referenced by R.* directly. `getIdentifier` is
     * not usable here: it resolves against the app package and returns 0 for
     * anything it cannot see, which silently made every view transparent.
     */
    private fun color(name: String) = when (name) {
        "bg" -> R.color.bg
        "surface" -> R.color.surface
        "surface_alt" -> R.color.surface_alt
        "line" -> R.color.line
        "text" -> R.color.text
        "text_dim" -> R.color.text_dim
        "accent" -> R.color.accent
        "ok" -> R.color.ok
        "warn" -> R.color.warn
        "bad" -> R.color.bad
        else -> R.color.text
    }.let { context.getColor(it) }

    private fun cardBg() = R.drawable.card_bg
    private fun logBg() = R.drawable.log_bg
    private fun btnPrimary() = R.drawable.btn_primary_bg
    private fun btnSecondary() = R.drawable.btn_secondary_bg
    private fun pillBg(tone: String) = when (tone) {
        "ok" -> R.drawable.pill_ok
        "warn" -> R.drawable.pill_warn
        else -> R.drawable.pill_accent
    }

    enum class LogLineKind { OUT, OK, FAIL, STEP }

    /**
     * Wraps this screen in a ScrollView so the whole page scrolls. The screen
     * itself is a vertical LinearLayout, which does nothing on its own when the
     * content is taller than the viewport.
     */
    companion object {
        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun scrolled(screen: MainScreen): ScrollView = ScrollView(screen.context).apply {
            isFillViewport = true
            addView(screen)
        }
    }
}
