package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils

/**
 * 基础运动平滑器
 * 提供运动平滑的通用功能
 */
abstract class BaseMotionSmoother : MotionSmoother {
    companion object {
        private const val TAG = "BaseMotionSmoother"
        
        // 默认参数
        private const val DEFAULT_WINDOW_SIZE = 30
        private const val DEFAULT_SMOOTHING_STRENGTH = 0.5f
    }
    
    // 平滑窗口大小
    protected var windowSize: Int = DEFAULT_WINDOW_SIZE
    
    // 平滑强度
    protected var smoothingStrength: Float = DEFAULT_SMOOTHING_STRENGTH
    
    // 原始变换矩阵列表
    protected val originalTransforms = ArrayList<Matrix>()
    
    // 平滑变换矩阵列表
    protected val smoothTransforms = ArrayList<Matrix>()
    
    // 时间戳列表
    protected val timestamps = ArrayList<Long>()
    
    // 是否已初始化
    protected var isInitialized: Boolean = false
    
    override fun initialize(windowSize: Int, smoothingStrength: Float) {
        this.windowSize = windowSize
        this.smoothingStrength = smoothingStrength.coerceIn(0.0f, 1.0f)
        isInitialized = true
        
        Log.d(TAG, "Initialized with window size: $windowSize, smoothing strength: $smoothingStrength")
    }
    
    override fun addTransform(transform: Matrix, timestamp: Long): Matrix {
        if (!isInitialized) {
            initialize(DEFAULT_WINDOW_SIZE, DEFAULT_SMOOTHING_STRENGTH)
        }
        
        // 添加原始变换矩阵和时间戳
        originalTransforms.add(Matrix(transform))
        timestamps.add(timestamp)
        
        // 计算平滑变换矩阵
        val smoothTransform = smoothTransform(originalTransforms.size - 1)
        smoothTransforms.add(smoothTransform)
        
        return smoothTransform
    }
    
    /**
     * 平滑指定索引的变换矩阵
     * @param index 索引
     * @return 平滑变换矩阵
     */
    abstract fun smoothTransform(index: Int): Matrix
    
    override fun getSmoothTransform(index: Int): Matrix {
        if (index < 0 || index >= smoothTransforms.size) {
            Log.w(TAG, "Invalid index: $index, size: ${smoothTransforms.size}")
            return Matrix()
        }
        
        return Matrix(smoothTransforms[index])
    }
    
    override fun getAllSmoothTransforms(): List<Matrix> {
        return smoothTransforms.map { Matrix(it) }
    }
    
    override fun reset() {
        originalTransforms.clear()
        smoothTransforms.clear()
        timestamps.clear()
        
        Log.d(TAG, "Reset motion smoother")
    }
    
    override fun release() {
        reset()
        isInitialized = false
        
        Log.d(TAG, "Released motion smoother")
    }
    
    /**
     * 提取变换参数
     * @param transforms 变换矩阵列表
     * @return 变换参数列表，每个元素是一个数组 [scaleX, scaleY, rotation, translationX, translationY]
     */
    protected fun extractTransformParams(transforms: List<Matrix>): List<FloatArray> {
        return transforms.map { MatrixUtils.extractTransformParams(it) }
    }
    
    /**
     * 创建变换矩阵
     * @param params 变换参数 [scaleX, scaleY, rotation, translationX, translationY]
     * @return 变换矩阵
     */
    protected fun createTransformMatrix(params: FloatArray): Matrix {
        return MatrixUtils.createTransformMatrix(
            params[0], params[1], params[2], params[3], params[4]
        )
    }
}
