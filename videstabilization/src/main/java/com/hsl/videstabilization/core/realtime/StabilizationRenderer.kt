package com.hsl.videstabilization.core.realtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import android.view.Surface
import com.hsl.videstabilization.algorithm.motion.MotionEstimator
import com.hsl.videstabilization.algorithm.motion.MotionEstimatorFactory
import com.hsl.videstabilization.algorithm.smooth.MotionSmoother
import com.hsl.videstabilization.algorithm.smooth.MotionSmootherFactory
import com.hsl.videstabilization.api.StabilizerConfig
import com.hsl.videstabilization.sensor.SensorDataCollector
import com.hsl.videstabilization.util.OpenCVUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 稳定渲染器
 * 用于实时渲染稳定后的视频帧
 */
class StabilizationRenderer(
    private val context: Context,
    private val config: StabilizerConfig
) : GLSurfaceView.Renderer, SensorDataCollector.SensorDataListener {
    companion object {
        private const val TAG = "StabilizationRenderer"
        
        // 顶点着色器
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """
        
        // 片段着色器
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTexCoord);
            }
        """
        
        // 顶点坐标
        private val VERTEX_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f,  // 左下
            1.0f, -1.0f, 0.0f,   // 右下
            -1.0f, 1.0f, 0.0f,   // 左上
            1.0f, 1.0f, 0.0f     // 右上
        )
        
        // 纹理坐标
        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 0.0f,  // 左下
            1.0f, 0.0f,  // 右下
            0.0f, 1.0f,  // 左上
            1.0f, 1.0f   // 右上
        )
    }
    
    // 输入SurfaceTexture
    private var inputSurfaceTexture: SurfaceTexture? = null
    
    // 输出Surface
    private var outputSurface: Surface? = null
    
    // 纹理ID
    private var textureId = 0
    
    // 程序ID
    private var programId = 0
    
    // 位置句柄
    private var positionHandle = 0
    
    // 纹理坐标句柄
    private var texCoordHandle = 0
    
    // MVP矩阵句柄
    private var mvpMatrixHandle = 0
    
    // ST矩阵句柄
    private var stMatrixHandle = 0
    
    // 纹理句柄
    private var textureHandle = 0
    
    // 顶点缓冲区
    private lateinit var vertexBuffer: FloatBuffer
    
    // 纹理坐标缓冲区
    private lateinit var texCoordBuffer: FloatBuffer
    
    // MVP矩阵
    private val mvpMatrix = FloatArray(16)
    
    // ST矩阵
    private val stMatrix = FloatArray(16)
    
    // 运动估计器
    private var motionEstimator: MotionEstimator? = null
    
    // 运动平滑器
    private var motionSmoother: MotionSmoother? = null
    
    // 前一帧
    private var prevFrame: Bitmap? = null
    
    // 是否正在运行
    private val isRunning = AtomicBoolean(false)
    
    // 帧计数器
    private var frameCount = 0
    
    // 上一次更新时间
    private var lastUpdateTime = 0L
    
    // 帧率
    private var fps = 0f
    
    /**
     * 初始化
     */
    private fun initialize() {
        // 初始化OpenCV
        OpenCVUtils.initSync()
        
        // 初始化运动估计器
        motionEstimator = MotionEstimatorFactory.createMotionEstimator(config.algorithmType)
        
        // 初始化运动平滑器
        motionSmoother = MotionSmootherFactory.createSmoother(
            MotionSmootherFactory.SmootherType.GAUSSIAN,
            30,
            config.stabilizationStrength
        )
        
        // 初始化缓冲区
        initBuffers()
        
        Log.d(TAG, "Stabilization renderer initialized")
    }
    
    /**
     * 初始化缓冲区
     */
    private fun initBuffers() {
        // 创建顶点缓冲区
        val vbb = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
        vbb.order(ByteOrder.nativeOrder())
        vertexBuffer = vbb.asFloatBuffer()
        vertexBuffer.put(VERTEX_COORDS)
        vertexBuffer.position(0)
        
        // 创建纹理坐标缓冲区
        val tbb = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
        tbb.order(ByteOrder.nativeOrder())
        texCoordBuffer = tbb.asFloatBuffer()
        texCoordBuffer.put(TEXTURE_COORDS)
        texCoordBuffer.position(0)
        
        // 初始化矩阵
        android.opengl.Matrix.setIdentityM(mvpMatrix, 0)
        android.opengl.Matrix.setIdentityM(stMatrix, 0)
    }
    
    /**
     * 设置输入和输出Surface
     * @param inputSurface 输入SurfaceTexture
     * @param outputSurface 输出Surface
     */
    fun setSurfaces(inputSurface: SurfaceTexture, outputSurface: Surface) {
        this.inputSurfaceTexture = inputSurface
        this.outputSurface = outputSurface
    }
    
    /**
     * 开始渲染
     */
    fun start() {
        if (isRunning.getAndSet(true)) {
            return
        }
        
        Log.d(TAG, "Stabilization renderer started")
    }
    
    /**
     * 停止渲染
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }
        
        Log.d(TAG, "Stabilization renderer stopped")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        
        motionEstimator?.release()
        motionSmoother?.release()
        
        motionEstimator = null
        motionSmoother = null
        
        prevFrame?.recycle()
        prevFrame = null
        
        Log.d(TAG, "Stabilization renderer released")
    }
    
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // 设置清屏颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // 创建程序
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        
        // 获取句柄
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        stMatrixHandle = GLES20.glGetUniformLocation(programId, "uSTMatrix")
        textureHandle = GLES20.glGetUniformLocation(programId, "sTexture")
        
        // 创建纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        
        // 绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        
        // 设置纹理参数
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        // 初始化
        initialize()
    }
    
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        // 设置视口
        GLES20.glViewport(0, 0, width, height)
        
        // 初始化运动估计器
        motionEstimator?.initialize(width, height)
        
        Log.d(TAG, "Surface changed: $width x $height")
    }
    
    override fun onDrawFrame(gl: GL10) {
        if (!isRunning.get() || inputSurfaceTexture == null || outputSurface == null) {
            return
        }
        
        // 清屏
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        // 更新纹理
        inputSurfaceTexture!!.updateTexImage()
        
        // 获取变换矩阵
        inputSurfaceTexture!!.getTransformMatrix(stMatrix)
        
        // 使用程序
        GLES20.glUseProgram(programId)
        
        // 设置顶点坐标
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)
        
        // 设置纹理坐标
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        
        // 设置纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        
        // 设置矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
        
        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        
        // 计算帧率
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= 1000) {
            fps = frameCount * 1000f / (currentTime - lastUpdateTime)
            frameCount = 0
            lastUpdateTime = currentTime
            Log.d(TAG, "FPS: $fps")
        }
    }
    
    /**
     * 创建程序
     * @param vertexShader 顶点着色器代码
     * @param fragmentShader 片段着色器代码
     * @return 程序ID
     */
    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        // 编译着色器
        val vertexShaderId = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragmentShaderId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        
        // 创建程序
        val programId = GLES20.glCreateProgram()
        
        // 附加着色器
        GLES20.glAttachShader(programId, vertexShaderId)
        GLES20.glAttachShader(programId, fragmentShaderId)
        
        // 链接程序
        GLES20.glLinkProgram(programId)
        
        // 检查链接状态
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw RuntimeException("Could not link program: $info")
        }
        
        // 删除着色器
        GLES20.glDeleteShader(vertexShaderId)
        GLES20.glDeleteShader(fragmentShaderId)
        
        return programId
    }
    
    /**
     * 编译着色器
     * @param type 着色器类型
     * @param shaderCode 着色器代码
     * @return 着色器ID
     */
    private fun compileShader(type: Int, shaderCode: String): Int {
        // 创建着色器
        val shaderId = GLES20.glCreateShader(type)
        
        // 设置着色器源代码
        GLES20.glShaderSource(shaderId, shaderCode)
        
        // 编译着色器
        GLES20.glCompileShader(shaderId)
        
        // 检查编译状态
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetShaderInfoLog(shaderId)
            GLES20.glDeleteShader(shaderId)
            throw RuntimeException("Could not compile shader: $info")
        }
        
        return shaderId
    }
    
    /**
     * 应用变换
     * @param transform 变换矩阵
     */
    private fun applyTransform(transform: Matrix) {
        // 将Android的Matrix转换为OpenGL的矩阵
        val values = FloatArray(9)
        transform.getValues(values)
        
        // 创建4x4矩阵
        val matrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(matrix, 0)
        
        // 设置变换
        matrix[0] = values[0] // scaleX
        matrix[1] = values[3] // skewY
        matrix[4] = values[1] // skewX
        matrix[5] = values[4] // scaleY
        matrix[12] = values[2] // translateX
        matrix[13] = values[5] // translateY
        
        // 应用变换
        android.opengl.Matrix.multiplyMM(mvpMatrix, 0, matrix, 0, mvpMatrix, 0)
    }
    
    override fun onGyroscopeDataChanged(data: FloatArray, timestamp: Long) {
        // 如果使用传感器融合，可以在这里处理陀螺仪数据
        if (config.useSensorFusion && motionEstimator is com.hsl.videstabilization.algorithm.motion.SensorBasedMotionEstimator) {
            (motionEstimator as com.hsl.videstabilization.algorithm.motion.SensorBasedMotionEstimator)
                .setSensorData(data, getAccelerometerData(), timestamp)
        }
    }
    
    override fun onAccelerometerDataChanged(data: FloatArray, timestamp: Long) {
        // 如果使用传感器融合，可以在这里处理加速度计数据
        // 已在onGyroscopeDataChanged中处理
    }
    
    /**
     * 获取加速度计数据
     * @return 加速度计数据
     */
    private fun getAccelerometerData(): FloatArray {
        // 这里应该从SensorDataCollector获取加速度计数据
        // 简化起见，返回空数组
        return FloatArray(3)
    }
}
