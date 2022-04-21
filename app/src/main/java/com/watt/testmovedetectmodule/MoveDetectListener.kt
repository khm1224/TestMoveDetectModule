package com.watt.testmovedetectmodule

interface MoveDetectListener{
    fun onNoMove()
    fun onNoMoveNotStanding()
    fun onMoveDetect()
    fun onFallDetect()
}