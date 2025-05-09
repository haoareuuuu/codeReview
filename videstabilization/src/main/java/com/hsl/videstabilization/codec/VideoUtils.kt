package com.hsl.videstabilization.codec

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.Size
import java.io.IOException

/**
 * 视频工具类
 * 提供视频相关的工具方法
 */
object VideoUtils {
    private const val TAG = "VideoUtils"
    
    /**
     * 获取视频信息
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频信息
     */
    fun getVideoInfo(context: Context, uri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            // 获取视频宽度
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            
            // 获取视频高度
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            // 获取视频时长（毫秒）
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            
            // 获取视频旋转角度
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            // 获取视频比特率
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            
            // 获取视频帧率
            val frameRate = getVideoFrameRate(context, uri)
            
            // 获取视频MIME类型
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            
            return VideoInfo(
                width = width,
                height = height,
                duration = duration,
                rotation = rotation,
                bitrate = bitrate,
                frameRate = frameRate,
                mimeType = mimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video info", e)
            return null
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 获取视频帧率
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频帧率
     */
    fun getVideoFrameRate(context: Context, uri: Uri): Int {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            
            // 查找视频轨道
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    // 获取帧率
                    return if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        format.getInteger(MediaFormat.KEY_FRAME_RATE)
                    } else {
                        30 // 默认帧率
                    }
                }
            }
            
            return 30 // 默认帧率
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video frame rate", e)
            return 30 // 默认帧率
        } finally {
            extractor.release()
        }
    }
    
    /**
     * 获取视频格式
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频格式
     */
    fun getVideoFormat(context: Context, uri: Uri): MediaFormat? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            
            // 查找视频轨道
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    return format
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video format", e)
            return null
        } finally {
            extractor.release()
        }
    }
    
    /**
     * 检查视频是否有效
     * @param context 上下文
     * @param uri 视频Uri
     * @return 是否有效
     */
    fun isValidVideo(context: Context, uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            // 检查是否有视频轨道
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            
            // 检查视频宽度和高度
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            return hasVideo && width > 0 && height > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check video validity", e)
            return false
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 获取视频尺寸
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频尺寸
     */
    fun getVideoSize(context: Context, uri: Uri): Size? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            // 获取视频宽度和高度
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            
            // 获取视频旋转角度
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            
            // 根据旋转角度调整宽高
            return if (rotation == 90 || rotation == 270) {
                Size(height, width)
            } else {
                Size(width, height)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video size", e)
            return null
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 获取视频时长（毫秒）
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频时长
     */
    fun getVideoDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            // 获取视频时长
            return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video duration", e)
            return 0
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 获取视频帧数
     * @param context 上下文
     * @param uri 视频Uri
     * @return 视频帧数
     */
    fun getVideoFrameCount(context: Context, uri: Uri): Int {
        // 获取视频时长（毫秒）
        val duration = getVideoDuration(context, uri)
        
        // 获取视频帧率
        val frameRate = getVideoFrameRate(context, uri)
        
        // 计算帧数
        return ((duration / 1000.0) * frameRate).toInt()
    }
    
    /**
     * 视频信息类
     */
    data class VideoInfo(
        val width: Int,
        val height: Int,
        val duration: Long,
        val rotation: Int,
        val bitrate: Int,
        val frameRate: Int,
        val mimeType: String
    ) {
        /**
         * 获取视频尺寸
         * @return 视频尺寸
         */
        fun getSize(): Size {
            return if (rotation == 90 || rotation == 270) {
                Size(height, width)
            } else {
                Size(width, height)
            }
        }
        
        /**
         * 获取视频时长（秒）
         * @return 视频时长
         */
        fun getDurationInSeconds(): Float {
            return duration / 1000.0f
        }
        
        /**
         * 获取视频帧数
         * @return 视频帧数
         */
        fun getFrameCount(): Int {
            return ((duration / 1000.0) * frameRate).toInt()
        }
        
        /**
         * 获取视频比特率（Mbps）
         * @return 视频比特率
         */
        fun getBitrateInMbps(): Float {
            return bitrate / 1000000.0f
        }
        
        override fun toString(): String {
            return "VideoInfo(width=$width, height=$height, duration=${getDurationInSeconds()}s, " +
                    "rotation=$rotation, bitrate=${getBitrateInMbps()}Mbps, frameRate=$frameRate, " +
                    "mimeType='$mimeType')"
        }
    }
}
