package com.hsl.videstabilization.algorithm.motion

import com.hsl.videstabilization.api.AlgorithmType

/**
 * 运动估计器工厂类
 * 用于创建不同类型的运动估计器
 */
object MotionEstimatorFactory {
    /**
     * 创建运动估计器
     * @param type 算法类型
     * @return 运动估计器实例
     */
    fun createMotionEstimator(type: AlgorithmType): MotionEstimator {
        return when (type) {
            AlgorithmType.FEATURE_BASED -> FeatureBasedMotionEstimator()
            AlgorithmType.OPTICAL_FLOW -> OpticalFlowMotionEstimator()
            AlgorithmType.SENSOR_BASED -> SensorBasedMotionEstimator()
            AlgorithmType.HYBRID -> HybridMotionEstimator()
        }
    }
}
