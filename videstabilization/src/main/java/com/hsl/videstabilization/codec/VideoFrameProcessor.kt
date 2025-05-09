package com.hsl.videstabilization.codec

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaFormat
import android.util.Log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频帧处理器
 * 负责对视频帧进行处理，如稳定化、滤镜等
 */
abstract class VideoFrameProcessor {
    companion object {
        const val TAG = "VideoFrameProcessor"
    }
    
    // 是否已初始化
    protected val initialized = AtomicBoolean(false)
    
    // 是否正在运行
    protected val running = AtomicBoolean(false)
    
    // 是否已取消
    protected val cancelled = AtomicBoolean(false)
    
    // 执行器
    protected val executor: Executor = Executors.newSingleThreadExecutor()
    
    // 回调
    protected var callback: ProcessorCallback? = null
    
    /**
     * 初始化处理器
     * @return 是否初始化成功
     */
    abstract fun initialize(): Boolean
    
    /**
     * 释放资源
     */
    abstract fun release()
    
    /**
     * 处理帧
     * @param bitmap 输入帧
     * @param presentationTimeUs 显示时间（微秒）
     * @return 处理后的帧
     */
    abstract fun processFrame(bitmap: Bitmap, presentationTimeUs: Long): Bitmap

    
    /**
     * 取消处理
     */
    open fun cancel() {
        cancelled.set(true)
    }
    
    /**
     * 是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return initialized.get()
    }
    
    /**
     * 是否正在运行
     * @return 是否正在运行
     */
    fun isRunning(): Boolean {
        return running.get()
    }
    
    /**
     * 处理器回调接口
     */
    interface ProcessorCallback {
        /**
         * 进度更新回调
         * @param progress 进度值，范围0.0-1.0
         */
        fun onProgressUpdate(progress: Float)
        
        /**
         * 完成回调
         */
        fun onComplete()
        
        /**
         * 错误回调
         * @param error 错误信息
         */
        fun onError(error: String)
    }
}

/**
 * 稳定化视频帧处理器
 * 对视频帧进行稳定化处理
 */
class StabilizationFrameProcessor : VideoFrameProcessor() {
    // 变换矩阵列表
    private var transforms: List<Matrix>? = null
    
    // 当前帧索引
    private var frameIndex = 0
    
    /**
     * 设置变换矩阵列表
     * @param transforms 变换矩阵列表
     */
    fun setTransforms(transforms: List<Matrix>) {
        this.transforms = transforms
    }
    
    override fun initialize(): Boolean {
        if (initialized.get()) {
            return true
        }
        
        if (transforms == null) {
            Log.e(TAG, "Transforms not set")
            callback?.onError("Transforms not set")
            return false
        }
        
        frameIndex = 0
        initialized.set(true)
        running.set(true)
        return true
    }
    
    override fun release() {
        initialized.set(false)
        running.set(false)
        transforms = null
        frameIndex = 0
    }
    
    override fun processFrame(bitmap: Bitmap, presentationTimeUs: Long): Bitmap {
        if (!initialized.get() || transforms == null) {
            return bitmap
        }
        
        // 获取当前帧的变换矩阵
        val transform = if (frameIndex < transforms!!.size) {
            transforms!![frameIndex]
        } else {
            Matrix()
        }
        
        // 应用变换
        val result = applyTransform(bitmap, transform)
        
        // 更新进度
        frameIndex++
        if (transforms != null && transforms!!.isNotEmpty()) {
            val progress = frameIndex.toFloat() / transforms!!.size
            callback?.onProgressUpdate(progress)
        }
        
        return result
    }
    
    /**
     * 应用变换
     * @param bitmap 输入Bitmap
     * @param transform 变换矩阵
     * @return 变换后的Bitmap
     */
    private fun applyTransform(bitmap: Bitmap, transform: Matrix): Bitmap {
        // 创建新的Bitmap
        val result = Bitmap.createBitmap(
            bitmap.width, bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        // 创建画布
        val canvas = android.graphics.Canvas(result)
        
        // 应用变换
        canvas.drawBitmap(bitmap, transform, null)
        
        return result
    }
}

/**
 * 视频帧处理管道
 * 可以将多个处理器串联起来，形成处理管道
 */
class VideoFrameProcessingPipeline : VideoFrameProcessor() {
    // 处理器列表
    private val processors = mutableListOf<VideoFrameProcessor>()
    
    /**
     * 添加处理器
     * @param processor 处理器
     */
    fun addProcessor(processor: VideoFrameProcessor) {
        processors.add(processor)
    }
    
    /**
     * 清空处理器
     */
    fun clearProcessors() {
        processors.clear()
    }
    
    override fun initialize(): Boolean {
        if (initialized.get()) {
            return true
        }
        
        if (processors.isEmpty()) {
            Log.e(TAG, "No processors added")
            callback?.onError("No processors added")
            return false
        }
        
        // 初始化所有处理器
        var success = true
        for (processor in processors) {
            if (!processor.initialize()) {
                success = false
                break
            }
        }
        
        initialized.set(success)
        running.set(success)
        return success
    }
    
    override fun release() {
        // 释放所有处理器
        for (processor in processors) {
            processor.release()
        }
        
        initialized.set(false)
        running.set(false)
    }
    
    override fun processFrame(bitmap: Bitmap, presentationTimeUs: Long): Bitmap {
        if (!initialized.get() || processors.isEmpty()) {
            return bitmap
        }
        
        // 依次应用所有处理器
        var result = bitmap
        for (processor in processors) {
            result = processor.processFrame(result, presentationTimeUs)
        }
        
        return result
    }
    
    override fun cancel() {
        super.cancel()
        
        // 取消所有处理器
        for (processor in processors) {
            processor.cancel()
        }
    }
}
