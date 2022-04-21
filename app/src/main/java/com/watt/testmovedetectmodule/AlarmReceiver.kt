package com.watt.testmovedetectmodule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Created by khm on 2021-08-09.
 */

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        l.d("----- onReceive in AlarmReceiver ")
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val undeadServiceIntent = Intent(context, MoveDetectService::class.java)
            context?.startForegroundService(undeadServiceIntent)
        }else{
            val undeadServiceIntent = Intent(context, MoveDetectService::class.java)
            context?.startService(undeadServiceIntent)
        }


    }
}