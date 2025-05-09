package com.hsl.videstabilization.codec

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * 视频编解码测试类
 * 用于测试视频编解码接口
 */
object VideoCodecTest {
    private const val TAG = "VideoCodecTest"
    
    /**
     * 测试视频转码
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @param listener 监听器
     */
    fun testTranscode(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: TestListener
    ) {
        // 获取视频信息
        val videoInfo = VideoUtils.getVideoInfo(context, inputUri)
        if (videoInfo == null) {
            listener.onError("Failed to get video info")
            return
        }
        
        Log.d(TAG, "Video info: $videoInfo")
        
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            width = videoInfo.width,
            height = videoInfo.height,
            frameRate = videoInfo.frameRate,
            bitRate = videoInfo.bitrate,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建转码器
        val transcoder = VideoTranscoder(context, inputUri, outputFile, config)
        
        // 设置回调
        transcoder.setCallback(object : VideoCodec.CodecCallback {
            override fun onProgressUpdate(progress: Float) {
                listener.onProgressUpdate(progress)
            }
            
            override fun onComplete() {
                listener.onComplete(outputFile)
            }
            
            override fun onError(error: String, code: Int) {
                listener.onError(error)
            }
        })
        
        // 初始化转码器
        if (!transcoder.initialize()) {
            listener.onError("Failed to initialize transcoder")
            return
        }
        
        // 开始转码
        transcoder.start()
    }
    
    /**
     * 测试视频解码
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param listener 监听器
     */
    fun testDecode(
        context: Context,
        inputUri: Uri,
        listener: TestListener
    ) {
        // 获取视频信息
        val videoInfo = VideoUtils.getVideoInfo(context, inputUri)
        if (videoInfo == null) {
            listener.onError("Failed to get video info")
            return
        }
        
        Log.d(TAG, "Video info: $videoInfo")
        
        // 创建解码器
        val decoder = VideoDecoder(context, inputUri)
        
        // 帧计数
        var frameCount = 0
        
        // 设置回调
        decoder.setCallback(object : VideoCodec.CodecCallback {
            override fun onProgressUpdate(progress: Float) {
                listener.onProgressUpdate(progress)
            }
            
            override fun onComplete() {
                listener.onComplete(null)
            }
            
            override fun onError(error: String, code: Int) {
                listener.onError(error)
            }
        })
        
        // 初始化解码器
        if (!decoder.initialize()) {
            listener.onError("Failed to initialize decoder")
            return
        }
        
        // 开始解码
        decoder.start(object : VideoDecoder.FrameCallback {
            override fun onFrameAvailable(bitmap: Bitmap, presentationTimeUs: Long) {
                frameCount++
                Log.d(TAG, "Frame $frameCount, time: ${presentationTimeUs / 1000}ms")
                
                // 每10帧输出一次
                if (frameCount % 10 == 0) {
                    listener.onFrameDecoded(bitmap, frameCount)
                }
            }
        })
    }
    
    /**
     * 测试视频编码
     * @param context 上下文
     * @param frames 帧列表
     * @param outputFile 输出视频文件
     * @param width 视频宽度
     * @param height 视频高度
     * @param frameRate 视频帧率
     * @param listener 监听器
     */
    fun testEncode(
        context: Context,
        frames: List<Bitmap>,
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Int,
        listener: TestListener
    ) {
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            width = width,
            height = height,
            frameRate = frameRate,
            bitRate = 8000000,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建编码器
        val encoder = VideoEncoder(outputFile, config)
        
        // 设置回调
        encoder.setCallback(object : VideoCodec.CodecCallback {
            override fun onProgressUpdate(progress: Float) {
                listener.onProgressUpdate(progress)
            }
            
            override fun onComplete() {
                listener.onComplete(outputFile)
            }
            
            override fun onError(error: String, code: Int) {
                listener.onError(error)
            }
        })
        
        // 设置总帧数
        encoder.setTotalFrames(frames.size)
        
        // 初始化编码器
        if (!encoder.initialize()) {
            listener.onError("Failed to initialize encoder")
            return
        }
        
        // 开始编码
        encoder.start()
        
        // 计算帧间隔（微秒）
        val frameIntervalUs = 1000000L / frameRate
        
        // 编码每一帧
        for (i in frames.indices) {
            val frame = frames[i]
            val presentationTimeUs = i * frameIntervalUs
            
            // 编码帧
            encoder.encodeFrame(frame, presentationTimeUs)
        }
        
        // 停止编码
        encoder.stop()
    }
    
    /**
     * 测试视频稳定化
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @param listener 监听器
     */
    fun testStabilization(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        listener: TestListener
    ) {
        // 获取视频信息
        val videoInfo = VideoUtils.getVideoInfo(context, inputUri)
        if (videoInfo == null) {
            listener.onError("Failed to get video info")
            return
        }
        
        Log.d(TAG, "Video info: $videoInfo")
        
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            width = videoInfo.width,
            height = videoInfo.height,
            frameRate = videoInfo.frameRate,
            bitRate = videoInfo.bitrate,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建转码器
        val transcoder = VideoTranscoder(context, inputUri, outputFile, config)
        
        // 创建稳定化帧处理器
        val stabilizationProcessor = StabilizationFrameProcessor()
        
        // TODO: 实现稳定化算法，计算变换矩阵
        
        // 设置帧处理器
        transcoder.setFrameProcessor(stabilizationProcessor)
        
        // 设置回调
        transcoder.setCallback(object : VideoCodec.CodecCallback {
            override fun onProgressUpdate(progress: Float) {
                listener.onProgressUpdate(progress)
            }
            
            override fun onComplete() {
                listener.onComplete(outputFile)
            }
            
            override fun onError(error: String, code: Int) {
                listener.onError(error)
            }
        })
        
        // 初始化转码器
        if (!transcoder.initialize()) {
            listener.onError("Failed to initialize transcoder")
            return
        }
        
        // 开始转码
        transcoder.start()
    }
    
    /**
     * 测试同步转码
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @return 是否成功
     */
    fun testSyncTranscode(
        context: Context,
        inputUri: Uri,
        outputFile: File
    ): Boolean {
        // 创建同步锁
        val latch = CountDownLatch(1)
        
        // 转码结果
        var result = false
        
        // 测试转码
        testTranscode(
            context,
            inputUri,
            outputFile,
            object : TestListener {
                override fun onProgressUpdate(progress: Float) {
                    Log.d(TAG, "Progress: ${progress * 100}%")
                }
                
                override fun onComplete(output: File?) {
                    result = true
                    latch.countDown()
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "Error: $error")
                    result = false
                    latch.countDown()
                }
                
                override fun onFrameDecoded(bitmap: Bitmap, frameIndex: Int) {
                    // 不需要处理
                }
            }
        )
        
        // 等待转码完成
        latch.await()
        
        return result
    }
    
    /**
     * 测试监听器
     */
    interface TestListener {
        /**
         * 进度更新回调
         * @param progress 进度值，范围0.0-1.0
         */
        fun onProgressUpdate(progress: Float)
        
        /**
         * 完成回调
         * @param output 输出文件
         */
        fun onComplete(output: File?)
        
        /**
         * 错误回调
         * @param error 错误信息
         */
        fun onError(error: String)
        
        /**
         * 帧解码回调
         * @param bitmap 帧图像
         * @param frameIndex 帧索引
         */
        fun onFrameDecoded(bitmap: Bitmap, frameIndex: Int)
    }
}
