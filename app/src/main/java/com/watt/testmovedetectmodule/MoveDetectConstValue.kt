package com.watt.testmovedetectmodule

import android.util.Log

/**
 * Created by khm on 2021-11-19.
 */

object MoveDetectConstValue {
    // 움직임이 아래의 숫자 이하면 움직임이 없다고 판단
    var moveRange:Double = 0.24
    var rotateRange:Double = 0.116
    var gravityAccOfYAxisRange:Double = 8.88
    var warningTime = 10

    var fallDetectRange:Double = 25.0

    var gravityAccOfYAxisRangeList = arrayListOf<Double>(6.93, 7.51, 8.03, 8.49, 8.88, 9.21, 9.65, 9.76, 9.8)
    var moveRangeList = arrayListOf<Double>(0.288, 0.276, 0.264, 0.252, 0.240, 0.228, 0.216, 0.204, 0.192, 0.180)
    var rotateRangeList = arrayListOf<Double>(0.139, 0.133, 0.128, 0.122, 0.116, 0.110, 0.104, 0.099, 0.093, 0.087)

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


//    //넘어짐감지지
//    fun setFallDetectRange(value:Double){
//        fallDetectRange = value
//    }
//
//    fun getFallDetectRange():Double{
//        return fallDetectRange
//    }

}