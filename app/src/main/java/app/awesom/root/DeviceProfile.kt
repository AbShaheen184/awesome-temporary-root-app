package app.awesom.root

import android.os.Build

/**
 * Everything the app can read about the device without privilege, plus the
 * privileged reads the [Transport] fills in.
 *
 * The matching rules upstream are strict: a loader is tied to the exact
 * `uname -r` string (and often the userspace page size), never to a device
 * name alone. Keep every field that upstream tables key on.
 */
data class DeviceProfile(
    val manufacturer: String = Build.MANUFACTURER.orEmpty(),
    val brand: String = Build.BRAND.orEmpty(),
    val model: String = Build.MODEL.orEmpty(),
    val device: String = Build.DEVICE.orEmpty(),
    val product: String = Build.PRODUCT.orEmpty(),
    val fingerprint: String = Build.FINGERPRINT.orEmpty(),
    val buildDisplay: String = "",
    val buildIncremental: String = "",
    val buildDateUtc: String = "",
    val androidRelease: String = Build.VERSION.RELEASE.orEmpty(),
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val securityPatch: String = Build.VERSION.SECURITY_PATCH.orEmpty(),
    val kernelRelease: String = "",
    val kernelVersion: String = "",
    val pageSize: Int = 4096,
    val abi: String = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    val selinux: String = "",
    val rootActive: Boolean = false,
) {
    val marketName: String
        get() = resolveMarketName()

    /** The string every upstream support table matches on. */
    val matchKey: String get() = kernelRelease.ifBlank { kernelVersion.substringBefore(" (") }

    private fun resolveMarketName(): String {
        val candidates = when (manufacturer.lowercase()) {
            "xiaomi", "redmi", "poco" -> listOf("ro.product.marketname")
            "oppo", "oneplus", "realme", "oplus" -> listOf("ro.vendor.oplus.market.name")
            "vivo" -> listOf("ro.vivo.market.name")
            "honor", "huawei" -> listOf("ro.config.marketing_name")
            "samsung" -> listOf("ro.product.market.name", "ro.vendor.product.market_name")
            else -> listOf("ro.product.marketname")
        }
        for (key in candidates) {
            val v = readProp(key)
            if (v.isNotBlank() && !v.equals("null", true) && v != Build.UNKNOWN) return v
        }
        return buildString {
            append(manufacturer)
            if (!brand.equals(manufacturer, true)) append(" ").append(brand)
            append(" ").append(model)
        }
    }

    private fun readProp(key: String): String = runCatching {
        val p = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out
    }.getOrDefault("")

    /** True when every field a strict upstream match needs is present. */
    fun isComplete(): Boolean = kernelRelease.isNotBlank() && model.isNotBlank()
}
