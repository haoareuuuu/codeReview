package com.hsl.videstabilization.api

/**
 * 视频防抖参数类
 */
class StabilizationParams private constructor(
    val outputWidth: Int,
    val outputHeight: Int,
    val outputFrameRate: Int,
    val outputBitRate: Int,
    val stabilizationStrength: Float,
    val keyFrameInterval: Int,
    val useHardwareEncoder: Boolean
) {
    /**
     * 构建器类
     */
    class Builder {
        private var outputWidth: Int = -1
        private var outputHeight: Int = -1
        private var outputFrameRate: Int = 30
        private var outputBitRate: Int = 8000000 // 8Mbps
        private var stabilizationStrength: Float = 0.5f
        private var keyFrameInterval: Int = 1 // 每秒一个关键帧
        private var useHardwareEncoder: Boolean = true
        
        /**
         * 设置输出分辨率
         * @param width 宽度
         * @param height 高度
         */
        fun setOutputResolution(width: Int, height: Int): Builder {
            this.outputWidth = width
            this.outputHeight = height
            return this
        }
        
        /**
         * 设置输出帧率
         * @param frameRate 帧率
         */
        fun setOutputFrameRate(frameRate: Int): Builder {
            this.outputFrameRate = frameRate
            return this
        }
        
        /**
         * 设置输出比特率
         * @param bitRate 比特率
         */
        fun setOutputBitRate(bitRate: Int): Builder {
            this.outputBitRate = bitRate
            return this
        }
        
        /**
         * 设置防抖强度
         * @param strength 强度值，范围0.0-1.0
         */
        fun setStabilizationStrength(strength: Float): Builder {
            this.stabilizationStrength = strength.coerceIn(0.0f, 1.0f)
            return this
        }
        
        /**
         * 设置关键帧间隔
         * @param interval 间隔（秒）
         */
        fun setKeyFrameInterval(interval: Int): Builder {
            this.keyFrameInterval = interval
            return this
        }
        
        /**
         * 设置是否使用硬件编码器
         * @param use 是否使用
         */
        fun useHardwareEncoder(use: Boolean): Builder {
            this.useHardwareEncoder = use
            return this
        }
        
        /**
         * 构建参数
         * @return 参数实例
         */
        fun build(): StabilizationParams {
            return StabilizationParams(
                outputWidth,
                outputHeight,
                outputFrameRate,
                outputBitRate,
                stabilizationStrength,
                keyFrameInterval,
                useHardwareEncoder
            )
        }
    }
}
