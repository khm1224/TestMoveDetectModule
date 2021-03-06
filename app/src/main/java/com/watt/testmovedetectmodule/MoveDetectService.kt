package com.watt.testmovedetectmodule

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.watt.testmovedetectmodule.MoveDetectConstValue.fallDetectRange
import com.watt.testmovedetectmodule.MoveDetectConstValue.gravityAccOfYAxisRange
import com.watt.testmovedetectmodule.MoveDetectConstValue.gravityAccOfYAxisRangeList
import com.watt.testmovedetectmodule.MoveDetectConstValue.moveRange
import com.watt.testmovedetectmodule.MoveDetectConstValue.moveRangeList
import com.watt.testmovedetectmodule.MoveDetectConstValue.rotateRange
import com.watt.testmovedetectmodule.MoveDetectConstValue.rotateRangeList
import com.watt.testmovedetectmodule.MoveDetectConstValue.warningTime
import java.util.*
import kotlin.math.abs

/**
 * Created by khm on 2021-11-11.
 */

class MoveDetectService : Service() {

    // ==================== undead service source code ====================
    @SuppressLint("StaticFieldLeak")
    companion object{
        var serviceIntent: Intent? = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


//    inner class LocalBinder: Binder(){
//        fun getService():MoveDetectService = this@MoveDetectService
//    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        l.d("UndeadService onStartCommand")
        serviceIntent = intent
        initializeNotification()
        triggerStopService = false
        start()

        return START_STICKY
    }



    override fun unbindService(conn: ServiceConnection) {
        super.unbindService(conn)
        l.d("unbind move detect service")
        stopService()
    }





    private var triggerStopService = false

    fun stopService(){
        l.e("stop service")
        triggerStopService = true
        stop()
    }

