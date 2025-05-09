package com.hsl.videstabilization.core.postprocess

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.hsl.videstabilization.algorithm.motion.MotionEstimator
import com.hsl.videstabilization.algorithm.motion.MotionEstimatorFactory
import com.hsl.videstabilization.algorithm.smooth.MotionSmootherFactory
import com.hsl.videstabilization.algorithm.smooth.TrajectoryOptimizer
import com.hsl.videstabilization.api.StabilizationParams
import com.hsl.videstabilization.api.StabilizerConfig
import com.hsl.videstabilization.util.OpenCVUtils
import java.io.File
import java.nio.ByteBuffer

/**
 * 视频处理器
 * 用于对视频进行防抖处理
 */
class VideoProcessor(
    private val context: Context,
    private val config: StabilizerConfig,
    private val params: StabilizationParams
) {
    companion object {
        private const val TAG = "VideoProcessor"
        
        // 缓冲区超时时间
        private const val TIMEOUT_US = 10000L
    }
    
    // 进度回调
    private var progressCallback: ((Float) -> Unit)? = null
    
    // 运动估计器
    private var motionEstimator: MotionEstimator? = null
    
    // 轨迹优化器
    private var trajectoryOptimizer: TrajectoryOptimizer? = null
    
    // 是否取消处理
    private var isCancelled = false
    
    /**
     * 设置进度回调
     * @param callback 回调函数
     */
    fun setProgressCallback(callback: (Float) -> Unit) {
        this.progressCallback = callback
    }
    
    /**
     * 处理视频
     * @param inputUri 输入视频的Uri
     * @param outputFile 输出视频的文件
     * @return 输出视频的Uri
     */
    fun process(inputUri: Uri, outputFile: File): Uri {
        // 初始化OpenCV
        if (!OpenCVUtils.initSync()) {
            throw RuntimeException("Failed to initialize OpenCV")
        }
        
        // 获取视频信息
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, inputUri)
        
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt()
            ?: throw RuntimeException("Failed to get video width")
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt()
            ?: throw RuntimeException("Failed to get video height")
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            ?: throw RuntimeException("Failed to get video duration")
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt()
            ?: 0
        
        Log.d(TAG, "Video info: width=$width, height=$height, duration=$duration, rotation=$rotation")
        
        // 初始化运动估计器
        motionEstimator = MotionEstimatorFactory.createMotionEstimator(config.algorithmType)
        motionEstimator!!.initialize(width, height)
        
        // 初始化轨迹优化器
        trajectoryOptimizer = TrajectoryOptimizer()
        trajectoryOptimizer!!.initialize(
            MotionSmootherFactory.SmootherType.GAUSSIAN,
            30,
            config.stabilizationStrength,
            0.1f
        )
        
        // 分析视频运动
        analyzeVideoMotion(inputUri, duration)
        
        // 优化轨迹
        val optimizedTransforms = trajectoryOptimizer!!.optimizeTrajectory()
        
        // 处理视频
        processVideo(inputUri, outputFile, optimizedTransforms)
        
        // 释放资源
        motionEstimator!!.release()
        trajectoryOptimizer!!.release()
        
        return Uri.fromFile(outputFile)
    }
    
    /**
     * 分析视频运动
     * @param inputUri 输入视频的Uri
     * @param duration 视频时长（毫秒）
     */
    private fun analyzeVideoMotion(inputUri: Uri, duration: Long) {
        // 创建MediaExtractor
        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)
        
        // 查找视频轨道
        val videoTrackIndex = findVideoTrack(extractor)
        if (videoTrackIndex < 0) {
            throw RuntimeException("No video track found")
        }
        
        // 选择视频轨道
        extractor.selectTrack(videoTrackIndex)
        
        // 获取视频格式
        val format = extractor.getTrackFormat(videoTrackIndex)
        
        // 创建解码器
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()
        
        // 解码视频帧并分析运动
        val inputBuffers = decoder.inputBuffers
        var outputBuffers = decoder.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()
        var outputDone = false
        var inputDone = false
        var frameCount = 0
        var prevBitmap: Bitmap? = null
        
        // 估计总帧数
        val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        val totalFrames = (duration / 1000.0 * frameRate).toInt()
        
        while (!outputDone && !isCancelled) {
            // 处理输入
            if (!inputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = inputBuffers[inputBufferIndex]
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }
            
            // 处理输出
            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputBufferIndex >= 0) {
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    outputDone = true
                }
                
                // 处理视频帧
                val outputBuffer = outputBuffers[outputBufferIndex]
                
                // 将输出缓冲区转换为Bitmap
                val bitmap = createBitmapFromBuffer(outputBuffer, bufferInfo, format)
                
                // 估计运动
                if (prevBitmap != null) {
                    val transform = motionEstimator!!.estimateMotion(prevBitmap, bitmap)
                    trajectoryOptimizer!!.addTransform(transform)
                }
                
                // 更新前一帧
                prevBitmap?.recycle()
                prevBitmap = bitmap
                
                // 释放输出缓冲区
                decoder.releaseOutputBuffer(outputBufferIndex, false)
                
                // 更新进度
                frameCount++
                val progress = frameCount.toFloat() / totalFrames
                progressCallback?.invoke(progress * 0.5f) // 分析阶段占总进度的50%
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = decoder.outputBuffers
            }
        }
        
        // 释放资源
        prevBitmap?.recycle()
        decoder.stop()
        decoder.release()
        extractor.release()
    }
    
    /**
     * 处理视频
     * @param inputUri 输入视频的Uri
     * @param outputFile 输出视频的文件
     * @param transforms 变换矩阵列表
     */
    private fun processVideo(inputUri: Uri, outputFile: File, transforms: List<android.graphics.Matrix>) {
        // 创建MediaExtractor
        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)
        
        // 查找视频轨道
        val videoTrackIndex = findVideoTrack(extractor)
        if (videoTrackIndex < 0) {
            throw RuntimeException("No video track found")
        }
        
        // 选择视频轨道
        extractor.selectTrack(videoTrackIndex)
        
        // 获取视频格式
        val format = extractor.getTrackFormat(videoTrackIndex)
        
        // 创建解码器
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()
        
        // 创建编码器
        val encoderFormat = createEncoderFormat(format)
        val encoder = MediaCodec.createEncoderByType(encoderFormat.getString(MediaFormat.KEY_MIME)!!)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        
        // 创建MediaMuxer
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        // 解码、稳定和编码视频帧
        val decoderInputBuffers = decoder.inputBuffers
        var decoderOutputBuffers = decoder.outputBuffers
        val encoderInputBuffers = encoder.inputBuffers
        var encoderOutputBuffers = encoder.outputBuffers
        val decoderBufferInfo = MediaCodec.BufferInfo()
        val encoderBufferInfo = MediaCodec.BufferInfo()
        var decoderOutputDone = false
        var encoderOutputDone = false
        var decoderInputDone = false
        var encoderInputDone = false
        var muxerStarted = false
        var muxerTrackIndex = -1
        var frameCount = 0
        
        // 估计总帧数
        val frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
        val duration = format.getLong(MediaFormat.KEY_DURATION)
        val totalFrames = (duration / 1000000.0 * frameRate).toInt()
        
        while (!encoderOutputDone && !isCancelled) {
            // 处理解码器输入
            if (!decoderInputDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoderInputBuffers[inputBufferIndex]
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        decoderInputDone = true
                    } else {
                        decoder.queueInputBuffer(
                            inputBufferIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }
            
            // 处理解码器输出
            if (!decoderOutputDone) {
                val outputBufferIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    if ((decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderInputDone = true
                    }
                    
                    // 处理视频帧
                    val outputBuffer = decoderOutputBuffers[outputBufferIndex]
                    
                    // 将输出缓冲区转换为Bitmap
                    val bitmap = createBitmapFromBuffer(outputBuffer, decoderBufferInfo, format)
                    
                    // 应用变换
                    val stabilizedBitmap = if (frameCount < transforms.size) {
                        applyTransform(bitmap, transforms[frameCount])
                    } else {
                        bitmap
                    }
                    
                    // 将稳定后的Bitmap编码
                    encodeFrame(encoder, encoderInputBuffers, stabilizedBitmap, decoderBufferInfo.presentationTimeUs)
                    
                    // 释放资源
                    bitmap.recycle()
                    if (stabilizedBitmap != bitmap) {
                        stabilizedBitmap.recycle()
                    }
                    
                    // 释放解码器输出缓冲区
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    // 更新进度
                    frameCount++
                    val progress = frameCount.toFloat() / totalFrames
                    progressCallback?.invoke(0.5f + progress * 0.5f) // 处理阶段占总进度的50%
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    decoderOutputBuffers = decoder.outputBuffers
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 忽略
                }
            }
            
            // 处理编码器输出
            val encoderOutputBufferIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_US)
            if (encoderOutputBufferIndex >= 0) {
                if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    encoderOutputDone = true
                }
                
                if (encoderBufferInfo.size > 0) {
                    val encoderOutputBuffer = encoderOutputBuffers[encoderOutputBufferIndex]
                    
                    if (!muxerStarted) {
                        throw RuntimeException("Muxer not started")
                    }
                    
                    // 写入复用器
                    encoderOutputBuffer.position(encoderBufferInfo.offset)
                    encoderOutputBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                    muxer.writeSampleData(muxerTrackIndex, encoderOutputBuffer, encoderBufferInfo)
                }
                
                // 释放编码器输出缓冲区
                encoder.releaseOutputBuffer(encoderOutputBufferIndex, false)
            } else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.outputBuffers
            } else if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 开始复用器
                if (muxerStarted) {
                    throw RuntimeException("Format changed twice")
                }
                val newFormat = encoder.outputFormat
                muxerTrackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
            }
            
            // 检查是否完成
            if (encoderInputDone && encoderOutputBufferIndex < 0) {
                encoder.signalEndOfInputStream()
                encoderOutputDone = true
            }
        }
        
        // 释放资源
        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        extractor.release()
        muxer.stop()
        muxer.release()
    }
    
    /**
     * 查找视频轨道
     * @param extractor MediaExtractor
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
     * 创建编码器格式
     * @param decoderFormat 解码器格式
     * @return 编码器格式
     */
    private fun createEncoderFormat(decoderFormat: MediaFormat): MediaFormat {
        val width = if (params.outputWidth > 0) params.outputWidth else decoderFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = if (params.outputHeight > 0) params.outputHeight else decoderFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = if (params.outputFrameRate > 0) params.outputFrameRate else decoderFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        val bitRate = if (params.outputBitRate > 0) params.outputBitRate else decoderFormat.getInteger(MediaFormat.KEY_BIT_RATE)
        
        val format = MediaFormat.createVideoFormat("video/avc", width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, params.keyFrameInterval)
        
        return format
    }
    
    /**
     * 从缓冲区创建Bitmap
     * @param buffer 缓冲区
     * @param bufferInfo 缓冲区信息
     * @param format 媒体格式
     * @return Bitmap
     */
    private fun createBitmapFromBuffer(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo, format: MediaFormat): Bitmap {
        // 注意：这是一个简化的实现，实际上需要根据颜色格式进行转换
        // 在实际应用中，应该使用OpenGL或RenderScript进行转换
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        
        // 创建Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 将缓冲区数据复制到Bitmap
        buffer.position(bufferInfo.offset)
        buffer.limit(bufferInfo.offset + bufferInfo.size)
        
        // 这里需要根据实际的颜色格式进行转换
        // 简化起见，我们假设缓冲区已经是ARGB格式
        bitmap.copyPixelsFromBuffer(buffer)
        
        return bitmap
    }
    
    /**
     * 应用变换
     * @param bitmap 输入Bitmap
     * @param transform 变换矩阵
     * @return 变换后的Bitmap
     */
    private fun applyTransform(bitmap: Bitmap, transform: android.graphics.Matrix): Bitmap {
        // 创建输出Bitmap
        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        // 创建画布
        val canvas = android.graphics.Canvas(output)
        
        // 应用变换
        canvas.drawBitmap(bitmap, transform, null)
        
        return output
    }
    
    /**
     * 编码帧
     * @param encoder 编码器
     * @param inputBuffers 输入缓冲区
     * @param bitmap Bitmap
     * @param presentationTimeUs 显示时间
     */
    private fun encodeFrame(
        encoder: MediaCodec,
        inputBuffers: Array<ByteBuffer>,
        bitmap: Bitmap,
        presentationTimeUs: Long
    ) {
        // 获取输入缓冲区
        val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferIndex >= 0) {
            val inputBuffer = inputBuffers[inputBufferIndex]
            inputBuffer.clear()
            
            // 将Bitmap数据复制到缓冲区
            bitmap.copyPixelsToBuffer(inputBuffer)
            
            // 提交缓冲区
            encoder.queueInputBuffer(
                inputBufferIndex, 0, inputBuffer.position(),
                presentationTimeUs, 0
            )
        }
    }
    
    /**
     * 取消处理
     */
    fun cancel() {
        isCancelled = true
    }
}
