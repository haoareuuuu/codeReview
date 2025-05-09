package com.hsl.videstabilization.util

import android.util.Log
import org.opencv.core.Mat

/**
 * OpenCV JNI接口类
 * 提供与C++层OpenCV功能的交互
 */
class OpenCVJNI {
    companion object {
        private const val TAG = "OpenCVJNI"
        
        // 加载本地库
        init {
            try {
                System.loadLibrary("videstabilization")
                Log.d(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
        
        /**
         * 获取OpenCV版本
         * @return OpenCV版本字符串
         */
        @JvmStatic
        external fun getOpenCVVersion(): String
        
        /**
         * 将RGBA图像转换为灰度图像
         * @param matAddrRgba RGBA图像的Mat地址
         * @param matAddrGray 灰度图像的Mat地址
         * @return 是否转换成功
         */
        @JvmStatic
        external fun convertToGray(matAddrRgba: Long, matAddrGray: Long): Boolean
    }
}
