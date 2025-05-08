package com.hsl.videstabilization.api

import android.net.Uri

/**
 * 视频防抖监听器接口
 */
interface StabilizationListener {
    /**
     * 进度更新回调
     * @param progress 进度值，范围0.0-1.0
     */
    fun onProgressUpdate(progress: Float)
    
    /**
     * 完成回调
     * @param outputUri 输出视频的Uri
     */
    fun onComplete(outputUri: Uri)
    
    /**
     * 错误回调
     * @param error 错误信息
     */
    fun onError(error: StabilizationError)
}

/**
 * 视频防抖错误类
 */
class StabilizationError(
    val code: Int,
    val message: String,
    val cause: Throwable? = null
) {
    companion object {
        // 错误码定义
        const val ERROR_INVALID_INPUT = 1001
        const val ERROR_INVALID_OUTPUT = 1002
        const val ERROR_CODEC_NOT_FOUND = 1003
        const val ERROR_CODEC_FAILED = 1004
        const val ERROR_INSUFFICIENT_MEMORY = 1005
        const val ERROR_PROCESSING_FAILED = 1006
        const val ERROR_UNKNOWN = 9999
    }
}
