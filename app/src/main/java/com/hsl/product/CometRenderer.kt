package com.hsl.product

import android.content.Context
import android.graphics.PointF // 导入 PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// CometRenderer 类，实现 GLSurfaceView.Renderer 接口，负责渲染彗星
class CometRenderer(private val context: Context, private val samplePath: List<PointF>? = null) : GLSurfaceView.Renderer {

    private lateinit var comet: Comet // 彗星对象
    private val projectionMatrix = FloatArray(16) // 投影矩阵

    // --- 动画计时 ---
    private var lastFrameTime: Long = 0 // 上一帧的时间戳 (毫秒)

    // --- 绘制进度控制 ---
    private var drawProgress: Float = 0.0f // 绘制进度 (0.0 到 1.0)

    // --- 坐标系控制 ---
    private var showCoordinateSystem: Boolean = true // 是否显示坐标系

    // 当 Surface 创建时调用
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // 设置清屏颜色为黑色

        // 使用从Activity传入的路径或默认路径
        val path = samplePath ?: listOf(
            PointF(-0.8f, 0.0f),
            PointF(-0.4f, 0.5f),
            PointF(0.0f, 0.8f),
            PointF(0.4f, 0.5f),
            PointF(0.8f, 0.0f)
        )

        // 在这里初始化 Comet 对象，传入路径
        comet = Comet(path)

        // 设置坐标系的初始显示状态
        comet.setCoordinateSystemVisible(showCoordinateSystem)

        // 初始化上一帧时间戳
        lastFrameTime = System.currentTimeMillis()
    }

    // 当 Surface 尺寸改变时调用
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 计算保持宽高比例为1:1的视口大小
        val size = Math.min(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2

        // 设置正方形视口，确保X轴和Y轴的比例相同
        GLES20.glViewport(x, y, size, size)

        // 使用正交投影，确保X轴和Y轴的坐标范围相同
        android.opengl.Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)

        // 对于2D场景，我们不需要视图矩阵，直接使用投影矩阵
    }

    // 每帧绘制时调用
    override fun onDrawFrame(gl: GL10?) {
        // --- 计算时间差 (deltaTime) ---
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastFrameTime) / 1000.0f // 转换为秒
        lastFrameTime = currentTime

        // --- 更新彗星动画 ---
        comet.update(deltaTime)

        // 清除屏幕
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 绘制彗星，传入进度控制参数
        comet.draw(drawProgress)

        // 绘制后检查 OpenGL 错误
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            android.util.Log.e("CometRenderer", "OpenGL Error after draw: " + error)
            error = GLES20.glGetError() // 检查后续错误
        }

        // 请求重绘以实现动画
        // (在 MainActivity 中渲染模式设置为 RENDERMODE_CONTINUOUSLY)
    }

    // 设置绘制进度 (0.0 到 1.0)
    fun setDrawProgress(progress: Float) {
        drawProgress = progress.coerceIn(0.0f, 1.0f) // 确保值在有效范围内
    }

    // 设置坐标系的显示状态
    fun setCoordinateSystemVisible(visible: Boolean) {
        showCoordinateSystem = visible
        // 如果comet已经初始化，则更新其坐标系显示状态
        if (::comet.isInitialized) {
            comet.setCoordinateSystemVisible(visible)
        }
    }

    // 获取坐标系的显示状态
    fun isCoordinateSystemVisible(): Boolean {
        return showCoordinateSystem
    }

    companion object {
        // 加载着色器
        fun loadShader(type: Int, shaderCode: String): Int {
            // 创建着色器对象
            val shader = GLES20.glCreateShader(type)
            // 加载着色器源代码
            GLES20.glShaderSource(shader, shaderCode)
            // 编译着色器
            GLES20.glCompileShader(shader)

            // 检查编译状态
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) { // 如果编译失败
                val errorLog = GLES20.glGetShaderInfoLog(shader) // 获取错误日志
                android.util.Log.e("CometRenderer", "Shader compilation failed: $errorLog") // 打印错误日志
                GLES20.glDeleteShader(shader) // 删除着色器对象
                return 0 // 返回 0 表示失败
            }
            return shader // 返回着色器句柄
        }
    }
}