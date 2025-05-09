package com.hsl.videstabilization.codec

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 视频编解码示例类
 * 展示如何使用视频编解码接口
 */
object VideoCodecExample {
    private const val TAG = "VideoCodecExample"
    
    /**
     * 简单转码示例
     * 将输入视频转码为输出视频，不做任何处理
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @param callback 回调
     */
    fun simpleTranscode(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        callback: VideoCodec.CodecCallback
    ) {
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            bitRate = 8000000,
            frameRate = 30,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建转码器
        val transcoder = VideoTranscoder(context, inputUri, outputFile, config)
        
        // 设置回调
        transcoder.setCallback(callback)
        
        // 初始化转码器
        if (!transcoder.initialize()) {
            callback.onError("Failed to initialize transcoder")
            return
        }
        
        // 开始转码
        transcoder.start()
    }
    
    /**
     * 视频稳定化示例
     * 对输入视频进行稳定化处理
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @param callback 回调
     */
    fun stabilizeVideo(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        callback: VideoCodec.CodecCallback
    ) {
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            bitRate = 8000000,
            frameRate = 30,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建转码器
        val transcoder = VideoTranscoder(context, inputUri, outputFile, config)
        
        // 创建稳定化帧处理器
        val stabilizationProcessor = StabilizationFrameProcessor()
        
        // 设置变换矩阵列表（这里只是示例，实际应该根据视频内容计算）
        val transforms = mutableListOf<Matrix>()
        for (i in 0 until 100) {
            val matrix = Matrix()
            // 这里只是示例，实际应该根据视频内容计算变换矩阵
            matrix.postTranslate(0f, 0f)
            transforms.add(matrix)
        }
        stabilizationProcessor.setTransforms(transforms)
        
        // 设置帧处理器
        transcoder.setFrameProcessor(stabilizationProcessor)
        
        // 设置回调
        transcoder.setCallback(callback)
        
        // 初始化转码器
        if (!transcoder.initialize()) {
            callback.onError("Failed to initialize transcoder")
            return
        }
        
        // 开始转码
        transcoder.start()
    }
    
    /**
     * 视频解码示例
     * 将视频解码为帧序列
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param frameCallback 帧回调
     * @param callback 回调
     */
    fun decodeVideo(
        context: Context,
        inputUri: Uri,
        frameCallback: VideoDecoder.FrameCallback,
        callback: VideoCodec.CodecCallback
    ) {
        // 创建解码器
        val decoder = VideoDecoder(context, inputUri)
        
        // 设置回调
        decoder.setCallback(callback)
        
        // 初始化解码器
        if (!decoder.initialize()) {
            callback.onError("Failed to initialize decoder")
            return
        }
        
        // 开始解码
        decoder.start(frameCallback)
    }
    
    /**
     * 视频编码示例
     * 将帧序列编码为视频
     * @param outputFile 输出视频文件
     * @param width 视频宽度
     * @param height 视频高度
     * @param frameRate 视频帧率
     * @param frames 帧列表
     * @param callback 回调
     */
    fun encodeVideo(
        outputFile: File,
        width: Int,
        height: Int,
        frameRate: Int,
        frames: List<Bitmap>,
        callback: VideoCodec.CodecCallback
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
        encoder.setCallback(callback)
        
        // 设置总帧数
        encoder.setTotalFrames(frames.size)
        
        // 初始化编码器
        if (!encoder.initialize()) {
            callback.onError("Failed to initialize encoder")
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
     * 视频格式转换示例
     * 将视频从一种格式转换为另一种格式
     * @param context 上下文
     * @param inputUri 输入视频Uri
     * @param outputFile 输出视频文件
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     * @param targetFrameRate 目标帧率
     * @param callback 回调
     */
    fun convertVideoFormat(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        targetWidth: Int,
        targetHeight: Int,
        targetFrameRate: Int,
        callback: VideoCodec.CodecCallback
    ) {
        // 创建编解码配置
        val config = VideoCodec.CodecConfig(
            width = targetWidth,
            height = targetHeight,
            frameRate = targetFrameRate,
            bitRate = 8000000,
            keyFrameInterval = 1,
            useHardwareAcceleration = true
        )
        
        // 创建转码器
        val transcoder = VideoTranscoder(context, inputUri, outputFile, config)
        
        // 设置回调
        transcoder.setCallback(callback)
        
        // 初始化转码器
        if (!transcoder.initialize()) {
            callback.onError("Failed to initialize transcoder")
            return
        }
        
        // 开始转码
        transcoder.start()
    }
}
