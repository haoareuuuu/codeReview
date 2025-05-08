package com.hsl.videstabilization.util

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV工具类
 * 用于初始化和管理OpenCV库
 */
object OpenCVUtils {
    private const val TAG = "OpenCVUtils"
    
    // OpenCV是否已初始化
    private var isInitialized = false
    
    /**
     * 初始化OpenCV库
     * @param context 上下文
     * @param callback 初始化完成回调
     */
    fun initAsync(context: Context, callback: (() -> Unit)? = null) {
        if (isInitialized) {
            callback?.invoke()
            return
        }
        
        // 异步初始化OpenCV
        if (OpenCVLoader.initLocal()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization")
            isInitialized = true
            callback?.invoke()
        }
    }
    
    /**
     * 同步初始化OpenCV库
     * @return 是否初始化成功
     */
    fun initSync(): Boolean {
        if (isInitialized) {
            return true
        }
        
        try {
            System.loadLibrary("opencv_java4")
            isInitialized = true
            Log.d(TAG, "OpenCV initialized successfully")
            return true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load OpenCV library", e)
            return false
        }
    }
    
    /**
     * 检查OpenCV是否已初始化
     * @return 是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized
    }
}
