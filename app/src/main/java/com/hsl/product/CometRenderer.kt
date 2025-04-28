package com.hsl.product

import android.content.Context
import android.graphics.PointF // 导入 PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// CometRenderer 类，实现 GLSurfaceView.Renderer 接口，负责渲染彗星
class CometRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private lateinit var comet: Comet // 彗星对象
    private val projectionMatrix = FloatArray(16) // 投影矩阵
    private val viewMatrix = FloatArray(16) // 视图矩阵
    private val viewProjectionMatrix = FloatArray(16) // 视图-投影 矩阵

    // --- 动画计时 ---
    private var lastFrameTime: Long = 0 // 上一帧的时间戳 (毫秒)

    // 当 Surface 创建时调用
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // 设置清屏颜色为黑色

        // --- 创建示例路径 --- (你可以替换成你自己的坐标点)
        val samplePath = listOf(
            PointF(-0.8f, 0.0f),
            PointF(-0.4f, 0.5f),
            PointF(0.0f, 0.8f),
            PointF(0.4f, 0.5f),
            PointF(0.8f, 0.0f)
        )

        // 在这里初始化 Comet 对象，传入路径
        comet = Comet(samplePath)

        // 初始化上一帧时间戳
        lastFrameTime = System.currentTimeMillis()
    }

    // 当 Surface 尺寸改变时调用
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height) // 设置视口大小

        // 计算宽高比
        val aspectRatio = if (width > height) {
            width.toFloat() / height.toFloat()
        } else {
            height.toFloat() / width.toFloat()
        }

        // 设置投影矩阵
        if (width > height) {
            // 横屏
            android.opengl.Matrix.orthoM(projectionMatrix, 0, -aspectRatio, aspectRatio, -1f, 1f, -1f, 1f)
        } else {
            // 竖屏或方形
            android.opengl.Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f)
        }

        // 设置视图矩阵（相机位置）
        // eyeX, eyeY, eyeZ: 相机位置
        // centerX, centerY, centerZ: 目标观察点
        // upX, upY, upZ: 相机朝上方向
        android.opengl.Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // 计算视图-投影矩阵
        android.opengl.Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
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

        // 绘制彗星
        comet.draw(viewProjectionMatrix)

        // 绘制后检查 OpenGL 错误
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            android.util.Log.e("CometRenderer", "OpenGL Error after draw: " + error)
            error = GLES20.glGetError() // 检查后续错误
        }

        // 请求重绘以实现动画
        // (在 MainActivity 中渲染模式设置为 RENDERMODE_CONTINUOUSLY)
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