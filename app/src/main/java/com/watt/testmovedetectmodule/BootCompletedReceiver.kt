package com.watt.testmovedetectmodule

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log

/**
 * Created by khm on 2021-11-08.
 */

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG: String = "BootCompletedReceiver"


    override fun onReceive(context: Context, intent: Intent) {

        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
        val action = intent.action

        Log.e(TAG, "Receive ACTION $action")
        if (action == null) {
            Log.e(TAG, "action is null")
            return
        }

        if (TextUtils.equals(action, Intent.ACTION_BOOT_COMPLETED)) {
            Log.e(TAG, "boot complete received")

            val serviceClass = MoveDetectService::class.java
            val serviceIntent = Intent(context, serviceClass)
            if (!isServiceRunning(context, serviceClass)) {
                Log.e(TAG, "Service is not running - START FOREGROUND SERVICE")
                context.startForegroundService(serviceIntent)
            }
        }
    }


    // Custom method to determine whether a service is running
    private fun isServiceRunning(context:Context, serviceClass: Class<*>): Boolean {

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Loop through the running services
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                // If the service is running then return true
                return true
            }
        }
        return false
    }
}