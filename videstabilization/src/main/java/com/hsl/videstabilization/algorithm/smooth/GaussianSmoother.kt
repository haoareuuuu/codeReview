package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix
import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 高斯滤波平滑器
 * 使用高斯权重对运动轨迹进行平滑
 */
class GaussianSmoother : BaseMotionSmoother() {
    companion object {
        private const val TAG = "GaussianSmoother"

        // 高斯核参数
        private const val SIGMA_FACTOR = 0.3f
    }

    // 高斯核
    private var gaussianKernel: FloatArray? = null

    override fun initialize(windowSize: Int, smoothingStrength: Float) {
        super.initialize(windowSize, smoothingStrength)

        // 计算高斯核
        computeGaussianKernel()

        Log.d(TAG, "Gaussian smoother initialized with kernel size: $windowSize")
    }

    /**
     * 计算高斯核
     */
    private fun computeGaussianKernel() {
        // 创建高斯核
        gaussianKernel = FloatArray(windowSize * 2 + 1)

        // 计算sigma，根据平滑强度调整
        val sigma = windowSize * SIGMA_FACTOR * smoothingStrength

        // 计算高斯权重
        var sum = 0.0f
        for (i in -windowSize..windowSize) {
            val weight = exp(-(i * i).toFloat() / (2 * sigma * sigma))
            gaussianKernel!![i + windowSize] = weight
            sum = sum + weight
        }

        // 归一化
        for (i in gaussianKernel!!.indices) {
            gaussianKernel!![i] = gaussianKernel!![i] / sum
        }

        Log.d(TAG, "Gaussian kernel computed with sigma: $sigma")
    }

    override fun smoothTransform(index: Int): Matrix {
        if (index < 0 || index >= originalTransforms.size) {
            Log.w(TAG, "Invalid index: $index, size: ${originalTransforms.size}")
            return Matrix()
        }

        // 如果是第一帧，直接返回原始变换
        if (index == 0) {
            return Matrix(originalTransforms[0])
        }

        // 提取所有变换参数
        val allParams = extractTransformParams(originalTransforms)

        // 计算当前帧的平滑参数
        val smoothParams = FloatArray(5) // [scaleX, scaleY, rotation, translationX, translationY]

        // 应用高斯滤波
        for (i in 0 until 5) {
            smoothParams[i] = applyGaussianFilter(allParams, i, index)
        }

        // 创建平滑变换矩阵
        return createTransformMatrix(smoothParams)
    }

    /**
     * 应用高斯滤波
     * @param allParams 所有变换参数
     * @param paramIndex 参数索引
     * @param frameIndex 帧索引
     * @return 平滑后的参数值
     */
    private fun applyGaussianFilter(
        allParams: List<FloatArray>,
        paramIndex: Int,
        frameIndex: Int
    ): Float {
        var sum = 0.0f
        var weightSum = 0.0f

        // 计算窗口范围
        val startIndex = max(0, frameIndex - windowSize)
        val endIndex = min(allParams.size - 1, frameIndex + windowSize)

        // 应用高斯权重
        for (i in startIndex..endIndex) {
            val kernelIndex = i - frameIndex + windowSize
            if (kernelIndex >= 0 && kernelIndex < gaussianKernel!!.size) {
                val weight = gaussianKernel!![kernelIndex]
                sum = sum + allParams[i][paramIndex] * weight
                weightSum = weightSum + weight
            }
        }

        // 归一化
        return if (weightSum > 0) sum / weightSum else allParams[frameIndex][paramIndex]
    }

    override fun reset() {
        super.reset()

        // 重新计算高斯核
        computeGaussianKernel()
    }

    override fun release() {
        super.release()

        // 释放资源
        gaussianKernel = null
    }
}
