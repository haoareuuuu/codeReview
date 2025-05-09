package com.hsl.videstabilization.util

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * OpenCV测试类
 * 用于测试OpenCV JNI接口
 */
class OpenCVTest {
    companion object {
        private const val TAG = "OpenCVTest"
        
        /**
         * 测试OpenCV版本
         * @return OpenCV版本字符串
         */
        fun testOpenCVVersion(): String {
            return try {
                val version = OpenCVJNI.getOpenCVVersion()
                Log.d(TAG, "OpenCV version: $version")
                version
            } catch (e: Exception) {
                Log.e(TAG, "Error getting OpenCV version", e)
                "Error: ${e.message}"
            }
        }
        
        /**
         * 测试灰度转换
         * @param context 上下文
         * @param bitmap 输入位图
         * @return 灰度位图
         */
        fun testGrayConversion(context: Context, bitmap: Bitmap): Bitmap? {
            return try {
                // 初始化OpenCV
                if (!OpenCVUtils.initSync()) {
                    Log.e(TAG, "Failed to initialize OpenCV")
                    return null
                }
                
                // 创建Mat对象
                val rgba = Mat()
                val gray = Mat()
                
                // 将Bitmap转换为Mat
                Utils.bitmapToMat(bitmap, rgba)
                
                // 调用JNI方法进行灰度转换
                val success = OpenCVJNI.convertToGray(rgba.nativeObj, gray.nativeObj)
                
                if (success) {
                    // 创建结果Bitmap
                    val resultBitmap = Bitmap.createBitmap(
                        bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
                    )
                    
                    // 将Mat转换为Bitmap
                    Utils.matToBitmap(gray, resultBitmap)
                    
                    // 释放Mat资源
                    rgba.release()
                    gray.release()
                    
                    resultBitmap
                } else {
                    Log.e(TAG, "Gray conversion failed")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in gray conversion", e)
                null
            }
        }
    }
}
