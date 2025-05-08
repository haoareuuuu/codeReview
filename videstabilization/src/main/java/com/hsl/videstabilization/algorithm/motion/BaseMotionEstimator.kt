package com.hsl.videstabilization.algorithm.motion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils

/**
 * 基础运动估计器
 * 提供运动估计的通用功能
 */
abstract class BaseMotionEstimator : MotionEstimator {
    companion object {
        private const val TAG = "BaseMotionEstimator"
    }
    
    // 图像尺寸
    protected var imageWidth: Int = 0
    protected var imageHeight: Int = 0
    
    // 上一帧的变换矩阵
    protected var previousTransform: Matrix? = null
    
    // 是否已初始化
    protected var isInitialized: Boolean = false
    
    override fun initialize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
        previousTransform = Matrix()
        isInitialized = true
        
        Log.d(TAG, "Initialized with image size: $width x $height")
    }
    
    override fun reset() {
        previousTransform = Matrix()
        Log.d(TAG, "Reset motion estimator")
    }
    
    /**
     * 验证变换矩阵的有效性
     * @param transform 变换矩阵
     * @return 有效的变换矩阵
     */
    protected fun validateTransform(transform: Matrix): Matrix {
        // 检查变换矩阵是否有效
        if (!MatrixUtils.isValidTransform(transform)) {
            Log.w(TAG, "Invalid transform detected, using identity matrix")
            return Matrix()
        }
        
        // 限制变换的范围，防止过度变换
        return MatrixUtils.constrainTransform(transform, 0.2f, 0.2f, 30f)
    }
    
    /**
     * 累积变换矩阵
     * @param newTransform 新的变换矩阵
     * @return 累积后的变换矩阵
     */
    protected fun accumulateTransform(newTransform: Matrix): Matrix {
        val result = Matrix(previousTransform)
        result.preConcat(newTransform)
        previousTransform = Matrix(result)
        return result
    }
}
