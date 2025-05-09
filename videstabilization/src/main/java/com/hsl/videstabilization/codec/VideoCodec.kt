package com.hsl.videstabilization.codec

import android.media.MediaFormat
import android.net.Uri
import java.io.File

/**
 * 视频编解码接口
 * 定义视频编解码的基本操作
 */
interface VideoCodec {
    /**
     * 初始化编解码器
     * @return 是否初始化成功
     */
    fun initialize(): Boolean
    
    /**
     * 释放资源
     */
    fun release()
    
    /**
     * 获取视频格式
     * @return 视频格式
     */
    fun getMediaFormat(): MediaFormat?
    
    /**
     * 是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean
    
    /**
     * 是否正在运行
     * @return 是否正在运行
     */
    fun isRunning(): Boolean
    
    /**
     * 取消操作
     */
    fun cancel()
    
    /**
     * 视频编解码配置类
     */
    data class CodecConfig(
        // 视频宽度
        val width: Int = -1,
        // 视频高度
        val height: Int = -1,
        // 视频帧率
        val frameRate: Int = 30,
        // 视频比特率
        val bitRate: Int = 8000000,
        // 关键帧间隔（秒）
        val keyFrameInterval: Int = 1,
        // 是否使用硬件加速
        val useHardwareAcceleration: Boolean = true,
        // 视频编码格式（如 "video/avc"）
        val mimeType: String = "video/avc",
        // 颜色格式
        val colorFormat: Int = -1
    )
    
    /**
     * 视频编解码状态监听器
     */
    interface CodecCallback {
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
        fun onError(error: String, code: Int = -1)
    }
}
