package com.hsl.videstabilization.codec

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频转码器
 * 负责将视频从一种格式转换为另一种格式，同时可以应用处理器
 */
class VideoTranscoder(
    private val context: Context,
    private val inputUri: Uri,
    private val outputFile: File,
    private val config: VideoCodec.CodecConfig = VideoCodec.CodecConfig()
) {
    companion object {
        private const val TAG = "VideoTranscoder"
    }
    
    // 解码器
    private var decoder: VideoDecoder? = null
    
    // 编码器
    private var encoder: VideoEncoder? = null
    
    // 帧处理器
    private var frameProcessor: VideoFrameProcessor? = null
    
    // 是否已初始化
    private val initialized = AtomicBoolean(false)
    
    // 是否正在运行
    private val running = AtomicBoolean(false)
    
    // 是否已取消
    private val cancelled = AtomicBoolean(false)
    
    // 执行器
    private val executor: Executor = Executors.newSingleThreadExecutor()
    
    // 回调
    private var callback: VideoCodec.CodecCallback? = null
    
    /**
     * 设置回调
     * @param callback 回调
     */
    fun setCallback(callback: VideoCodec.CodecCallback) {
        this.callback = callback
    }
    
    /**
     * 设置帧处理器
     * @param processor 帧处理器
     */
    fun setFrameProcessor(processor: VideoFrameProcessor) {
        this.frameProcessor = processor
    }
    
    /**
     * 初始化转码器
     * @return 是否初始化成功
     */
    fun initialize(): Boolean {
        if (initialized.get()) {
            return true
        }
        
        try {
            // 创建解码器
            decoder = VideoDecoder(context, inputUri)
            decoder?.setCallback(object : VideoCodec.CodecCallback {
                override fun onProgressUpdate(progress: Float) {
                    // 解码进度占总进度的50%
                    callback?.onProgressUpdate(progress * 0.5f)
                }
                
                override fun onComplete() {
                    // 解码完成
                }
                
                override fun onError(error: String, code: Int) {
                    callback?.onError(error, code)
                }
            })
            
            if (!decoder!!.initialize()) {
                Log.e(TAG, "Failed to initialize decoder")
                return false
            }
            
            // 获取视频格式
            val inputFormat = decoder?.getMediaFormat()
            if (inputFormat == null) {
                Log.e(TAG, "Failed to get input format")
                return false
            }
            
            // 创建编码器配置
            val encoderConfig = createEncoderConfig(inputFormat)
            
            // 创建编码器
            encoder = VideoEncoder(outputFile, encoderConfig)
            encoder?.setCallback(object : VideoCodec.CodecCallback {
                override fun onProgressUpdate(progress: Float) {
                    // 编码进度占总进度的50%
                    callback?.onProgressUpdate(0.5f + progress * 0.5f)
                }
                
                override fun onComplete() {
                    callback?.onComplete()
                }
                
                override fun onError(error: String, code: Int) {
                    callback?.onError(error, code)
                }
            })
            
            if (!encoder!!.initialize()) {
                Log.e(TAG, "Failed to initialize encoder")
                return false
            }
            
            // 初始化帧处理器
            if (frameProcessor != null && !frameProcessor!!.initialize()) {
                Log.e(TAG, "Failed to initialize frame processor")
                return false
            }
            
            initialized.set(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize transcoder", e)
            callback?.onError("Failed to initialize transcoder: ${e.message}")
            release()
            return false
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            decoder?.release()
            decoder = null
            
            encoder?.release()
            encoder = null
            
            frameProcessor?.release()
            
            initialized.set(false)
            running.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
    
    /**
     * 开始转码
     */
    fun start() {
        if (!initialized.get()) {
            if (!initialize()) {
                return
            }
        }
        
        if (running.get()) {
            return
        }
        
        running.set(true)
        cancelled.set(false)
        
        // 在后台线程中执行转码
        executor.execute {
            try {
                // 启动编码器
                encoder?.start()
                
                // 估计总帧数
                val inputFormat = decoder?.getMediaFormat()
                val frameRate = inputFormat?.getInteger(MediaFormat.KEY_FRAME_RATE) ?: 30
                val duration = inputFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0
                val totalFrames = (duration / 1000000.0 * frameRate).toInt()
                
                // 设置总帧数
                encoder?.setTotalFrames(totalFrames)
                
                // 开始解码
                decoder?.start(object : VideoDecoder.FrameCallback {
                    override fun onFrameAvailable(bitmap: Bitmap, presentationTimeUs: Long) {
                        if (cancelled.get()) {
                            return
                        }
                        
                        // 处理帧
                        val processedBitmap = if (frameProcessor != null) {
                            frameProcessor!!.processFrame(bitmap, presentationTimeUs)
                        } else {
                            bitmap
                        }
                        
                        // 编码帧
                        encoder?.encodeFrame(processedBitmap, presentationTimeUs)
                        
                        // 如果处理后的帧与原始帧不同，释放处理后的帧
                        if (processedBitmap != bitmap) {
                            processedBitmap.recycle()
                        }
                    }
                })
                
                // 停止编码器
                encoder?.stop()
                
                if (!cancelled.get()) {
                    callback?.onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transcoding", e)
                callback?.onError("Error during transcoding: ${e.message}")
            } finally {
                running.set(false)
            }
        }
    }
    
    /**
     * 取消转码
     */
    fun cancel() {
        cancelled.set(true)
        decoder?.cancel()
        encoder?.cancel()
        frameProcessor?.cancel()
    }
    
    /**
     * 是否正在运行
     * @return 是否正在运行
     */
    fun isRunning(): Boolean {
        return running.get()
    }
    
    /**
     * 创建编码器配置
     * @param inputFormat 输入格式
     * @return 编码器配置
     */
    private fun createEncoderConfig(inputFormat: MediaFormat): VideoCodec.CodecConfig {
        // 获取输入视频的宽高
        val width = if (config.width > 0) {
            config.width
        } else {
            inputFormat.getInteger(MediaFormat.KEY_WIDTH)
        }
        
        val height = if (config.height > 0) {
            config.height
        } else {
            inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
        }
        
        // 获取输入视频的帧率
        val frameRate = if (config.frameRate > 0) {
            config.frameRate
        } else {
            inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        }
        
        // 获取输入视频的比特率
        val bitRate = if (config.bitRate > 0) {
            config.bitRate
        } else {
            inputFormat.getInteger(MediaFormat.KEY_BIT_RATE)
        }
        
        return VideoCodec.CodecConfig(
            width = width,
            height = height,
            frameRate = frameRate,
            bitRate = bitRate,
            keyFrameInterval = config.keyFrameInterval,
            useHardwareAcceleration = config.useHardwareAcceleration,
            mimeType = config.mimeType,
            colorFormat = config.colorFormat
        )
    }
}
