package com.hsl.videstabilization.api

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import com.hsl.videstabilization.core.StabilizationTask
import com.hsl.videstabilization.core.postprocess.PostProcessStabilizer
import com.hsl.videstabilization.core.realtime.RealTimeStabilizer
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 视频防抖SDK的主入口类
 * 提供实时防抖和后处理防抖功能
 */
class VideoStabilizer private constructor(
    private val context: Context,
    private val config: StabilizerConfig
) {
    // 实时防抖处理器
    private var realTimeStabilizer: RealTimeStabilizer? = null
    
    // 后处理防抖处理器
    private var postProcessStabilizer: PostProcessStabilizer? = null
    
    // 防抖监听器
    private var stabilizationListener: StabilizationListener? = null
    
    // 执行器
    private val executor: Executor = Executors.newSingleThreadExecutor()
    
    /**
     * 开始实时防抖
     * @param inputSurface 输入视频帧的SurfaceTexture
     * @param outputSurface 输出视频帧的Surface
     */
    fun startRealTimeStabilization(inputSurface: SurfaceTexture, outputSurface: Surface) {
        if (realTimeStabilizer == null) {
            realTimeStabilizer = RealTimeStabilizer(context, config)
        }
        
        realTimeStabilizer?.start(inputSurface, outputSurface)
    }
    
    /**
     * 停止实时防抖
     */
    fun stopRealTimeStabilization() {
        realTimeStabilizer?.stop()
    }
    
    /**
     * 对视频文件进行后处理防抖
     * @param inputVideo 输入视频的Uri
     * @param outputFile 输出视频的文件
     * @param params 防抖参数
     * @return 防抖任务
     */
    fun stabilizeVideo(
        inputVideo: Uri,
        outputFile: File,
        params: StabilizationParams
    ): StabilizationTask {
        if (postProcessStabilizer == null) {
            postProcessStabilizer = PostProcessStabilizer(context, config)
        }
        
        return postProcessStabilizer!!.stabilize(inputVideo, outputFile, params, stabilizationListener)
    }
    
    /**
     * 设置防抖监听器
     * @param listener 监听器
     */
    fun setStabilizationListener(listener: StabilizationListener) {
        this.stabilizationListener = listener
    }
    
    /**
     * 释放资源
     */
    fun release() {
        realTimeStabilizer?.release()
        realTimeStabilizer = null
        
        postProcessStabilizer?.release()
        postProcessStabilizer = null
        
        stabilizationListener = null
    }
    
    companion object {
        /**
         * 初始化视频防抖SDK
         * @param context 上下文
         * @param config 配置
         * @return VideoStabilizer实例
         */
        @JvmStatic
        fun init(context: Context, config: StabilizerConfig): VideoStabilizer {
            return VideoStabilizer(context.applicationContext, config)
        }
    }
}
