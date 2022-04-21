package com.watt.testmovedetectmodule

interface DebugMoveDetectListener {
    // 가속도 센서 값 변화량
    fun onAccelValueChanged(changedAccel:Double)

    // 자이로 센서 값 변화량
    fun onGyroValueChanged(changedGyro:Double)

    // 서있는 상태에서 움직임이 없을때 카운트
    fun onCountStand(second:Int)

    // 쓰러져있는 상태에서 움직임이 없을때 카운트
    fun onCountNotStand(second:Int)

    // 가속도 센서 x, y, z값
    fun onAccelValueXYZChanged(array:FloatArray)

    // 자이로 센서 x, y, z값
    fun onGyroValueXYZChanged(array:FloatArray)
}