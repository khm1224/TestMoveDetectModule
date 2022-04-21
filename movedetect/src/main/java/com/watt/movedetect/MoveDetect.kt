package com.watt.movedetect


import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.widget.Toast
import java.util.*
import kotlin.math.abs

class MoveDetect(context: Context) : SensorEventListener {
    private var mContext = context

    // 움직임이 아래의 숫자 이하면 움직임이 없다고 판단
    private var moveRange:Double = 0.24
    private var rotateRange:Double = 0.116
    private var gravityAccOfYAxisRange:Double = 8.88
    private var warningTime = 10

    private var fallDetectRange:Double = 25.0

    private var callbackListener: com.watt.testmovedetectmodule.MoveDetectListener? = null
    private var callbackDebugListener: com.watt.testmovedetectmodule.DebugMoveDetectListener?= null

    private var gravityAccOfYAxisRangeList = arrayListOf<Double>(6.93, 7.51, 8.03, 8.49, 8.88, 9.21, 9.65, 9.76, 9.8)
    private var moveRangeList = arrayListOf<Double>(0.288, 0.276, 0.264, 0.252, 0.240, 0.228, 0.216, 0.204, 0.192, 0.180)
    private var rotateRangeList = arrayListOf<Double>(0.139, 0.133, 0.128, 0.122, 0.116, 0.110, 0.104, 0.099, 0.093, 0.087)

    private enum class SendWarningStatus{
        None,
        First,
        Second
    }


    private var moveDetectCount = 0

    private var currentWarningState = SendWarningStatus.None

    //서비스에서 작동될 쓰레드 시간 관리 타이머
    private var fuseTimer: Timer? = null


    //가속도계 벡터
    private val accel = FloatArray(3)


    //시간으로 적분하기 위한 변수
    private var timestamp = 0f


    private var prevGyroFloatArray = FloatArray(3)
    private var prevAccelFloatArray = FloatArray(3)

    private var roll:Double = 0.0  //끄덕끄덕
    private var pitch:Double = 0.0  //도리도리
    private var yaw:Double = 0.0 //갸우뚱갸우뚱



    private var prevSmv:Double = 0.0
    private var prevRotage:Double = 0.0
    private var countFirstWarning:Int = 0
    private var countSecondWarning:Int = 0


    //안드로이드 센서를 사용하기 위한 변수
    private var sensorManager: SensorManager? = null
    private var sensorAccelerometer: Sensor? = null


    companion object {
        //타이머의 쓰레드의 간격 설정용 변수
        private const val TIME_CONSTANT = 200L

        //나노s -> s
        private const val NS2S = 1.0f / 1000000000.0f
    }

    private val dividValue = 1000 / TIME_CONSTANT





    // 자세 민감도 설정 (1~10)
    fun setPosturalSensitivity(value:Int){
        val index = value - 1
        if(index in 0..9){
            gravityAccOfYAxisRange = gravityAccOfYAxisRangeList[index]
        }else{
            Log.e("setPosturalSensitivity","out of range : $value")
        }
    }

    fun getPosturalSensitivity():Int{
        return gravityAccOfYAxisRangeList.indexOf(gravityAccOfYAxisRange) + 1
    }



    // 움직임 민감도 설정 (1~10)
    fun setMoveSensitivity(value:Int){
        val index = value -1
        if(index in 0..9){
            moveRange = moveRangeList[index]
            rotateRange = rotateRangeList[index]
        }else{
            Log.e("setMoveSensitivity","out of range : $value")
        }
    }

    fun getMoveSensitivity():Int{
        return moveRangeList.indexOf(moveRange) + 1
    }



    //알람지연시간
    fun setAlarmTime(seconds:Int){
        warningTime = seconds
    }

    fun getAlarmTime():Int{
        return warningTime
    }


    //넘어짐감지지
   fun setFallDetectRange(value:Double){
        fallDetectRange = value
    }

    fun getFallDetectRange():Double{
        return fallDetectRange
    }



    fun setOnDebugMoveDetectListener(debugMoveDetectListener: com.watt.testmovedetectmodule.DebugMoveDetectListener){
        callbackDebugListener = debugMoveDetectListener
    }





    override fun onSensorChanged(sensorEvent: SensorEvent) {
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // 새로운 가속도계 데이터를 가속도계 배열에 복사
                // 새로운 방위각 계산
                System.arraycopy(sensorEvent.values, 0, accel, 0, 3)
            }
            Sensor.TYPE_GYROSCOPE ->                  // 자이로 데이터 처리
                gyroFunction(sensorEvent)
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }

    private fun gyroFunction(event: SensorEvent) {
        val gyroX = event.values[0].toDouble()
        val gyroY = event.values[1].toDouble()
        val gyroZ = event.values[2].toDouble()

        //새 자이로 값을 자이로 배열에 복사한다.
        //원래의 자이로 데이터를 회전 벡터로 변환한다.
        if (timestamp != 0f) {
            val dT = (event.timestamp - timestamp) * NS2S

            pitch += gyroY * dT
            roll += gyroX * dT
            yaw += gyroZ * dT
        }

        //측정 완료이 완료되면, 다음 시간 간격을 위해 현재 시간을 설정한다.
        timestamp = event.timestamp.toFloat()
    }

    fun start(){
        //callbackListener = moveDetectListener
        sensorManager = mContext.getSystemService(SENSOR_SERVICE) as SensorManager
        sensorAccelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //간혹 낮은 버전의 안드로이드 기종은 가속도계 센서가 사용 불가한 경우가 있으니, 확인 작업을 한다.
        if (sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            //이전 타이머 취소
            fuseTimer?.cancel()
            fuseTimer?.purge()

            sensorManager!!.unregisterListener(this)
            initListeners()
            initVariable()

            //타이머 시작.
            fuseTimer = Timer()
            fuseTimer?.scheduleAtFixedRate(calculateFusedOrientationTask(), 1000L, TIME_CONSTANT)
        } else {
            Toast.makeText(
                mContext,
                "센서를 지원하지 않는 기종입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun stop(){
        ///l.d("stop")
        fuseTimer?.cancel()
        sensorManager!!.unregisterListener(this)

    }


    //센서 초기화.
    private fun initListeners() {
        sensorManager!!.registerListener(
            this,
            sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )
        sensorManager!!.registerListener(
            this,
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




    //지속적으로 센서 값의 변화를 계산할 쓰레드 세팅.
    internal inner class calculateFusedOrientationTask : TimerTask() {

        override fun run() {
            //**********가속도계 센서 값의 변화 측정**********
            // accel[1] y축 (중력가속도 방향), accel[0] x축(좌우), accel[2] z축(앞뒤)
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
                sendIntent.putExtra("state","onNoMove")
                mContext.sendBroadcast(sendIntent)
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
                        //l.d("first : $pastFirstTime, second : $pastSecondTime")
                    }
                }



                if(pastSecondTime >= warningTime){
                    //l.d("on second")
                    countFirstWarning = 0
                    countSecondWarning = 0
                    moveDetectCount = 0

                    val sendIntent = Intent("com.watt.movedetect.broadcast")
                    sendIntent.putExtra("state","onNoMoveNotStanding")
                    mContext.sendBroadcast(sendIntent)

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


                    val sendIntent = Intent("com.watt.movedetect.broadcast")
                    sendIntent.putExtra("state","onNoMove")
                    mContext.sendBroadcast(sendIntent)
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

                                val sendIntent = Intent("com.watt.movedetect.broadcast")
                                sendIntent.putExtra("state","onMoveDetect")
                                mContext.sendBroadcast(sendIntent)


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