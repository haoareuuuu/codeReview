package com.hsl.videstabilization.codec

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 视频编码器
 * 负责将帧序列编码为视频文件
 */
class VideoEncoder(
    private val outputFile: File,
    private val config: VideoCodec.CodecConfig
) : VideoCodec {
    companion object {
        private const val TAG = "VideoEncoder"
        private const val TIMEOUT_US = 10000L // 10ms
    }
    
    // 编码器
    private var encoder: MediaCodec? = null
    
    // 复用器
    private var muxer: MediaMuxer? = null
    
    // 输入Surface
    private var inputSurface: Surface? = null
    
    // 视频轨道索引
    private var trackIndex = -1
    
    // 是否已启动复用器
    private var muxerStarted = false
    
    // 输入缓冲区
    private var inputBuffers: Array<ByteBuffer>? = null
    
    // 输出缓冲区
    private var outputBuffers: Array<ByteBuffer>? = null
    
    // 缓冲区信息
    private val bufferInfo = MediaCodec.BufferInfo()
    
    // 是否已初始化
    private val initialized = AtomicBoolean(false)
    
    // 是否正在运行
    private val running = AtomicBoolean(false)
    
    // 是否已取消
    private val cancelled = AtomicBoolean(false)
    
    // 锁
    private val lock = ReentrantLock()
    
    // 回调
    private var callback: VideoCodec.CodecCallback? = null
    
    // 视频格式
    private var videoFormat: MediaFormat? = null
    
    // 帧计数
    private var frameCount = 0
    
    // 总帧数
    private var totalFrames = 0
    
    /**
     * 设置回调
     * @param callback 回调
     */
    fun setCallback(callback: VideoCodec.CodecCallback) {
        this.callback = callback
    }
    
    /**
     * 设置总帧数
     * @param frames 总帧数
     */
    fun setTotalFrames(frames: Int) {
        this.totalFrames = frames
    }
    
    override fun initialize(): Boolean {
        if (initialized.get()) {
            return true
        }
        
        try {
            // 创建视频格式
            videoFormat = createMediaFormat()
            
            // 创建编码器
            encoder = MediaCodec.createEncoderByType(config.mimeType)
            encoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            // 创建输入Surface
            inputSurface = encoder?.createInputSurface()
            
            // 创建复用器
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            initialized.set(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encoder", e)
            callback?.onError("Failed to initialize encoder: ${e.message}")
            release()
            return false
        }
    }
    
    override fun release() {
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            
            inputSurface?.release()
            inputSurface = null
            
            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
            muxer = null
            
            videoFormat = null
            inputBuffers = null
            outputBuffers = null
            
            trackIndex = -1
            muxerStarted = false
            
            initialized.set(false)
            running.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
    
    override fun getMediaFormat(): MediaFormat? {
        return videoFormat
    }
    
    override fun isInitialized(): Boolean {
        return initialized.get()
    }
    
    override fun isRunning(): Boolean {
        return running.get()
    }
    
    override fun cancel() {
        cancelled.set(true)
    }
    
    /**
     * 获取输入Surface
     * @return 输入Surface
     */
    fun getInputSurface(): Surface? {
        if (!initialized.get()) {
            if (!initialize()) {
                return null
            }
        }
        return inputSurface
    }
    
    /**
     * 开始编码
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
        
        try {
            encoder?.start()
            
            // 获取输入和输出缓冲区
            inputBuffers = encoder?.inputBuffers
            outputBuffers = encoder?.outputBuffers
            
            // 重置帧计数
            frameCount = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error starting encoder", e)
            callback?.onError("Error starting encoder: ${e.message}")
            running.set(false)
        }
    }
    
    /**
     * 停止编码
     */
    fun stop() {
        if (!running.get()) {
            return
        }
        
        try {
            // 发送结束信号
            encoder?.signalEndOfInputStream()
            
            // 处理剩余的输出
            drainEncoder(true)
            
            // 停止编码器
            encoder?.stop()
            
            // 停止复用器
            if (muxerStarted) {
                muxer?.stop()
                muxerStarted = false
            }
            
            if (!cancelled.get()) {
                callback?.onComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
            callback?.onError("Error stopping encoder: ${e.message}")
        } finally {
            running.set(false)
        }
    }
    
    /**
     * 编码帧
     * @param bitmap 帧图像
     * @param presentationTimeUs 显示时间（微秒）
     */
    fun encodeFrame(bitmap: Bitmap, presentationTimeUs: Long) {
        if (!running.get() || cancelled.get()) {
            return
        }
        
        lock.withLock {
            try {
                // 获取输入缓冲区
                val inputBufferIndex = encoder?.dequeueInputBuffer(TIMEOUT_US) ?: -1
                if (inputBufferIndex >= 0 && inputBuffers != null) {
                    val inputBuffer = inputBuffers!![inputBufferIndex]
                    inputBuffer.clear()
                    
                    // 将Bitmap数据复制到缓冲区
                    bitmap.copyPixelsToBuffer(inputBuffer)
                    
                    // 提交缓冲区
                    encoder?.queueInputBuffer(
                        inputBufferIndex, 0, inputBuffer.position(),
                        presentationTimeUs, 0
                    )
                }
                
                // 处理输出
                drainEncoder(false)
                
                // 更新进度
                frameCount++
                if (totalFrames > 0) {
                    val progress = frameCount.toFloat() / totalFrames
                    callback?.onProgressUpdate(progress)
                } else {

                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding frame", e)
                callback?.onError("Error encoding frame: ${e.message}")
            }
        }
    }
    
    /**
     * 处理编码器输出
     * @param endOfStream 是否是流结束
     */
    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            encoder?.signalEndOfInputStream()
        }
        
        // 处理所有可用的输出
        while (true) {
            val outputBufferIndex = encoder?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 没有可用的输出
                if (!endOfStream) {
                    break
                }
                // 如果是流结束，继续等待
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // 输出缓冲区已更改
                outputBuffers = encoder?.outputBuffers
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 输出格式已更改，应该只发生一次
                if (muxerStarted) {
                    throw RuntimeException("Format changed twice")
                }
                val newFormat = encoder?.outputFormat
                Log.d(TAG, "Encoder output format changed: $newFormat")
                
                // 开始复用器
                trackIndex = muxer?.addTrack(newFormat!!) ?: -1
                muxer?.start()
                muxerStarted = true
            } else if (outputBufferIndex < 0) {
                // 未知错误
                Log.w(TAG, "Unexpected result from encoder.dequeueOutputBuffer: $outputBufferIndex")
            } else {
                // 正常输出
                val encodedData = outputBuffers?.get(outputBufferIndex)
                if (encodedData == null) {
                    Log.w(TAG, "encoderOutputBuffer $outputBufferIndex was null")
                    continue
                }
                
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // 编解码器配置数据，不写入文件
                    bufferInfo.size = 0
                }
                
                if (bufferInfo.size > 0 && muxerStarted) {
                    // 调整缓冲区位置
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    
                    // 写入复用器
                    muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                
                // 释放输出缓冲区
                encoder?.releaseOutputBuffer(outputBufferIndex, false)
                
                // 如果是流结束，退出循环
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
    
    /**
     * 创建媒体格式
     * @return 媒体格式
     */
    private fun createMediaFormat(): MediaFormat {
        val width = if (config.width > 0) config.width else 1920
        val height = if (config.height > 0) config.height else 1080
        val frameRate = config.frameRate
        val bitRate = config.bitRate
        
        val format = MediaFormat.createVideoFormat(config.mimeType, width, height)
        
        // 设置颜色格式
        val colorFormat = if (config.colorFormat > 0) {
            config.colorFormat
        } else {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
        
        // 设置比特率
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        
        // 设置帧率
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        
        // 设置关键帧间隔
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.keyFrameInterval)
        
        // 设置硬件加速
        if (config.useHardwareAcceleration) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        
        return format
    }
}
