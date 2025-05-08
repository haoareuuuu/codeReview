package com.hsl.videstabilization.core.realtime

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
import android.view.Surface
import com.hsl.videstabilization.api.StabilizerConfig
import com.hsl.videstabilization.core.realtime.StabilizationRenderer
import com.hsl.videstabilization.sensor.SensorDataCollector

/**
 * 实时视频防抖处理器
 */
class RealTimeStabilizer(
    private val context: Context,
    private val config: StabilizerConfig
) {
    // OpenGL渲染器
    private var renderer: StabilizationRenderer? = null

    // 传感器数据收集器
    private var sensorCollector: SensorDataCollector? = null

    // 是否正在运行
    private var isRunning = false

    /**
     * 初始化
     */
    private fun initialize() {
        // 初始化渲染器
        renderer = StabilizationRenderer(context, config)

        // 如果使用传感器融合，初始化传感器收集器
        if (config.useSensorFusion) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorCollector = SensorDataCollector(sensorManager)
        }
    }

    /**
     * 开始实时防抖
     * @param inputSurface 输入视频帧的SurfaceTexture
     * @param outputSurface 输出视频帧的Surface
     */
    fun start(inputSurface: SurfaceTexture, outputSurface: Surface) {
        if (isRunning) {
            return
        }

        if (renderer == null) {
            initialize()
        }

        // 设置输入和输出Surface
        renderer?.setSurfaces(inputSurface, outputSurface)

        // 开始渲染
        renderer?.start()

        // 开始收集传感器数据
        sensorCollector?.start()

        isRunning = true
    }

    /**
     * 停止实时防抖
     */
    fun stop() {
        if (!isRunning) {
            return
        }

        // 停止渲染
        renderer?.stop()

        // 停止收集传感器数据
        sensorCollector?.stop()

        isRunning = false
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()

        renderer?.release()
        renderer = null

        sensorCollector?.release()
        sensorCollector = null
    }
}
