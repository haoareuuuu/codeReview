package com.hsl.videstabilization.util

import android.graphics.Matrix
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 矩阵工具类
 * 提供矩阵操作的辅助方法
 */
object MatrixUtils {
    /**
     * 检查变换矩阵是否有效
     * @param matrix 变换矩阵
     * @return 是否有效
     */
    fun isValidTransform(matrix: Matrix): Boolean {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        // 检查矩阵是否包含NaN或Infinity
        for (value in values) {
            if (value.isNaN() || value.isInfinite()) {
                return false
            }
        }
        
        // 检查矩阵是否可逆
        return try {
            val inverse = Matrix()
            matrix.invert(inverse)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 限制变换矩阵的范围
     * @param matrix 变换矩阵
     * @param maxTranslation 最大平移量（相对于图像尺寸的比例）
     * @param maxScale 最大缩放比例
     * @param maxRotation 最大旋转角度（度）
     * @return 限制后的变换矩阵
     */
    fun constrainTransform(
        matrix: Matrix,
        maxTranslation: Float,
        maxScale: Float,
        maxRotation: Float
    ): Matrix {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        // 提取变换参数
        val scaleX = sqrt(values[0] * values[0] + values[3] * values[3])
        val scaleY = sqrt(values[1] * values[1] + values[4] * values[4])
        val rotation = atan2(values[1], values[0]) * 180 / Math.PI
        val translationX = values[2]
        val translationY = values[5]
        
        // 限制缩放
        val constrainedScaleX = constrainValue(scaleX, 1.0f - maxScale, 1.0f + maxScale)
        val constrainedScaleY = constrainValue(scaleY, 1.0f - maxScale, 1.0f + maxScale)
        
        // 限制旋转
        val constrainedRotation = constrainValue(rotation.toFloat(), -maxRotation, maxRotation)
        
        // 限制平移
        val constrainedTranslationX = constrainValue(translationX, -maxTranslation, maxTranslation)
        val constrainedTranslationY = constrainValue(translationY, -maxTranslation, maxTranslation)
        
        // 创建新的变换矩阵
        val result = Matrix()
        
        // 应用缩放和旋转
        val rotationRad = constrainedRotation * Math.PI / 180
        val cosTheta = cos(rotationRad).toFloat()
        val sinTheta = sin(rotationRad).toFloat()
        
        values[0] = constrainedScaleX * cosTheta
        values[1] = constrainedScaleX * sinTheta
        values[3] = -constrainedScaleY * sinTheta
        values[4] = constrainedScaleY * cosTheta
        
        // 应用平移
        values[2] = constrainedTranslationX
        values[5] = constrainedTranslationY
        
        result.setValues(values)
        return result
    }
    
    /**
     * 限制值的范围
     * @param value 值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值
     */
    private fun constrainValue(value: Float, min: Float, max: Float): Float {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }
    
    /**
     * 提取变换矩阵的参数
     * @param matrix 变换矩阵
     * @return 变换参数数组 [scaleX, scaleY, rotation, translationX, translationY]
     */
    fun extractTransformParams(matrix: Matrix): FloatArray {
        val values = FloatArray(9)
        matrix.getValues(values)
        
        // 提取变换参数
        val scaleX = sqrt(values[0] * values[0] + values[3] * values[3])
        val scaleY = sqrt(values[1] * values[1] + values[4] * values[4])
        val rotation = atan2(values[1], values[0]) * 180 / Math.PI
        val translationX = values[2]
        val translationY = values[5]
        
        return floatArrayOf(scaleX.toFloat(), scaleY.toFloat(), rotation.toFloat(), translationX, translationY)
    }
    
    /**
     * 创建变换矩阵
     * @param scaleX X轴缩放
     * @param scaleY Y轴缩放
     * @param rotation 旋转角度（度）
     * @param translationX X轴平移
     * @param translationY Y轴平移
     * @return 变换矩阵
     */
    fun createTransformMatrix(
        scaleX: Float,
        scaleY: Float,
        rotation: Float,
        translationX: Float,
        translationY: Float
    ): Matrix {
        val matrix = Matrix()
        
        // 应用缩放
        matrix.postScale(scaleX, scaleY)
        
        // 应用旋转
        matrix.postRotate(rotation)
        
        // 应用平移
        matrix.postTranslate(translationX, translationY)
        
        return matrix
    }
    
    /**
     * 计算两个变换矩阵的加权平均
     * @param matrix1 第一个矩阵
     * @param matrix2 第二个矩阵
     * @param weight1 第一个矩阵的权重
     * @return 加权平均后的矩阵
     */
    fun weightedAverage(matrix1: Matrix, matrix2: Matrix, weight1: Float): Matrix {
        val weight2 = 1.0f - weight1
        
        // 提取两个矩阵的参数
        val params1 = extractTransformParams(matrix1)
        val params2 = extractTransformParams(matrix2)
        
        // 计算加权平均
        val avgScaleX = params1[0] * weight1 + params2[0] * weight2
        val avgScaleY = params1[1] * weight1 + params2[1] * weight2
        val avgRotation = params1[2] * weight1 + params2[2] * weight2
        val avgTranslationX = params1[3] * weight1 + params2[3] * weight2
        val avgTranslationY = params1[4] * weight1 + params2[4] * weight2
        
        // 创建新的变换矩阵
        return createTransformMatrix(
            avgScaleX,
            avgScaleY,
            avgRotation,
            avgTranslationX,
            avgTranslationY
        )
    }
    
    /**
     * 计算变换矩阵的逆矩阵
     * @param matrix 变换矩阵
     * @return 逆矩阵，如果不可逆则返回单位矩阵
     */
    fun inverse(matrix: Matrix): Matrix {
        val result = Matrix()
        if (matrix.invert(result)) {
            return result
        }
        return Matrix() // 返回单位矩阵
    }
    
    /**
     * 应用变换矩阵到矩形
     * @param rect 矩形
     * @param matrix 变换矩阵
     * @return 变换后的矩形
     */
    fun mapRect(rect: RectF, matrix: Matrix): RectF {
        val result = RectF(rect)
        matrix.mapRect(result)
        return result
    }
}
