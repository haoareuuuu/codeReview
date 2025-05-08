package com.hsl.videstabilization.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * 传感器数据收集器
 * 用于收集设备的陀螺仪和加速度计数据
 */
class SensorDataCollector(private val sensorManager: SensorManager) : SensorEventListener {
    companion object {
        private const val TAG = "SensorDataCollector"
        
        // 传感器采样率
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME
    }
    
    // 陀螺仪传感器
    private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    // 加速度计传感器
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // 陀螺仪数据
    private val gyroscopeData = FloatArray(3)
    
    // 加速度计数据
    private val accelerometerData = FloatArray(3)
    
    // 时间戳
    private var timestamp: Long = 0
    
    // 传感器数据监听器
    private var listener: SensorDataListener? = null
    
    // 是否正在收集数据
    private var isCollecting = false
    
    /**
     * 开始收集传感器数据
     */
    fun start() {
        if (isCollecting) {
            return
        }
        
        // 注册传感器监听器
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
            Log.d(TAG, "Gyroscope sensor registered")
        } ?: Log.w(TAG, "Gyroscope sensor not available")
        
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
            Log.d(TAG, "Accelerometer sensor registered")
        } ?: Log.w(TAG, "Accelerometer sensor not available")
        
        isCollecting = true
        Log.d(TAG, "Sensor data collection started")
    }
    
    /**
     * 停止收集传感器数据
     */
    fun stop() {
        if (!isCollecting) {
            return
        }
        
        // 注销传感器监听器
        sensorManager.unregisterListener(this)
        
        isCollecting = false
        Log.d(TAG, "Sensor data collection stopped")
    }
    
    /**
     * 设置传感器数据监听器
     * @param listener 监听器
     */
    fun setListener(listener: SensorDataListener) {
        this.listener = listener
    }
    
    /**
     * 获取陀螺仪数据
     * @return 陀螺仪数据数组 [x, y, z]
     */
    fun getGyroscopeData(): FloatArray {
        return gyroscopeData.clone()
    }
    
    /**
     * 获取加速度计数据
     * @return 加速度计数据数组 [x, y, z]
     */
    fun getAccelerometerData(): FloatArray {
        return accelerometerData.clone()
    }
    
    /**
     * 获取时间戳
     * @return 时间戳（纳秒）
     */
    fun getTimestamp(): Long {
        return timestamp
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        listener = null
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                // 更新陀螺仪数据
                System.arraycopy(event.values, 0, gyroscopeData, 0, 3)
                timestamp = event.timestamp
                
                // 通知监听器
                listener?.onGyroscopeDataChanged(gyroscopeData, timestamp)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // 更新加速度计数据
                System.arraycopy(event.values, 0, accelerometerData, 0, 3)
                timestamp = event.timestamp
                
                // 通知监听器
                listener?.onAccelerometerDataChanged(accelerometerData, timestamp)
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // 忽略
    }
    
    /**
     * 传感器数据监听器接口
     */
    interface SensorDataListener {
        /**
         * 陀螺仪数据变化回调
         * @param data 陀螺仪数据数组 [x, y, z]
         * @param timestamp 时间戳（纳秒）
         */
        fun onGyroscopeDataChanged(data: FloatArray, timestamp: Long)
        
        /**
         * 加速度计数据变化回调
         * @param data 加速度计数据数组 [x, y, z]
         * @param timestamp 时间戳（纳秒）
         */
        fun onAccelerometerDataChanged(data: FloatArray, timestamp: Long)
    }
}
