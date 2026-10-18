package com.cuscus.ffmpeggui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.cuscus.ffmpeggui.di.AppContainer
import com.cuscus.ffmpeggui.media.FfmpegFonts
import kotlin.concurrent.thread

/** Owns the dependency container for the lifetime of the process. */
class FfmpegGuiApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) { startedActivities > 0 } }

    /** Activities on screen (started and not yet stopped). */
    @Volatile private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                    // Back on screen: a notification about a finished export has done its job.
                    container.exportNotifier.clearResult()
                    container.workNotifier.clearResults()
                }
                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        // Whatever an earlier run left in the cache is of no use any more.
        thread(name = "cache-sweep", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            container.scratchStorage.sweep()
            container.smartFeatures.transcriber.sweep()
            // Subtitles burnt in by libass find the phone's fonts through fontconfig.
            FfmpegFonts.configure(this)
        }
    }
}
