package app.jisr.vpn.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** يعرف نوع شبكة الجوال الحقيقية (Wi-Fi / 5G / 4G ...) حتى لو الـVPN شغّال. */
object NetworkInfo {

    fun label(ctx: Context): String {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // نتجاهل شبكة الـVPN نفسها ونبحث عن الشبكة الفعلية تحتها
            val caps = cm.allNetworks
                .mapNotNull { cm.getNetworkCapabilities(it) }
                .filter {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                }
            val chosen = caps.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                ?: caps.firstOrNull()
                ?: return "بدون شبكة"
            when {
                chosen.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                chosen.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> cellular(ctx)
                chosen.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "شبكة"
            }
        } catch (e: Exception) {
            "شبكة"
        }
    }

    @Suppress("MissingPermission")
    private fun cellular(ctx: Context): String {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "بيانات الجوال"
        }
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "بيانات الجوال"
            }
        } catch (e: SecurityException) {
            "بيانات الجوال"
        }
    }
}
