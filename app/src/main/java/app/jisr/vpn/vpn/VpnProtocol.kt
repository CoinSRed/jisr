package app.jisr.vpn.vpn

/** ثوابت التواصل بين الواجهة وخدمة الـVPN (تعمل في عمليتين مختلفتين). */
object VpnProtocol {
    const val ACTION_START = "app.jisr.vpn.action.START"
    const val ACTION_STOP = "app.jisr.vpn.action.STOP"
    const val ACTION_QUERY = "app.jisr.vpn.action.QUERY"
    const val ACTION_STATUS = "app.jisr.vpn.action.STATUS"

    const val EXTRA_CONFIG = "config"
    const val EXTRA_DNS = "dns"
    const val EXTRA_STATUS = "status"
    const val EXTRA_ERROR = "error"
    const val EXTRA_SINCE = "since"
}
