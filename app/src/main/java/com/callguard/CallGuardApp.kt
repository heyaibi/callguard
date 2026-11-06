package com.callguard

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.callguard.call.CallManager
import com.callguard.call.InCallActivity
import com.callguard.data.DemoData

class CallGuardApp : Application() {
    lateinit var graph: AppGraph

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        if (BuildConfig.DEBUG) DemoData.seedOnce(this, graph.repos)
        // A ringing call pops up as a heads-up only while neither the main app nor the in-call screen is in front.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (isCallScreen(activity)) CallManager.screenStarted()
            }

            override fun onActivityStopped(activity: Activity) {
                if (isCallScreen(activity)) CallManager.screenStopped()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun isCallScreen(activity: Activity): Boolean = activity is MainActivity || activity is InCallActivity

    companion object {
        fun graph(ctx: Context): AppGraph = (ctx.applicationContext as CallGuardApp).graph
    }
}
