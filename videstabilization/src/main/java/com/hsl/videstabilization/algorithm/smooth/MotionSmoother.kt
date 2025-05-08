package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix

/**
 * 运动平滑器接口
 * 用于平滑运动轨迹，去除抖动同时保留有意的相机运动
 */
interface MotionSmoother {
    /**
     * 初始化平滑器
     * @param windowSize 平滑窗口大小
     * @param smoothingStrength 平滑强度，范围0.0-1.0
     */
    fun initialize(windowSize: Int, smoothingStrength: Float)
    
    /**
     * 添加原始变换矩阵
     * @param transform 原始变换矩阵
     * @param timestamp 时间戳（毫秒）
     * @return 平滑后的变换矩阵
     */
    fun addTransform(transform: Matrix, timestamp: Long): Matrix
    
    /**
     * 获取指定索引的平滑变换矩阵
     * @param index 索引
     * @return 平滑变换矩阵
     */
    fun getSmoothTransform(index: Int): Matrix
    
    /**
     * 获取所有平滑变换矩阵
     * @return 平滑变换矩阵列表
     */
    fun getAllSmoothTransforms(): List<Matrix>
    
    /**
     * 重置平滑器状态
     */
    fun reset()
    
    /**
     * 释放资源
     */
    fun release()
}
