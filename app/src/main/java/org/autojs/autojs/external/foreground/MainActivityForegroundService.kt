package org.autojs.autojs.external.foreground

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import org.autojs.autojs.tool.ForegroundServiceCreator
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.util.ForegroundServiceUtils
import com.tencent.apphelper.R

/**
 * Modified by SuperMonster003 as of Apr 10, 2022.
 * Transformed by SuperMonster003 on May 13, 2023.
 */
class MainActivityForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground()
    }

    override fun onBind(intent: Intent) = null

    private fun startForeground() {
        val foregroundServiceCreator = ForegroundServiceCreator.Builder(this)
            .setClassName(sClassName)
            .setIntent(MainActivity.getIntent(this))
            .setNotificationId(NOTIFICATION_ID)
            .setServiceName(R.string.foreground_notification_channel_name)
            .setServiceDescription(R.string.foreground_notification_channel_name)
            .setNotificationTitle(R.string.foreground_notification_title)
            .setNotificationContent(R.string.foreground_notification_text)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundServiceUtils.startForeground(foregroundServiceCreator, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            ForegroundServiceUtils.startForeground(foregroundServiceCreator)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {

        private const val NOTIFICATION_ID = 0xBF
        private val sClassName = MainActivityForegroundService::class.java

    }

}