    private fun initializeNotification() {

        l.d("UndeadService Notification ")
        val builder = NotificationCompat.Builder(this, "1")
        //builder.setSmallIcon(R.mipmap.ic_launcher)
        val style = NotificationCompat.BigTextStyle()
        style.bigText("????????? ????????? ????????????.")
        style.setBigContentTitle(null)
        style.setSummaryText("????????? ?????????")
        builder.setContentText(null)
        builder.setContentTitle(null)
        builder.setOngoing(true)
        builder.setStyle(style)
        builder.setWhen(0)
        builder.setShowWhen(false)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        builder.setContentIntent(pendingIntent)
        val manager = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "1",
                    "undead_service",
                    NotificationManager.IMPORTANCE_NONE
                )
            )
        }
        val notification = builder.build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        l.d("UndeadService onDestroy -- triggerStopService : $triggerStopService")
        super.onDestroy()
        if(triggerStopService){
            l.d("pass undeadservice onDestroy")
        }else{
            setAlarmTimer()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        l.d("UndeadService onTaskRemoved ")
        super.onTaskRemoved(rootIntent)
        if(triggerStopService){
            l.d("pass undeadservice onDestroy")
        }else{
            setAlarmTimer()
        }

    }

    private fun setAlarmTimer(){
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.SECOND, 3)
        val intent = Intent(this, AlarmReceiver::class.java)
        val sender = PendingIntent.getBroadcast(this, 0, intent, 0)
        val alarmManager = getSystemService(Service.ALARM_SERVICE) as AlarmManager
        alarmManager[AlarmManager.RTC_WAKEUP, calendar.timeInMillis] = sender
    }




    // ==================== move detect source code ====================



    private var callbackListener: MoveDetectListener? = null
    private var callbackDebugListener: DebugMoveDetectListener?= null



    private enum class SendWarningStatus{
        None,
        First,
        Second
    }


    private var moveDetectCount = 0

    private var currentWarningState = SendWarningStatus.None

    //??????????????? ????????? ????????? ?????? ?????? ?????????
    private var fuseTimer: Timer? = null


    //???????????? ??????
    private val accel = FloatArray(3)


    //???????????? ???????????? ?????? ??????
    private var timestamp = 0f


    private var prevGyroFloatArray = FloatArray(3)
    private var prevAccelFloatArray = FloatArray(3)

    private var roll:Double = 0.0  //????????????
    private var pitch:Double = 0.0  //????????????
    private var yaw:Double = 0.0 //??????????????????



    private var prevSmv:Double = 0.0
    private var prevRotage:Double = 0.0
    private var countFirstWarning:Int = 0
    private var countSecondWarning:Int = 0


    //??????????????? ????????? ???????????? ?????? ??????
    private var sensorManager: SensorManager? = null
    private var sensorAccelerometer: Sensor? = null

    private val TIME_CONSTANT = 200L
    private val NS2S = 1.0f / 1000000000.0f
    private val dividValue = 1000 / TIME_CONSTANT






    fun setOnDebugMoveDetectListener(debugMoveDetectListener: DebugMoveDetectListener){
        callbackDebugListener = debugMoveDetectListener
    }


    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(sensorEvent: SensorEvent) {
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // ????????? ???????????? ???????????? ???????????? ????????? ??????
                    // ????????? ????????? ??????
                    System.arraycopy(sensorEvent.values, 0, accel, 0, 3)
                }
                Sensor.TYPE_GYROSCOPE ->                  // ????????? ????????? ??????
                    gyroFunction(sensorEvent)
            }
        }

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

        }
    }




    private fun gyroFunction(event: SensorEvent) {
        val gyroX = event.values[0].toDouble()
        val gyroY = event.values[1].toDouble()
        val gyroZ = event.values[2].toDouble()

        //??? ????????? ?????? ????????? ????????? ????????????.
        //????????? ????????? ???????????? ?????? ????????? ????????????.
        if (timestamp != 0f) {
            val dT = (event.timestamp - timestamp) * NS2S

            pitch += gyroY * dT
            roll += gyroX * dT
            yaw += gyroZ * dT
        }

        //?????? ????????? ????????????, ?????? ?????? ????????? ?????? ?????? ????????? ????????????.
        timestamp = event.timestamp.toFloat()
    }

    fun start(){
        //callbackListener = moveDetectListener
        sensorManager = getSystemService(Service.SENSOR_SERVICE) as SensorManager
        sensorAccelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //?????? ?????? ????????? ??????????????? ????????? ???????????? ????????? ?????? ????????? ????????? ?????????, ?????? ????????? ??????.
        if (sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            //?????? ????????? ??????
            fuseTimer?.cancel()
            fuseTimer?.purge()

            sensorManager!!.unregisterListener(sensorEventListener)
            initListeners()
            initVariable()

            l.d("start timer")
            //????????? ??????.
            fuseTimer = Timer()
            fuseTimer?.scheduleAtFixedRate(calculateFusedOrientationTask(), 1000L, TIME_CONSTANT)
        } else {
            Toast.makeText(
                this,
                "????????? ???????????? ?????? ???????????????.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun stop(){
        ///l.d("stop")
        fuseTimer?.cancel()
        sensorManager!!.unregisterListener(sensorEventListener)

    }


    //?????? ?????????.
    private fun initListeners() {
        sensorManager!!.registerListener(
            sensorEventListener,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager!!.registerListener(
            sensorEventListener,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }


    private fun initVariable(){
        countFirstWarning = 0
        countSecondWarning = 0
        moveDetectCount = 0
    }



    private var sendedPastFirstTime = 0
    private var sendedPastSecondTime = 0


    //??????????????? ?????? ?????? ????????? ????????? ????????? ??????.
    internal inner class calculateFusedOrientationTask : TimerTask() {

        override fun run() {
            //**********???????????? ?????? ?????? ?????? ??????**********
            // accel[1] y??? (??????????????? ??????), accel[0] x???(??????), accel[2] z???(??????)
            val SMV =
                Math.sqrt(accel[0] * accel[0] + accel[1] * accel[1] + (accel[2] * accel[2]).toDouble())

            val gyroFloatArray = FloatArray(3)
            gyroFloatArray[0] = abs(roll.toFloat() - prevGyroFloatArray[0])
            gyroFloatArray[1] = abs(pitch.toFloat() - prevGyroFloatArray[1])
            gyroFloatArray[2] = abs(yaw.toFloat() - prevGyroFloatArray[2])

            val accelFloatArray = FloatArray(3)
            accelFloatArray[0] = abs(accel[0] - prevAccelFloatArray[0])
            accelFloatArray[1] = abs(accel[1] - prevAccelFloatArray[1])
            accelFloatArray[2] = abs(accel[2] - prevAccelFloatArray[2])

            prevGyroFloatArray[0] = roll.toFloat()
            prevGyroFloatArray[1] = pitch.toFloat()
            prevGyroFloatArray[2] = yaw.toFloat()

            System.arraycopy(prevAccelFloatArray, 0, accel, 0, 3)

            if(SMV >= fallDetectRange){
                val sendIntent = Intent("com.watt.movedetect.broadcast")
                sendIntent.putExtra("state","onFallDetect")
                sendBroadcast(sendIntent)
//                mActivity.runOnUiThread{
//                    callbackListener?.onFallDetect()
//                }
            }

            val sumRotate = abs(gyroFloatArray[0] + gyroFloatArray[1] + gyroFloatArray[2]).toDouble()

            if (prevSmv != 0.0 && prevRotage != 0.0) {
                val gapAccel = abs(SMV - prevSmv)
                val gapGyro = abs(sumRotate - prevRotage)
//                mActivity.runOnUiThread {
//                    callbackDebugListener?.onAccelValueChanged(gapAccel)
//                    callbackDebugListener?.onGyroValueChanged(gapGyro)
//                    callbackDebugListener?.onAccelValueXYZChanged(accelFloatArray)
//                    callbackDebugListener?.onGyroValueXYZChanged(gyroFloatArray)
//                }

                if (gapAccel < moveRange) {
                    if (gapGyro < rotateRange){
                        countFirstWarning += 1
                        if(accelFloatArray[1] < gravityAccOfYAxisRange || accelFloatArray[1] > 10){
                            //l.d("accel[1] : ${accelFloatArray[1]}")
                            countSecondWarning += 1
                        }else{
                            countSecondWarning = 0
                        }
                    }else{
                        countFirstWarning = 0
                        countSecondWarning = 0
                    }

                } else {
                    countFirstWarning = 0
                    countSecondWarning = 0
                }

                val pastFirstTime = countFirstWarning / dividValue.toInt()
                val pastSecondTime = countSecondWarning / dividValue.toInt()

                when {
                    pastSecondTime > 0 -> {
                        if(sendedPastSecondTime != pastSecondTime){
                            sendedPastSecondTime = pastSecondTime
                            callbackDebugListener?.onCountNotStand(pastSecondTime)
                        }
                    }
                    pastFirstTime > 0 -> {
                        //l.d("pastFirstTime : $pastFirstTime")
                        if(sendedPastFirstTime != pastFirstTime){
                            if(sendedPastSecondTime != 0){
                                sendedPastSecondTime = 0
                                callbackDebugListener?.onCountNotStand(sendedPastSecondTime)
                            }

                            sendedPastFirstTime = pastFirstTime
                            callbackDebugListener?.onCountStand(pastFirstTime)
                        }
                    }
                    pastFirstTime == 0 ->{
                        if(sendedPastFirstTime !=0){
                            sendedPastFirstTime = pastFirstTime
                            callbackDebugListener?.onCountStand(pastFirstTime)
                        }
                    }
                    else -> {
                        l.d("first : $pastFirstTime, second : $pastSecondTime")
                    }
                }



                if(pastSecondTime >= warningTime){
                    //l.d("on second")
                    countFirstWarning = 0
                    countSecondWarning = 0
                    moveDetectCount = 0

                    l.d("onNoMoveNotStanding")
                    val sendIntent = Intent("com.watt.movedetect.broadcast")
                    sendIntent.putExtra("state","onNoMoveNotStanding")
                    sendBroadcast(sendIntent)

                    currentWarningState = SendWarningStatus.Second
//                    mActivity.runOnUiThread{
//                        callbackListener?.onNoMoveNotStanding()
//                    }
                    return
                }


                if(pastFirstTime >= warningTime){
                    //l.d("on first")
                    countFirstWarning = 0
                    countSecondWarning = 0
                    moveDetectCount = 0

                    l.d("onNoMove")
                    val sendIntent = Intent("com.watt.movedetect.broadcast")
                    sendIntent.putExtra("state","onNoMove")
                    sendBroadcast(sendIntent)
                    currentWarningState = SendWarningStatus.First

//                    mActivity.runOnUiThread{
//
//                        callbackListener?.onNoMove()
//                    }
                }else{
                    if(currentWarningState != SendWarningStatus.None){
                        if(countFirstWarning == 0){
                            moveDetectCount++
                            if(moveDetectCount > 1){
                                moveDetectCount = 0
                                currentWarningState = SendWarningStatus.None
                                l.d("onMoveDetect")
                                val sendIntent = Intent("com.watt.movedetect.broadcast")
                                sendIntent.putExtra("state","onMoveDetect")
                                sendBroadcast(sendIntent)


//                                mActivity.runOnUiThread{
//                                    callbackListener?.onMoveDetect()
//                                }
                            }
                        }
                    }
                }



            }
            prevSmv = SMV
            prevRotage = sumRotate
        }
    }

}