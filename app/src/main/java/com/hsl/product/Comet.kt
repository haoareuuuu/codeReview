package com.hsl.product

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import kotlin.random.Random

import kotlin.math.cos
import kotlin.math.sin

// 彗星类，负责定义彗星的形状、着色器和绘制逻辑
class Comet {

    // --- 着色器 --- (顶点着色器传递位置，片段着色器设置颜色)
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix; // 模型-视图-投影 矩阵
        attribute vec4 vPosition; // 顶点位置属性 (x, y, z, w)
        attribute float aAlpha;   // 顶点透明度属性
        varying float vAlpha;     // 传递给片段着色器的透明度
        void main() {
            // 计算最终的顶点位置
            gl_Position = uMVPMatrix * vPosition;
            // 将顶点透明度传递给片段着色器
            vAlpha = aAlpha;
        }
    """

    private val fragmentShaderCode = """
        precision mediump float; // 设置浮点数精度
        uniform vec4 uColor;     // 基础颜色 (从 Kotlin 传入)
        varying lowp float vAlpha; // 从顶点着色器传入的透明度
        void main() {
             // 设置最终的片段颜色，混合基础颜色和顶点透明度
             gl_FragColor = vec4(uColor.rgb, uColor.a * vAlpha);
        }
    """

    // --- OpenGL 程序 --- (链接顶点和片段着色器)
    private var program: Int
    private var positionHandle: Int = 0 // 顶点位置属性句柄
    private var alphaHandle: Int = 0    // 顶点透明度属性句柄
    private var colorUniformHandle: Int = 0 // 统一颜色变量句柄
    private var mvpMatrixHandle: Int = 0 // MVP 矩阵句柄

    // --- 顶点数据 --- (弧形的顶点)
    private val vertexData: FloatArray // 存储顶点数据 (位置 + Alpha) 的数组
    private val vertexBuffer: FloatBuffer // 存储顶点数据的缓冲区
    private val vertexCount: Int // 顶点数量
    // 每个顶点包含位置 (X, Y, Z) 和 Alpha (A)，共 4 个 float
    private val vertexStride: Int = (COORDS_PER_VERTEX_POS + COORDS_PER_VERTEX_ALPHA) * 4 // 每个顶点的步长（字节数）

    // --- 颜色 --- (弧形的颜色，红色)
    private val arcColor = floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f) // R, G, B, A

    // --- 动画 --- (控制绘制进度)
    private var animationProgress = 0.0f // 动画进度 (0.0 到 1.0)
    private val animationSpeed = 0.2f // 动画速度 (每秒进度增加量)

    // --- 变换矩阵 --- (用于定位和变换彗星)
    private val modelMatrix = FloatArray(16) // 模型矩阵，定义对象在世界空间中的位置和方向
    private val mvpMatrix = FloatArray(16) // 模型-视图-投影 矩阵，最终变换矩阵

    init {
        // 定义具有可变宽度的三角形带的弧形顶点
        val numSegments = 100 // 弧形的分段数量，越多越平滑
        val arcWidth = 1.6f // 弧形的宽度 (大致对应 x 轴 -0.8 到 0.8)
        val arcHeight = 0.8f // 弧形的峰值高度
        val startX = -arcWidth / 2f // 弧形的起始 X 坐标
        val stepX = arcWidth / numSegments // 每个分段的 X 步长
        val minWidth = 0.01f // 头部（起点）的宽度
        val maxWidth = 0.08f // 尾部（终点）的宽度

        val vertexDataList = mutableListOf<Float>() // 用于存储顶点数据 (位置+Alpha) 的可变列表
        // 计算初始点的前一个点坐标（用于计算第一个分段的切线）
        var prevX = startX
        var prevY = arcHeight * (1 - (2 * prevX / arcWidth) * (2 * prevX / arcWidth)) // 抛物线方程

        // 循环生成每个分段的顶点
        for (i in 0..numSegments) {
            val t = i.toFloat() / numSegments // 参数 t，从 0 到 1，表示在弧线上的位置
            val currentX = startX + i * stepX // 当前点的 X 坐标
            val currentY = arcHeight * (1 - (2 * currentX / arcWidth) * (2 * currentX / arcWidth)) // 当前点的 Y 坐标 (抛物线)

            // 计算切线（使用有限差分近似）
            val dx = currentX - prevX // X 方向的变化量
            val dy = currentY - prevY // Y 方向的变化量
            val length = kotlin.math.sqrt(dx * dx + dy * dy) // 切线向量的长度
            val tangentX = if (length > 0) dx / length else 1.0f // 单位切线向量 X 分量 (处理长度为0的情况)
            val tangentY = if (length > 0) dy / length else 0.0f // 单位切线向量 Y 分量

            // 计算法线（垂直于切线）
            val normalX = -tangentY // 法线向量 X 分量
            val normalY = tangentX // 法线向量 Y 分量

            // 根据参数 t 计算当前宽度（线性插值）
            val currentHalfWidth = (minWidth + (maxWidth - minWidth) * t) / 2.0f // 当前点处弧形宽度的一半

            // 计算当前点处的三角形带的两个顶点
            val x1 = currentX + normalX * currentHalfWidth // 顶点1 X 坐标 (沿法线方向)
            val y1 = currentY + normalY * currentHalfWidth // 顶点1 Y 坐标
            val x2 = currentX - normalX * currentHalfWidth // 顶点2 X 坐标 (沿法线反方向)
            val y2 = currentY - normalY * currentHalfWidth // 顶点2 Y 坐标

            // 计算当前点的 Alpha 值 (从头到尾 1.0 -> 0.0)
            val currentAlpha = 1.0f - t

            // 添加顶点1 (位置 + Alpha) 到列表
            vertexDataList.add(x1)
            vertexDataList.add(y1)
            vertexDataList.add(0.0f) // Z 坐标
            vertexDataList.add(currentAlpha) // Alpha

            // 添加顶点2 (位置 + Alpha) 到列表
            vertexDataList.add(x2)
            vertexDataList.add(y2)
            vertexDataList.add(0.0f) // Z 坐标
            vertexDataList.add(currentAlpha) // Alpha

            // 更新上一个点，用于下一次迭代计算切线
            // 仅在第一个真实分段点之后更新 prev 值
            if (i > 0 || numSegments == 0) { // 确保在第一个点之后更新
                 prevX = currentX
                 prevY = currentY
            }
        }

        vertexData = vertexDataList.toFloatArray() // 将可变列表转换为 FloatArray
        // 每个顶点有 4 个 float (X, Y, Z, A)
        vertexCount = vertexData.size / (COORDS_PER_VERTEX_POS + COORDS_PER_VERTEX_ALPHA) // 计算顶点总数

        // 初始化顶点字节缓冲区，用于存储顶点数据
        val bb = ByteBuffer.allocateDirect(vertexData.size * 4) // 分配直接字节缓冲区 (每个 float 4字节)
        bb.order(ByteOrder.nativeOrder()) // 设置字节顺序为本地顺序
        vertexBuffer = bb.asFloatBuffer() // 将字节缓冲区转换为 FloatBuffer
        vertexBuffer.put(vertexData) // 将顶点数据放入缓冲区
        vertexBuffer.position(0) // 将缓冲区的位置重置为0，以便从头读取

        // 准备着色器和 OpenGL 程序
        val vertexShader: Int = CometRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode) // 加载顶点着色器
        val fragmentShader: Int = CometRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode) // 加载片段着色器

        // 创建 OpenGL 程序并链接着色器
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader) // 附加顶点着色器
            GLES20.glAttachShader(it, fragmentShader) // 附加片段着色器
            GLES20.glLinkProgram(it) // 链接程序
            checkGlError("glLinkProgram") // 检查链接错误

            // 检查链接状态
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) { // 如果链接失败
                val errorLog = GLES20.glGetProgramInfoLog(it) // 获取错误日志
                android.util.Log.e("Comet", "Program linking failed: $errorLog") // 打印错误日志
                GLES20.glDeleteProgram(it) // 删除程序
                // 适当地处理错误，例如抛出异常
                throw RuntimeException("OpenGL Program Linking Failed: $errorLog")
            }
        }
        checkGlError("glCreateProgram") // 检查创建程序错误

        // 获取着色器成员的句柄
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition") // 获取顶点位置属性句柄
        checkGlError("glGetAttribLocation vPosition")
        if (positionHandle == -1) { throw RuntimeException("Could not get attrib location for vPosition") }

        alphaHandle = GLES20.glGetAttribLocation(program, "aAlpha") // 获取顶点 Alpha 属性句柄
        checkGlError("glGetAttribLocation aAlpha")
        if (alphaHandle == -1) { throw RuntimeException("Could not get attrib location for aAlpha") }
        colorUniformHandle = GLES20.glGetUniformLocation(program, "uColor") // 获取统一颜色变量句柄
        checkGlError("glGetUniformLocation uColor")
        if (colorUniformHandle == -1) { // 检查句柄是否有效
            throw RuntimeException("Could not get uniform location for uColor")
        }
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix") // 获取 MVP 矩阵句柄
        checkGlError("glGetUniformLocation uMVPMatrix")
        if (mvpMatrixHandle == -1) { // 检查句柄是否有效
            throw RuntimeException("Could not get uniform location for uMVPMatrix")
        }

        // 初始化模型矩阵为单位矩阵
        Matrix.setIdentityM(modelMatrix, 0)
    }

    // 更新动画进度
    fun update(deltaTime: Float) {
        animationProgress += animationSpeed * deltaTime
        if (animationProgress > 1.0f) {
            animationProgress = 0.0f // 动画循环
        }
    }

    // 对于静态弧形，不需要更新逻辑
    // fun update() { ... } // 已移除

    // 绘制彗星
    fun draw(viewProjectionMatrix: FloatArray) { // 传入视图-投影矩阵
        GLES20.glUseProgram(program) // 使用此 OpenGL 程序进行绘制
        checkGlError("glUseProgram") // 检查错误

        // 在继续之前检查句柄是否有效
        if (positionHandle == -1 || alphaHandle == -1 || colorUniformHandle == -1 || mvpMatrixHandle == -1) {
            android.util.Log.e("Comet", "Invalid shader handles!") // 打印错误日志
            return // 如果句柄无效则不绘制
        }

        // 启用混合以支持透明度
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        checkGlError("glEnable/BlendFunc")

        // --- 设置顶点位置属性 --- 
        vertexBuffer.position(0) // 将缓冲区指针定位到位置数据的开始
        GLES20.glVertexAttribPointer(
            positionHandle,          // 属性句柄
            COORDS_PER_VERTEX_POS,   // 每个位置的坐标数 (X, Y, Z)
            GLES20.GL_FLOAT,         // 数据类型
            false,                   // 是否归一化
            vertexStride,            // 步长 (整个顶点的大小)
            vertexBuffer             // 顶点缓冲区
        )
        checkGlError("glVertexAttribPointer - position")
        GLES20.glEnableVertexAttribArray(positionHandle) // 启用位置属性
        checkGlError("glEnableVertexAttribArray positionHandle")

        // --- 设置顶点 Alpha 属性 ---
        vertexBuffer.position(COORDS_PER_VERTEX_POS) // 将缓冲区指针定位到 Alpha 数据的开始 (跳过 X, Y, Z)
        GLES20.glVertexAttribPointer(
            alphaHandle,             // 属性句柄
            COORDS_PER_VERTEX_ALPHA, // 每个 Alpha 的分量数 (1)
            GLES20.GL_FLOAT,         // 数据类型
            false,                   // 是否归一化
            vertexStride,            // 步长 (整个顶点的大小)
            vertexBuffer             // 顶点缓冲区
        )
        checkGlError("glVertexAttribPointer - alpha")
        GLES20.glEnableVertexAttribArray(alphaHandle) // 启用 Alpha 属性
        checkGlError("glEnableVertexAttribArray alphaHandle")

        // 设置弧形的统一颜色（红色）
        GLES20.glUniform4fv(colorUniformHandle, 1, arcColor, 0)
        checkGlError("glUniform4fv - color")

        // 设置模型矩阵（目前为单位矩阵，弧形定义在世界空间中）
        // Matrix.setIdentityM(modelMatrix, 0) // 模型矩阵默认为单位矩阵，如果不需要可以移除

        // 计算最终的变换矩阵 (模型 * 视图 * 投影)
        Matrix.multiplyMM(mvpMatrix, 0, viewProjectionMatrix, 0, modelMatrix, 0)

        // 将变换矩阵传递给着色器
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        checkGlError("glUniformMatrix4fv - mvpMatrix")

        // --- 绘制动画部分 ---
        // 计算需要绘制的顶点数量，从尾部开始
        // vertexCount 是总顶点数
        // animationProgress 从 0 到 1
        // 我们想绘制最后 (animationProgress * vertexCount) 个顶点
        val verticesToDraw = (animationProgress * vertexCount).toInt()
        // 确保顶点数是偶数，因为我们使用 TRIANGLE_STRIP，每段2个顶点
        val count = (verticesToDraw / 2) * 2
        // 计算起始绘制的顶点索引 (从尾部开始)
        val first = vertexCount - count

        // 只绘制计算出的部分
        if (count > 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, first, count)
            checkGlError("glDrawArrays - comet strip animated")
        }
        // --- 动画绘制结束 ---

        // 禁用顶点属性数组
        GLES20.glDisableVertexAttribArray(positionHandle)
        checkGlError("glDisableVertexAttribArray positionHandle")
        GLES20.glDisableVertexAttribArray(alphaHandle)
        checkGlError("glDisableVertexAttribArray alphaHandle")

        // 禁用混合（如果后续绘制不需要）
        GLES20.glDisable(GLES20.GL_BLEND)
        checkGlError("glDisableBlend")
    }

    // 检查 OpenGL 错误
    private fun checkGlError(op: String) { // op: 操作名称，用于日志记录
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) { // 循环检查错误直到没有错误
            android.util.Log.e("Comet", "$op: glError $error") // 打印错误日志
            // 根据需要考虑在此处抛出异常
        }
    }

    companion object {
        // 定义每个顶点属性的分量数量
        const val COORDS_PER_VERTEX_POS = 3 // 位置坐标数 (X, Y, Z)
        const val COORDS_PER_VERTEX_ALPHA = 1 // Alpha 分量数 (A)
    }
}