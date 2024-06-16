package org.autojs.autojs

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.LayoutInspector.CaptureAvailableListener
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.execution.ScriptExecutionGlobalListener
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.AppUtils.Companion.ActivityShortForm
import org.autojs.autojs.runtime.api.AppUtils.Companion.BroadcastShortForm
import org.autojs.autojs.ui.doc.DocumentationActivity
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.floating.FullScreenFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutBoundsFloatyWindow
import org.autojs.autojs.ui.floating.layoutinspector.LayoutHierarchyFloatyWindow
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.autojs.ui.settings.PreferencesActivity
import com.tencent.apphelper.BuildConfig
import java.util.concurrent.Executors
import org.autojs.autojs.inrt.autojs.AutoJs as AutoJsInrt

/**
 * Created by Stardust on Apr 2, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on Oct 10, 2022.
 * Modified by LZX284 (https://github.com/LZX284) as of Sep 30, 2023.
 */
open class AutoJs(appContext: Application) : AbstractAutoJs(appContext) {

    // @Thank to Zen2H
    private val mPrintExecutor = Executors.newSingleThreadExecutor()

    private val mA11yTool = AccessibilityTool(appContext)
    private val mA11yToolService = mA11yTool.service

    init {
        scriptEngineService.registerGlobalScriptExecutionListener(ScriptExecutionGlobalListener())

        LocalBroadcastManager.getInstance(appContext).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val action = intent.action ?: return
                    when {
                        action.equals(LayoutBoundsFloatyWindow::class.java.name, true) -> {
                            mA11yToolService.ensure()
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(nodeInfo: NodeInfo?) = LayoutBoundsFloatyWindow(nodeInfo, context, true)
                            })
                        }
                        action.equals(LayoutHierarchyFloatyWindow::class.java.name, true) -> {
                            mA11yToolService.ensure()
                            capture(object : LayoutInspectFloatyWindow {
                                override fun create(nodeInfo: NodeInfo?) = LayoutHierarchyFloatyWindow(nodeInfo, context, true)
                            })
                        }
                    }
                } catch (e: Exception) {
                    if (Looper.myLooper() != Looper.getMainLooper()) {
                        throw e
                    }
                }
            }
        }, IntentFilter().apply {
            addAction(LayoutBoundsFloatyWindow::class.java.name)
            addAction(LayoutHierarchyFloatyWindow::class.java.name)
        })
    }

    private interface LayoutInspectFloatyWindow {
        fun create(nodeInfo: NodeInfo?): FullScreenFloatyWindow?
    }

    private fun capture(window: LayoutInspectFloatyWindow) {
        val inspector = layoutInspector
        val listener: CaptureAvailableListener = object : CaptureAvailableListener {
            override fun onCaptureAvailable(capture: NodeInfo?, context: Context) {
                inspector.removeCaptureAvailableListener(this)
                uiHandler.post { FloatyWindowManger.addWindow(context, window.create(capture)) }
            }
        }
        inspector.addCaptureAvailableListener(listener)
        if (!inspector.captureCurrentWindow()) {
            inspector.removeCaptureAvailableListener(listener)
        }
    }

    override fun createAppUtils(context: Context) = AppUtils(context, AppFileProvider.AUTHORITY)

    override fun createGlobalConsole(): GlobalConsole {
        val devPluginService by lazy { App.app.devPluginService }
        return object : GlobalConsole(uiHandler) {
            override fun println(level: Int, charSequence: CharSequence): String {
                return super.println(level, charSequence).also {
                    // FIXME by SuperMonster003 as of Feb 2, 2022.
                    //  ! When running in "ui" thread (ui.run() or ui.post()),
                    //  ! android.os.NetworkOnMainThreadException may happen.
                    //  ! Further more, dunno if a thread executor is a good idea.
                    mPrintExecutor.submit { devPluginService.print(it) }
                }
            }
        }
    }

    override fun createRuntime(): ScriptRuntime = super.createRuntime().apply {

        /* Activities. */

        putProperty(ActivityShortForm.SETTINGS.fullName, PreferencesActivity::class.java)
        putProperty(ActivityShortForm.PREFERENCES.fullName, PreferencesActivity::class.java)
        putProperty(ActivityShortForm.PREF.fullName, PreferencesActivity::class.java)

        putProperty(ActivityShortForm.CONSOLE.fullName, LogActivity::class.java)
        putProperty(ActivityShortForm.LOG.fullName, LogActivity::class.java)

        putProperty(ActivityShortForm.HOMEPAGE.fullName, MainActivity::class.java)
        putProperty(ActivityShortForm.HOME.fullName, MainActivity::class.java)

        putProperty(ActivityShortForm.ABOUT.fullName, AboutActivity::class.java)

        putProperty(ActivityShortForm.BUILD.fullName, BuildActivity::class.java)

        putProperty(ActivityShortForm.DOCUMENTATION.fullName, DocumentationActivity::class.java)
        putProperty(ActivityShortForm.DOC.fullName, DocumentationActivity::class.java)
        putProperty(ActivityShortForm.DOCS.fullName, DocumentationActivity::class.java)

        /* Broadcasts. */

        putProperty(BroadcastShortForm.INSPECT_LAYOUT_BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.LAYOUT_BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.BOUNDS.fullName, LayoutBoundsFloatyWindow::class.java.name)

        putProperty(BroadcastShortForm.INSPECT_LAYOUT_HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.LAYOUT_HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
        putProperty(BroadcastShortForm.HIERARCHY.fullName, LayoutHierarchyFloatyWindow::class.java.name)
    }

    companion object {
        private var isInitialized = false

        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        lateinit var instance: AutoJs
            private set

        @Synchronized
        @JvmStatic
        fun initInstance(application: Application) {
            if (!isInitialized) {
                instance = when (BuildConfig.isInrt) {
                    true -> AutoJsInrt(application)
                    else -> AutoJs(application)
                }
                isInitialized = true
            }
        }
    }

}