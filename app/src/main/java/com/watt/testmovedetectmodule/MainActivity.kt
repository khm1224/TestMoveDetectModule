package com.watt.testmovedetectmodule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setReceiver()
        startUndeadService()


    }

    var receiver:BroadcastReceiver?=null

    private fun setReceiver(){
        val intentFilter = IntentFilter()
        intentFilter.addAction("com.watt.movedetect.broadcast")

        receiver = object:BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra("state")
                l.d("onReceive state:$state")

            }
        }
        registerReceiver(receiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun startUndeadService(){
        val foregroundServiceIntent: Intent
        if (null == MoveDetectService.serviceIntent) {
            foregroundServiceIntent = Intent(applicationContext, MoveDetectService::class.java)
            foregroundServiceIntent.putExtra("ready", "t")
            applicationContext.startForegroundService(foregroundServiceIntent)
            l.d(" UndeadService.serviceIntent is null")
        } else {
            foregroundServiceIntent = MoveDetectService.serviceIntent!!
            foregroundServiceIntent.putExtra("ready", "t")
            l.d(" UndeadService.serviceIntent not null")
        }
    }


}