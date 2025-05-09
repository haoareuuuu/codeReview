package com.hsl.videstabilization.codec

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * 视频格式转换工具类
 * 提供各种视频格式之间的转换方法
 */
object VideoFormatConverter {
    private const val TAG = "VideoFormatConverter"
    
    /**
     * 将Bitmap转换为NV21格式的字节数组
     * @param bitmap 输入Bitmap
     * @return NV21格式的字节数组
     */
    fun bitmapToNV21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        
        // 创建OpenCV的Mat对象
        val rgbaMat = Mat(height, width, CvType.CV_8UC4)
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        
        // 将Bitmap转换为RGBA Mat
        Utils.bitmapToMat(bitmap, rgbaMat)
        
        // 将RGBA转换为YUV
        Imgproc.cvtColor(rgbaMat, yuvMat, Imgproc.COLOR_RGBA2YUV_I420)
        
        // 将YUV Mat转换为NV21字节数组
        val nv21 = ByteArray(size + size / 2)
        yuvMat.get(0, 0, nv21)
        
        // 释放Mat资源
        rgbaMat.release()
        yuvMat.release()
        
        return nv21
    }
    
    /**
     * 将NV21格式的字节数组转换为Bitmap
     * @param data NV21格式的字节数组
     * @param width 宽度
     * @param height 高度
     * @return Bitmap
     */
    fun nv21ToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        // 创建OpenCV的Mat对象
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        val rgbaMat = Mat(height, width, CvType.CV_8UC4)
        
        // 将NV21字节数组转换为YUV Mat
        yuvMat.put(0, 0, data)
        
        // 将YUV转换为RGBA
        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_NV21)
        
        // 创建Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 将RGBA Mat转换为Bitmap
        Utils.matToBitmap(rgbaMat, bitmap)
        
        // 释放Mat资源
        yuvMat.release()
        rgbaMat.release()
        
        return bitmap
    }
    
    /**
     * 将Bitmap转换为I420格式的字节数组
     * @param bitmap 输入Bitmap
     * @return I420格式的字节数组
     */
    fun bitmapToI420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        
        // 创建OpenCV的Mat对象
        val rgbaMat = Mat(height, width, CvType.CV_8UC4)
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        
        // 将Bitmap转换为RGBA Mat
        Utils.bitmapToMat(bitmap, rgbaMat)
        
        // 将RGBA转换为YUV
        Imgproc.cvtColor(rgbaMat, yuvMat, Imgproc.COLOR_RGBA2YUV_I420)
        
        // 将YUV Mat转换为I420字节数组
        val i420 = ByteArray(size + size / 2)
        yuvMat.get(0, 0, i420)
        
        // 释放Mat资源
        rgbaMat.release()
        yuvMat.release()
        
        return i420
    }
    
    /**
     * 将I420格式的字节数组转换为Bitmap
     * @param data I420格式的字节数组
     * @param width 宽度
     * @param height 高度
     * @return Bitmap
     */
    fun i420ToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        // 创建OpenCV的Mat对象
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        val rgbaMat = Mat(height, width, CvType.CV_8UC4)
        
        // 将I420字节数组转换为YUV Mat
        yuvMat.put(0, 0, data)
        
        // 将YUV转换为RGBA
        Imgproc.cvtColor(yuvMat, rgbaMat, Imgproc.COLOR_YUV2RGBA_I420)
        
        // 创建Bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // 将RGBA Mat转换为Bitmap
        Utils.matToBitmap(rgbaMat, bitmap)
        
        // 释放Mat资源
        yuvMat.release()
        rgbaMat.release()
        
        return bitmap
    }
    
    /**
     * 调整Bitmap大小
     * @param bitmap 输入Bitmap
     * @param width 目标宽度
     * @param height 目标高度
     * @return 调整大小后的Bitmap
     */
    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) {
            return bitmap
        }
        
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    
    /**
     * 旋转Bitmap
     * @param bitmap 输入Bitmap
     * @param degrees 旋转角度
     * @return 旋转后的Bitmap
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) {
            return bitmap
        }
        
        val matrix = Matrix()
        matrix.postRotate(degrees)
        
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            matrix, true
        )
    }
    
    /**
     * 裁剪Bitmap
     * @param bitmap 输入Bitmap
     * @param rect 裁剪区域
     * @return 裁剪后的Bitmap
     */
    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            rect.left, rect.top, rect.width(), rect.height()
        )
    }
    
    /**
     * 将Bitmap转换为指定颜色格式的ByteBuffer
     * @param bitmap 输入Bitmap
     * @param colorFormat 颜色格式
     * @return ByteBuffer
     */
    fun bitmapToByteBuffer(bitmap: Bitmap, colorFormat: Int): ByteBuffer {
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar -> {
                val i420 = bitmapToI420(bitmap)
                val buffer = ByteBuffer.allocateDirect(i420.size)
                buffer.put(i420)
                buffer.flip()
                return buffer
            }
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar -> {
                val nv21 = bitmapToNV21(bitmap)
                val buffer = ByteBuffer.allocateDirect(nv21.size)
                buffer.put(nv21)
                buffer.flip()
                return buffer
            }
            else -> {
                // 默认使用ARGB格式
                val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(buffer)
                buffer.flip()
                return buffer
            }
        }
    }
    
    /**
     * 创建带边框的Bitmap
     * @param bitmap 输入Bitmap
     * @param borderWidth 边框宽度
     * @param borderColor 边框颜色
     * @return 带边框的Bitmap
     */
    fun createBitmapWithBorder(
        bitmap: Bitmap,
        borderWidth: Int,
        borderColor: Int = Color.BLACK
    ): Bitmap {
        val width = bitmap.width + borderWidth * 2
        val height = bitmap.height + borderWidth * 2
        
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        
        // 绘制边框
        val paint = Paint()
        paint.color = borderColor
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // 绘制原始Bitmap
        canvas.drawBitmap(
            bitmap,
            borderWidth.toFloat(),
            borderWidth.toFloat(),
            null
        )
        
        return result
    }
    
    /**
     * 获取支持的颜色格式
     * @param mimeType 媒体类型
     * @return 支持的颜色格式列表
     */
    fun getSupportedColorFormats(mimeType: String): List<Int> {
        try {
            val encoderInfo = MediaCodecInfo.CodecCapabilities.createFromProfileLevel(
                mimeType,
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                MediaCodecInfo.CodecProfileLevel.AVCLevel41
            )
            
            return encoderInfo.colorFormats.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting supported color formats", e)
            return listOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
        }
    }
    
    /**
     * 从MediaFormat获取视频宽度
     * @param format MediaFormat
     * @return 视频宽度
     */
    fun getVideoWidth(format: MediaFormat): Int {
        return format.getInteger(MediaFormat.KEY_WIDTH)
    }
    
    /**
     * 从MediaFormat获取视频高度
     * @param format MediaFormat
     * @return 视频高度
     */
    fun getVideoHeight(format: MediaFormat): Int {
        return format.getInteger(MediaFormat.KEY_HEIGHT)
    }
    
    /**
     * 从MediaFormat获取视频帧率
     * @param format MediaFormat
     * @return 视频帧率
     */
    fun getVideoFrameRate(format: MediaFormat): Int {
        return format.getInteger(MediaFormat.KEY_FRAME_RATE)
    }
    
    /**
     * 从MediaFormat获取视频比特率
     * @param format MediaFormat
     * @return 视频比特率
     */
    fun getVideoBitRate(format: MediaFormat): Int {
        return format.getInteger(MediaFormat.KEY_BIT_RATE)
    }
    
    /**
     * 从MediaFormat获取视频时长（微秒）
     * @param format MediaFormat
     * @return 视频时长
     */
    fun getVideoDuration(format: MediaFormat): Long {
        return format.getLong(MediaFormat.KEY_DURATION)
    }
}
