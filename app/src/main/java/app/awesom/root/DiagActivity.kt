package app.awesom.root

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Temporary diagnostic screen: reports exactly what the Shizuku binder sees
 * (ping / uid / permission / an actual newProcess) and the local uname read, so a
 * missing grant is distinguishable from a broken transport.
 */
class DiagActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = StringBuilder()

        out.append("sdkInt      : ${android.os.Build.VERSION.SDK_INT}\n")
        out.append("pingBinder  : ${runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrElse { "ERR $it" }}\n")
        out.append("getUid      : ${runCatching { rikka.shizuku.Shizuku.getUid() }.getOrElse { "ERR $it" }}\n")
        out.append("perm        : ${runCatching { rikka.shizuku.Shizuku.checkSelfPermission() }.getOrElse { "ERR $it" }}\n")
        out.append("uid==shell  : ${runCatching { rikka.shizuku.Shizuku.getUid() == android.os.Process.SHELL_UID }.getOrElse { false }}\n")

        val binder = runCatching { rikka.shizuku.Shizuku.getBinder() }.getOrNull()
        out.append("binder      : ${if (binder == null) "null" else "ok"}\n")
        if (binder != null) {
            val svc = runCatching {
                moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
            }.getOrNull()
            out.append("service     : ${if (svc == null) "null" else "ok"}\n")
            if (svc != null) {
                val p = runCatching {
                    svc.newProcess(
                        arrayOf("/system/bin/sh", "-c", "uname -r; getenforce"),
                        arrayOf("PATH=/system/bin", "HOME=/"), "/"
                    )
                }.getOrNull()
                out.append("newProcess  : ${if (p == null) "null (permission denied)" else "ok"}\n")
                if (p != null) {
                    val text = runCatching {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(p.inputStream)
                            .bufferedReader().readText().trim()
                    }.getOrDefault("ERR")
                    out.append("  output    : $text\n")
                    runCatching { p.waitFor() }
                }
            }
        }
        out.append("local uname : ${runCatching {
            val pr = ProcessBuilder("/system/bin/uname", "-r").redirectErrorStream(true).start()
            val t = pr.inputStream.bufferedReader().readText().trim(); pr.waitFor(); t
        }.getOrDefault("ERR")}\n")
        out.append("manager     : ${TransportSelector(this).findManager() ?: "none"}\n")

        val tv = TextView(this).apply {
            text = out.toString()
            textSize = 13f
            setTextColor(0xffe6e8ee.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
        }
        setContentView(ScrollView(this).apply { addView(tv) })
    }
}
