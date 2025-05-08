package com.hsl.videstabilization.algorithm.smooth

/**
 * 运动平滑器工厂类
 * 用于创建不同类型的运动平滑器
 */
object MotionSmootherFactory {
    /**
     * 平滑器类型
     */
    enum class SmootherType {
        /**
         * 卡尔曼滤波平滑器
         */
        KALMAN,
        
        /**
         * 高斯滤波平滑器
         */
        GAUSSIAN,
        
        /**
         * 自适应滤波平滑器
         */
        ADAPTIVE
    }
    
    /**
     * 创建运动平滑器
     * @param type 平滑器类型
     * @param windowSize 平滑窗口大小
     * @param smoothingStrength 平滑强度，范围0.0-1.0
     * @return 运动平滑器实例
     */
    fun createSmoother(
        type: SmootherType,
        windowSize: Int = 30,
        smoothingStrength: Float = 0.5f
    ): MotionSmoother {
        val smoother = when (type) {
            SmootherType.KALMAN -> KalmanSmoother()
            SmootherType.GAUSSIAN -> GaussianSmoother()
            SmootherType.ADAPTIVE -> AdaptiveSmoother()
        }
        
        smoother.initialize(windowSize, smoothingStrength)
        return smoother
    }
}
