package com.hsl.videstabilization.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.hsl.videstabilization.algorithm.motion.MotionEstimator
import com.hsl.videstabilization.algorithm.motion.MotionEstimatorFactory
import com.hsl.videstabilization.algorithm.smooth.MotionSmoother
import com.hsl.videstabilization.algorithm.smooth.MotionSmootherFactory
import com.hsl.videstabilization.api.AlgorithmType
import com.hsl.videstabilization.util.OpenCVUtils

/**
 * 视频防抖示例类
 * 展示如何使用运动估计和平滑算法
 */
class StabilizationExample(private val context: Context) {
    companion object {
        private const val TAG = "StabilizationExample"
    }
    
    // 运动估计器
    private var motionEstimator: MotionEstimator? = null
    
    // 运动平滑器
    private var motionSmoother: MotionSmoother? = null
    
    // 是否已初始化
    private var isInitialized = false
    
    /**
     * 初始化
     * @param width 图像宽度
     * @param height 图像高度
     * @param algorithmType 算法类型
     * @param smootherType 平滑器类型
     * @param windowSize 平滑窗口大小
     * @param smoothingStrength 平滑强度
     */
    fun initialize(
        width: Int,
        height: Int,
        algorithmType: AlgorithmType = AlgorithmType.FEATURE_BASED,
        smootherType: MotionSmootherFactory.SmootherType = MotionSmootherFactory.SmootherType.GAUSSIAN,
        windowSize: Int = 30,
        smoothingStrength: Float = 0.5f
    ) {
        // 初始化OpenCV
        OpenCVUtils.initAsync(context) {
            // 创建运动估计器
            motionEstimator = MotionEstimatorFactory.createMotionEstimator(algorithmType)
            motionEstimator?.initialize(width, height)
            
            // 创建运动平滑器
            motionSmoother = MotionSmootherFactory.createSmoother(
                smootherType,
                windowSize,
                smoothingStrength
            )
            
            isInitialized = true
            Log.d(TAG, "Stabilization example initialized")
        }
    }
    
    /**
     * 处理帧
     * @param prevFrame 前一帧
     * @param currFrame 当前帧
     * @return 稳定后的变换矩阵
     */
    fun processFrame(prevFrame: Bitmap, currFrame: Bitmap): Matrix? {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized yet")
            return null
        }
        
        // 估计运动
        val motionTransform = motionEstimator?.estimateMotion(prevFrame, currFrame)
            ?: return null
        
        // 平滑运动
        val timestamp = System.currentTimeMillis()
        val smoothTransform = motionSmoother?.addTransform(motionTransform, timestamp)
            ?: return null
        
        return smoothTransform
    }
    
    /**
     * 应用变换
     * @param frame 输入帧
     * @param transform 变换矩阵
     * @return 变换后的帧
     */
    fun applyTransform(frame: Bitmap, transform: Matrix): Bitmap {
        // 创建输出位图
        val output = Bitmap.createBitmap(
            frame.width,
            frame.height,
            Bitmap.Config.ARGB_8888
        )
        
        // 创建画布
        val canvas = android.graphics.Canvas(output)
        
        // 应用变换
        canvas.drawBitmap(frame, transform, null)
        
        return output
    }
    
    /**
     * 释放资源
     */
    fun release() {
        motionEstimator?.release()
        motionSmoother?.release()
        
        motionEstimator = null
        motionSmoother = null
        
        isInitialized = false
    }
}
