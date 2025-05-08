package com.hsl.videstabilization.algorithm.smooth

import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.util.MatrixUtils
import com.hsl.videstabilization.util.OpenCVUtils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.video.KalmanFilter

/**
 * 卡尔曼滤波平滑器
 * 使用卡尔曼滤波算法平滑运动轨迹
 */
class KalmanSmoother : BaseMotionSmoother() {
    companion object {
        private const val TAG = "KalmanSmoother"

        // 状态向量维度（位置、速度）
        private const val STATE_DIM = 10 // 5个参数 * 2（位置和速度）

        // 测量向量维度
        private const val MEASURE_DIM = 5 // 5个参数（scaleX, scaleY, rotation, translationX, translationY）

        // 控制向量维度
        private const val CONTROL_DIM = 0

        // 是否启用OpenCV
        private var OPENCV_INITIALIZED = false
    }

    // 卡尔曼滤波器
    private var kalmanFilter: KalmanFilter? = null

    // 状态向量
    private var stateVector: Mat? = null

    // 测量向量
    private var measurementVector: Mat? = null

    // 过程噪声协方差矩阵
    private var processNoiseCov: Mat? = null

    // 测量噪声协方差矩阵
    private var measurementNoiseCov: Mat? = null

    /**
     * 初始化OpenCV库
     */
    private fun initOpenCV() {
        if (!OPENCV_INITIALIZED) {
            if (OpenCVUtils.initSync()) {
                OPENCV_INITIALIZED = true
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize OpenCV")
                throw RuntimeException("OpenCV initialization failed")
            }
        }
    }

    override fun initialize(windowSize: Int, smoothingStrength: Float) {
        super.initialize(windowSize, smoothingStrength)
        // 初始化OpenCV
        initOpenCV()

        // 创建卡尔曼滤波器
        kalmanFilter = KalmanFilter(STATE_DIM, MEASURE_DIM, CONTROL_DIM, CvType.CV_32F)

        // 初始化状态转移矩阵 (A)
        val transitionMatrix = kalmanFilter!!._transitionMatrix
        // 设置为单位矩阵
        Core.setIdentity(transitionMatrix)

        // 设置位置到速度的关系
        for (i in 0 until MEASURE_DIM) {
            transitionMatrix.put(i, i + MEASURE_DIM, 1.0)
        }

        kalmanFilter!!._transitionMatrix = transitionMatrix

        // 初始化测量矩阵 (H)
        val measurementMatrix = kalmanFilter!!._measurementMatrix
        // 设置为零矩阵
        measurementMatrix.setTo(Scalar(0.0))

        // 设置测量矩阵，只测量位置
        for (i in 0 until MEASURE_DIM) {
            measurementMatrix.put(i, i, 1.0)
        }

        kalmanFilter!!._measurementMatrix = measurementMatrix

        // 初始化过程噪声协方差矩阵 (Q)
        processNoiseCov = kalmanFilter!!._processNoiseCov
        // 设置为单位矩阵
        Core.setIdentity(processNoiseCov, Scalar(1e-4))

        // 根据平滑强度调整过程噪声
        val processNoise = 1e-4 * (1.0 - smoothingStrength)
        processNoiseCov!!.setTo(Scalar(processNoise))

        kalmanFilter!!._processNoiseCov = processNoiseCov!!

        // 初始化测量噪声协方差矩阵 (R)
        measurementNoiseCov = kalmanFilter!!._measurementNoiseCov
        // 设置为单位矩阵
        Core.setIdentity(measurementNoiseCov, Scalar(1e-1))

        // 根据平滑强度调整测量噪声
        val measurementNoise = 1e-1 * smoothingStrength
        measurementNoiseCov!!.setTo(Scalar(measurementNoise))

        kalmanFilter!!._measurementNoiseCov = measurementNoiseCov!!

        // 初始化后验误差协方差矩阵 (P)
        val errorCovPost = kalmanFilter!!._errorCovPost
        // 设置为单位矩阵
        Core.setIdentity(errorCovPost, Scalar(1.0))

        kalmanFilter!!._errorCovPost = errorCovPost

        // 初始化状态向量和测量向量
        stateVector = Mat.zeros(STATE_DIM, 1, CvType.CV_32F)
        measurementVector = Mat.zeros(MEASURE_DIM, 1, CvType.CV_32F)

        Log.d(TAG, "Kalman filter initialized")
    }

    override fun smoothTransform(index: Int): Matrix {
        if (index < 0 || index >= originalTransforms.size) {
            Log.w(TAG, "Invalid index: $index, size: ${originalTransforms.size}")
            return Matrix()
        }

        // 获取原始变换矩阵
        val originalTransform = originalTransforms[index]

        // 提取变换参数
        val params = MatrixUtils.extractTransformParams(originalTransform)

        // 更新测量向量
        for (i in 0 until MEASURE_DIM) {
            measurementVector!!.put(i, 0, params[i].toDouble())
        }

        // 预测步骤
        val prediction = kalmanFilter!!.predict()

        // 更新步骤
        val corrected = kalmanFilter!!.correct(measurementVector)

        // 提取平滑后的参数
        val smoothParams = FloatArray(MEASURE_DIM)
        for (i in 0 until MEASURE_DIM) {
            smoothParams[i] = corrected.get(i, 0)[0].toFloat()
        }

        // 创建平滑变换矩阵
        return createTransformMatrix(smoothParams)
    }

    override fun reset() {
        super.reset()

        // 重置卡尔曼滤波器
        if (kalmanFilter != null) {
            // 重置状态向量
            stateVector!!.setTo(Scalar(0.0))
            kalmanFilter!!._statePost = stateVector!!

            // 重置后验误差协方差矩阵
            val errorCovPost = kalmanFilter!!._errorCovPost
            Core.setIdentity(errorCovPost, Scalar(1.0))
            kalmanFilter!!._errorCovPost = errorCovPost
        }
    }

    override fun release() {
        super.release()

        // 释放OpenCV资源
        stateVector?.release()
        measurementVector?.release()
        processNoiseCov?.release()
        measurementNoiseCov?.release()

        stateVector = null
        measurementVector = null
        processNoiseCov = null
        measurementNoiseCov = null
        kalmanFilter = null
    }
}
