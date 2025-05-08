package com.hsl.videstabilization.api

/**
 * 视频防抖配置类
 */
class StabilizerConfig private constructor(
    val stabilizationStrength: Float,
    val borderPolicy: BorderPolicy,
    val algorithmType: AlgorithmType,
    val performanceMode: PerformanceMode,
    val useGpuAcceleration: Boolean,
    val useSensorFusion: Boolean
) {
    /**
     * 构建器类
     */
    class Builder {
        private var stabilizationStrength: Float = 0.5f
        private var borderPolicy: BorderPolicy = BorderPolicy.CROP
        private var algorithmType: AlgorithmType = AlgorithmType.FEATURE_BASED
        private var performanceMode: PerformanceMode = PerformanceMode.BALANCED
        private var useGpuAcceleration: Boolean = true
        private var useSensorFusion: Boolean = true
        
        /**
         * 设置防抖强度
         * @param strength 强度值，范围0.0-1.0，值越大防抖效果越强
         */
        fun setStabilizationStrength(strength: Float): Builder {
            this.stabilizationStrength = strength.coerceIn(0.0f, 1.0f)
            return this
        }
        
        /**
         * 设置边缘处理策略
         * @param policy 边缘处理策略
         */
        fun setBorderPolicy(policy: BorderPolicy): Builder {
            this.borderPolicy = policy
            return this
        }
        
        /**
         * 设置算法类型
         * @param type 算法类型
         */
        fun setAlgorithmType(type: AlgorithmType): Builder {
            this.algorithmType = type
            return this
        }
        
        /**
         * 设置性能模式
         * @param mode 性能模式
         */
        fun setPerformanceMode(mode: PerformanceMode): Builder {
            this.performanceMode = mode
            return this
        }
        
        /**
         * 设置是否使用GPU加速
         * @param use 是否使用
         */
        fun useGpuAcceleration(use: Boolean): Builder {
            this.useGpuAcceleration = use
            return this
        }
        
        /**
         * 设置是否使用传感器融合
         * @param use 是否使用
         */
        fun useSensorFusion(use: Boolean): Builder {
            this.useSensorFusion = use
            return this
        }
        
        /**
         * 构建配置
         * @return 配置实例
         */
        fun build(): StabilizerConfig {
            return StabilizerConfig(
                stabilizationStrength,
                borderPolicy,
                algorithmType,
                performanceMode,
                useGpuAcceleration,
                useSensorFusion
            )
        }
    }
}

/**
 * 边缘处理策略
 */
enum class BorderPolicy {
    /**
     * 裁剪边缘
     */
    CROP,
    
    /**
     * 填充边缘
     */
    FILL,
    
    /**
     * 变形边缘
     */
    DEFORM
}

/**
 * 算法类型
 */
enum class AlgorithmType {
    /**
     * 基于特征点的防抖
     */
    FEATURE_BASED,
    
    /**
     * 基于光流的防抖
     */
    OPTICAL_FLOW,
    
    /**
     * 基于传感器的防抖
     */
    SENSOR_BASED,
    
    /**
     * 混合防抖
     */
    HYBRID
}

/**
 * 性能模式
 */
enum class PerformanceMode {
    /**
     * 高质量模式
     */
    HIGH_QUALITY,
    
    /**
     * 平衡模式
     */
    BALANCED,
    
    /**
     * 高性能模式
     */
    HIGH_PERFORMANCE
}
