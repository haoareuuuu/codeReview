package com.hsl.videstabilization.codec

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 视频解码器
 * 负责将视频文件解码为帧序列
 */
class VideoDecoder(
    private val context: Context,
    private val uri: Uri,
    private val config: VideoCodec.CodecConfig = VideoCodec.CodecConfig()
) : VideoCodec {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val TIMEOUT_US = 10000L // 10ms
    }
    
    // 媒体提取器
    private var extractor: MediaExtractor? = null
    
    // 解码器
    private var decoder: MediaCodec? = null
    
    // 视频轨道索引
    private var videoTrackIndex = -1
    
    // 视频格式
    private var videoFormat: MediaFormat? = null
    
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
    
    // 输出Surface
    private var outputSurface: Surface? = null
    
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
     * 设置输出Surface
     * @param surface 输出Surface
     */
    fun setOutputSurface(surface: Surface) {
        this.outputSurface = surface
    }
    
    override fun initialize(): Boolean {
        if (initialized.get()) {
            return true
        }
        
        try {
            // 创建MediaExtractor
            extractor = MediaExtractor()
            extractor?.setDataSource(context, uri, null)
            
            // 查找视频轨道
            videoTrackIndex = findVideoTrack(extractor!!)
            if (videoTrackIndex < 0) {
                Log.e(TAG, "No video track found")
                callback?.onError("No video track found")
                return false
            }
            
            // 选择视频轨道
            extractor?.selectTrack(videoTrackIndex)
            
            // 获取视频格式
            videoFormat = extractor?.getTrackFormat(videoTrackIndex)
            
            // 创建解码器
            val mimeType = videoFormat?.getString(MediaFormat.KEY_MIME)
            if (mimeType == null) {
                Log.e(TAG, "No mime type found")
                callback?.onError("No mime type found")
                return false
            }
            
            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder?.configure(videoFormat, outputSurface, null, 0)
            
            initialized.set(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize decoder", e)
            callback?.onError("Failed to initialize decoder: ${e.message}")
            release()
            return false
        }
    }
    
    override fun release() {
        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
            
            extractor?.release()
            extractor = null
            
            videoFormat = null
            inputBuffers = null
            outputBuffers = null
            
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
     * 开始解码
     * @param frameCallback 帧回调
     */
    fun start(frameCallback: FrameCallback) {
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
            decoder?.start()
            
            // 获取输入和输出缓冲区
            inputBuffers = decoder?.inputBuffers
            outputBuffers = decoder?.outputBuffers
            
            // 解码循环
            var outputDone = false
            var inputDone = false
            var frameCount = 0
            
            // 估计总帧数
            val frameRate = videoFormat?.getInteger(MediaFormat.KEY_FRAME_RATE) ?: 30
            val duration = videoFormat?.getLong(MediaFormat.KEY_DURATION) ?: 0
            val totalFrames = (duration / 1000000.0 * frameRate).toInt()
            
            while (!outputDone && !cancelled.get()) {
                // 处理输入
                if (!inputDone) {
                    val inputBufferIndex = decoder?.dequeueInputBuffer(TIMEOUT_US) ?: -1
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = inputBuffers?.get(inputBufferIndex)
                        val sampleSize = extractor?.readSampleData(inputBuffer!!, 0) ?: -1
                        
                        if (sampleSize < 0) {
                            decoder?.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val sampleTime = extractor?.sampleTime ?: 0
                            decoder?.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                sampleTime, 0
                            )
                            extractor?.advance()
                        }
                    }
                }
                
                // 处理输出
                val outputBufferIndex = decoder?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                    
                    // 处理视频帧
                    val doRender = outputSurface != null
                    
                    if (!doRender && outputBuffers != null) {
                        val outputBuffer = outputBuffers!![outputBufferIndex]
                        
                        // 将输出缓冲区转换为Bitmap
                        val bitmap = createBitmapFromBuffer(outputBuffer, bufferInfo, videoFormat!!)
                        
                        // 回调帧
                        frameCallback.onFrameAvailable(bitmap, bufferInfo.presentationTimeUs)
                        
                        // 更新进度
                        if (totalFrames > 0) {
                            val progress = frameCount.toFloat() / totalFrames
                            callback?.onProgressUpdate(progress)
                        }
                        
                        frameCount++
                    }
                    
                    // 释放输出缓冲区
                    decoder?.releaseOutputBuffer(outputBufferIndex, doRender)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = decoder?.outputBuffers
                }
            }
            
            if (!cancelled.get()) {
                callback?.onComplete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during decoding", e)
            callback?.onError("Error during decoding: ${e.message}")
        } finally {
            running.set(false)
        }
    }
    
    /**
     * 查找视频轨道
     * @param extractor 媒体提取器
     * @return 视频轨道索引，如果没有找到则返回-1
     */
    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return i
            }
        }
        return -1
    }
    
    /**
     * 从缓冲区创建Bitmap
     * @param buffer 缓冲区
     * @param bufferInfo 缓冲区信息
     * @param format 媒体格式
     * @return Bitmap
     */
    private fun createBitmapFromBuffer(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        format: MediaFormat
    ): Bitmap {
        // 获取视频宽高
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        
        // 创建Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 设置缓冲区位置
        buffer.position(bufferInfo.offset)
        buffer.limit(bufferInfo.offset + bufferInfo.size)
        
        // 将缓冲区数据复制到Bitmap
        bitmap.copyPixelsFromBuffer(buffer)
        
        return bitmap
    }
    
    /**
     * 帧回调接口
     */
    interface FrameCallback {
        /**
         * 帧可用回调
         * @param bitmap 帧图像
         * @param presentationTimeUs 显示时间（微秒）
         */
        fun onFrameAvailable(bitmap: Bitmap, presentationTimeUs: Long)
    }
}
