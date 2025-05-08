package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 自适应滤波平滑器
 * 根据运动特性动态调整滤波参数
 */
class AdaptiveSmoother : BaseMotionSmoother() {
    companion object {
        private const val TAG = "AdaptiveSmoother"

        // 运动阈值
        private const val MOTION_THRESHOLD_LOW = 0.01f
        private const val MOTION_THRESHOLD_HIGH = 0.1f

        // 窗口大小范围
        private const val MIN_WINDOW_SIZE = 5
        private const val MAX_WINDOW_SIZE = 60
    }

    // 当前使用的平滑器
    private var currentSmoother: BaseMotionSmoother? = null

    // 高斯平滑器
    private var gaussianSmoother: GaussianSmoother? = null

    // 卡尔曼平滑器
    private var kalmanSmoother: KalmanSmoother? = null

    // 运动速度历史
    private val motionVelocityHistory = ArrayList<FloatArray>()

    override fun initialize(windowSize: Int, smoothingStrength: Float) {
        super.initialize(windowSize, smoothingStrength)

        // 初始化高斯平滑器
        gaussianSmoother = GaussianSmoother()
        gaussianSmoother!!.initialize(windowSize, smoothingStrength)

        // 初始化卡尔曼平滑器
        kalmanSmoother = KalmanSmoother()
        kalmanSmoother!!.initialize(windowSize, smoothingStrength)

        // 默认使用高斯平滑器
        currentSmoother = gaussianSmoother

        Log.d(TAG, "Adaptive smoother initialized")
    }

    override fun addTransform(transform: Matrix, timestamp: Long): Matrix {
        if (!isInitialized) {
            initialize(windowSize, smoothingStrength)
        }

        // 添加原始变换矩阵和时间戳
        originalTransforms.add(Matrix(transform))
        timestamps.add(timestamp)

        // 计算运动速度
        if (originalTransforms.size > 1) {
            val prevParams = MatrixUtils.extractTransformParams(originalTransforms[originalTransforms.size - 2])
            val currParams = MatrixUtils.extractTransformParams(transform)

            // 计算参数变化率
            val velocities = FloatArray(5)
            for (i in 0 until 5) {
                velocities[i] = currParams[i] - prevParams[i]
            }

            motionVelocityHistory.add(velocities)

            // 根据运动特性选择平滑器和调整参数
            adaptParameters()
        }

        // 使用当前平滑器计算平滑变换矩阵
        val smoothTransform = currentSmoother!!.addTransform(transform, timestamp)
        smoothTransforms.add(smoothTransform)

        return smoothTransform
    }

    /**
     * 根据运动特性调整参数
     */
    private fun adaptParameters() {
        // 计算最近几帧的平均运动速度
        val recentFrames = min(10, motionVelocityHistory.size)
        val avgVelocities = FloatArray(5)

        for (i in 0 until recentFrames) {
            val index = motionVelocityHistory.size - 1 - i
            val velocities = motionVelocityHistory[index]

            for (j in 0 until 5) {
                avgVelocities[j] = avgVelocities[j] + abs(velocities[j])
            }
        }

        for (j in 0 until 5) {
            avgVelocities[j] = avgVelocities[j] / recentFrames
        }

        // 计算总体运动强度
        val motionIntensity = (avgVelocities[2] + avgVelocities[3] + avgVelocities[4]) / 3

        // 根据运动强度选择平滑器
        if (motionIntensity < MOTION_THRESHOLD_LOW) {
            // 低运动强度，使用高斯平滑器
            currentSmoother = gaussianSmoother

            // 增加窗口大小，增强平滑效果
            val newWindowSize = min(MAX_WINDOW_SIZE, windowSize + 5)
            if (newWindowSize != windowSize) {
                windowSize = newWindowSize
                gaussianSmoother!!.initialize(windowSize, smoothingStrength)
                Log.d(TAG, "Low motion detected, switched to Gaussian smoother with window size: $windowSize")
            }
        } else if (motionIntensity > MOTION_THRESHOLD_HIGH) {
            // 高运动强度，使用卡尔曼平滑器
            currentSmoother = kalmanSmoother

            // 减小窗口大小，减弱平滑效果
            val newWindowSize = max(MIN_WINDOW_SIZE, windowSize - 5)
            if (newWindowSize != windowSize) {
                windowSize = newWindowSize
                kalmanSmoother!!.initialize(windowSize, smoothingStrength)
                Log.d(TAG, "High motion detected, switched to Kalman smoother with window size: $windowSize")
            }
        }

        // 根据运动强度调整平滑强度
        val newSmoothingStrength = when {
            motionIntensity < MOTION_THRESHOLD_LOW -> min(1.0f, smoothingStrength + 0.1f)
            motionIntensity > MOTION_THRESHOLD_HIGH -> max(0.1f, smoothingStrength - 0.1f)
            else -> smoothingStrength
        }

        if (newSmoothingStrength != smoothingStrength) {
            smoothingStrength = newSmoothingStrength
            currentSmoother!!.initialize(windowSize, smoothingStrength)
            Log.d(TAG, "Adjusted smoothing strength to: $smoothingStrength")
        }
    }

    override fun smoothTransform(index: Int): Matrix {
        // 这个方法不会被直接调用，因为我们在addTransform中已经计算了平滑变换
        // 但为了实现接口，我们返回当前平滑器的结果
        return currentSmoother?.smoothTransform(index) ?: Matrix()
    }

    override fun reset() {
        super.reset()

        // 重置平滑器
        gaussianSmoother?.reset()
        kalmanSmoother?.reset()

        // 重置运动速度历史
        motionVelocityHistory.clear()
    }

    override fun release() {
        super.release()

        // 释放平滑器
        gaussianSmoother?.release()
        kalmanSmoother?.release()

        gaussianSmoother = null
        kalmanSmoother = null
        currentSmoother = null

        // 清空运动速度历史
        motionVelocityHistory.clear()
    }
}
