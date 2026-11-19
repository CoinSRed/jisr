package app.jisr.vpn

import android.app.Application
import app.jisr.vpn.data.Prefs

class JisrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
