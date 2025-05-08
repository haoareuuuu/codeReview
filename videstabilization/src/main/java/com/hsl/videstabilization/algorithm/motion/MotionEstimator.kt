package com.hsl.videstabilization.algorithm.motion

import android.graphics.Bitmap
import android.graphics.Matrix

/**
 * 运动估计器接口
 * 用于估计相邻帧之间的运动
 */
interface MotionEstimator {
    /**
     * 初始化估计器
     * @param width 图像宽度
     * @param height 图像高度
     */
    fun initialize(width: Int, height: Int)
    
    /**
     * 估计两帧之间的运动
     * @param prevFrame 前一帧图像
     * @param currFrame 当前帧图像
     * @return 运动变换矩阵
     */
    fun estimateMotion(prevFrame: Bitmap, currFrame: Bitmap): Matrix
    
    /**
     * 重置估计器状态
     */
    fun reset()
    
    /**
     * 释放资源
     */
    fun release()
}
