package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils
import kotlin.math.max
import kotlin.math.min

/**
 * 轨迹优化器
 * 用于全局优化视频的运动轨迹
 */
class TrajectoryOptimizer {
    companion object {
        private const val TAG = "TrajectoryOptimizer"
    }
    
    // 原始变换矩阵列表
    private val originalTransforms = ArrayList<Matrix>()
    
    // 平滑变换矩阵列表
    private val smoothTransforms = ArrayList<Matrix>()
    
    // 优化变换矩阵列表
    private val optimizedTransforms = ArrayList<Matrix>()
    
    // 平滑器
    private var smoother: MotionSmoother? = null
    
    // 边界约束
    private var boundaryConstraint: Float = 0.1f
    
    /**
     * 初始化轨迹优化器
     * @param smootherType 平滑器类型
     * @param windowSize 平滑窗口大小
     * @param smoothingStrength 平滑强度，范围0.0-1.0
     * @param boundaryConstraint 边界约束，范围0.0-1.0
     */
    fun initialize(
        smootherType: MotionSmootherFactory.SmootherType,
        windowSize: Int,
        smoothingStrength: Float,
        boundaryConstraint: Float
    ) {
        // 创建平滑器
        smoother = MotionSmootherFactory.createSmoother(
            smootherType,
            windowSize,
            smoothingStrength
        )
        
        // 设置边界约束
        this.boundaryConstraint = boundaryConstraint.coerceIn(0.0f, 1.0f)
        
        Log.d(TAG, "Trajectory optimizer initialized with smoother: $smootherType, " +
                "window size: $windowSize, smoothing strength: $smoothingStrength, " +
                "boundary constraint: $boundaryConstraint")
    }
    
    /**
     * 添加原始变换矩阵
     * @param transform 原始变换矩阵
     */
    fun addTransform(transform: Matrix) {
        originalTransforms.add(Matrix(transform))
    }
    
    /**
     * 优化轨迹
     * @return 优化后的变换矩阵列表
     */
    fun optimizeTrajectory(): List<Matrix> {
        if (smoother == null) {
            Log.w(TAG, "Smoother not initialized, using default")
            initialize(
                MotionSmootherFactory.SmootherType.GAUSSIAN,
                30,
                0.5f,
                0.1f
            )
        }
        
        // 清空之前的结果
        smoothTransforms.clear()
        optimizedTransforms.clear()
        
        // 平滑轨迹
        for (i in originalTransforms.indices) {
            val smoothTransform = smoother!!.addTransform(originalTransforms[i], i.toLong())
            smoothTransforms.add(smoothTransform)
        }
        
        // 应用边界约束
        applyBoundaryConstraint()
        
        return optimizedTransforms
    }
    
    /**
     * 应用边界约束
     */
    private fun applyBoundaryConstraint() {
        // 计算原始轨迹和平滑轨迹的边界
        val originalBounds = calculateTrajectoryBounds(originalTransforms)
        val smoothBounds = calculateTrajectoryBounds(smoothTransforms)
        
        // 计算边界差异
        val boundaryDiff = FloatArray(4)
        for (i in 0 until 4) {
            boundaryDiff[i] = smoothBounds[i] - originalBounds[i]
        }
        
        // 应用边界约束
        for (i in smoothTransforms.indices) {
            // 提取平滑变换参数
            val smoothParams = MatrixUtils.extractTransformParams(smoothTransforms[i])
            
            // 计算约束因子，随着时间逐渐增加
            val constraintFactor = min(1.0f, i.toFloat() / smoothTransforms.size * 5) * boundaryConstraint
            
            // 应用约束
            smoothParams[3] -= boundaryDiff[0] * constraintFactor // translationX
            smoothParams[4] -= boundaryDiff[1] * constraintFactor // translationY
            
            // 创建优化变换矩阵
            val optimizedTransform = MatrixUtils.createTransformMatrix(
                smoothParams[0], smoothParams[1], smoothParams[2], smoothParams[3], smoothParams[4]
            )
            
            optimizedTransforms.add(optimizedTransform)
        }
    }
    
    /**
     * 计算轨迹边界
     * @param transforms 变换矩阵列表
     * @return 边界数组 [minX, minY, maxX, maxY]
     */
    private fun calculateTrajectoryBounds(transforms: List<Matrix>): FloatArray {
        val bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
        
        for (transform in transforms) {
            val params = MatrixUtils.extractTransformParams(transform)
            
            // 更新边界
            bounds[0] = min(bounds[0], params[3]) // minX
            bounds[1] = min(bounds[1], params[4]) // minY
            bounds[2] = max(bounds[2], params[3]) // maxX
            bounds[3] = max(bounds[3], params[4]) // maxY
        }
        
        return bounds
    }
    
    /**
     * 获取原始变换矩阵
     * @param index 索引
     * @return 原始变换矩阵
     */
    fun getOriginalTransform(index: Int): Matrix {
        if (index < 0 || index >= originalTransforms.size) {
            return Matrix()
        }
        return Matrix(originalTransforms[index])
    }
    
    /**
     * 获取平滑变换矩阵
     * @param index 索引
     * @return 平滑变换矩阵
     */
    fun getSmoothTransform(index: Int): Matrix {
        if (index < 0 || index >= smoothTransforms.size) {
            return Matrix()
        }
        return Matrix(smoothTransforms[index])
    }
    
    /**
     * 获取优化变换矩阵
     * @param index 索引
     * @return 优化变换矩阵
     */
    fun getOptimizedTransform(index: Int): Matrix {
        if (index < 0 || index >= optimizedTransforms.size) {
            return Matrix()
        }
        return Matrix(optimizedTransforms[index])
    }
    
    /**
     * 重置轨迹优化器
     */
    fun reset() {
        originalTransforms.clear()
        smoothTransforms.clear()
        optimizedTransforms.clear()
        smoother?.reset()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        reset()
        smoother?.release()
        smoother = null
    }
}
