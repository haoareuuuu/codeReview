package com.hsl.product

import android.graphics.PointF // 导入 PointF
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.pow
import kotlin.math.sqrt // 确保导入 sqrt

// 移除未使用的导入
// import java.util.LinkedList
// import kotlin.random.Random
// import kotlin.math.cos
// import kotlin.math.sin

// 彗星类，负责定义彗星的形状、着色器和绘制逻辑
class Comet(private val pathPoints: List<PointF>) { // 添加构造函数参数 pathPoints

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
    private var vertexData: FloatArray // 改为 var 以便在 init 中赋值
    private var vertexBuffer: FloatBuffer // 改为 var
    private var vertexCount: Int // 改为 var
    // 每个顶点包含位置 (X, Y, Z) 和 Alpha (A)，共 4 个 float
    private val vertexStride: Int = (COORDS_PER_VERTEX_POS + COORDS_PER_VERTEX_ALPHA) * 4 // 每个顶点的步长（字节数）

    // --- 颜色 --- (弧形的颜色，红色)
    private val arcColor = floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f) // R, G, B, A

    // --- 动画 --- (控制绘制进度)
    private var animationProgress = 0.0f // 动画进度 (0.0 到 1.0)
    private val animationSpeed = 0.2f // 动画速度 (每秒进度增加量)

    // --- 变换矩阵 --- (用于定位和变换彗星)
    private val mvpMatrix = FloatArray(16) // 模型-视图-投影 矩阵，最终变换矩阵

    // --- 插值参数 ---
    private val numInterpolationPointsPerSegment = 30 // 每个原始线段插值点的数量 (增加点数以提高平滑度)

    init {
        // --- 对原始路径进行插值以获得平滑路径 ---
        val smoothPathPoints = if (pathPoints.size >= 2) {
            interpolatePath(pathPoints, numInterpolationPointsPerSegment)
        } else {
            listOf() // 如果原始点不足，则路径为空
        }

        // --- 根据插值后的 smoothPathPoints 生成顶点数据 ---
        if (smoothPathPoints.size < 2) {
            // 如果点数少于2，无法形成路径，设置空数据或抛出异常
            vertexData = FloatArray(0)
            vertexCount = 0
            // 初始化空的 FloatBuffer
            val bb = ByteBuffer.allocateDirect(0)
            bb.order(ByteOrder.nativeOrder())
            vertexBuffer = bb.asFloatBuffer()
            android.util.Log.w("Comet", "Interpolated path needs at least 2 points.") // 更新日志信息
        } else {
            val numSegments = smoothPathPoints.size - 1 // 使用插值后的点计算分段数量
            val minWidth = 0.01f // 头部（起点）的宽度
            val maxWidth = 0.08f // 尾部（终点）的宽度

            val vertexDataList = mutableListOf<Float>() // 用于存储顶点数据 (位置+Alpha) 的可变列表

            // 计算插值后路径的总长度，用于计算 t 值
            var totalLength = 0f
            for (i in 0 until numSegments) {
                val p1 = smoothPathPoints[i]
                val p2 = smoothPathPoints[i + 1]
                totalLength += kotlin.math.sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
            }

            var accumulatedLength = 0f

            // 处理第一个点 (i=0)
            val p0 = smoothPathPoints[0]
            val p1 = smoothPathPoints[1]
            var dx = p1.x - p0.x
            var dy = p1.y - p0.y
            var segmentLength = kotlin.math.sqrt(dx * dx + dy * dy)
            var tangentX = if (segmentLength > 0) dx / segmentLength else 1.0f
            var tangentY = if (segmentLength > 0) dy / segmentLength else 0.0f
            var normalX = -tangentY
            var normalY = tangentX
            var t = 0f // 第一个点的 t 值为 0
            var currentHalfWidth = (minWidth + (maxWidth - minWidth) * t) / 2.0f
            var currentAlpha = 1.0f - t

            // 添加第一个点的两个顶点
            vertexDataList.add(p0.x + normalX * currentHalfWidth)
            vertexDataList.add(p0.y + normalY * currentHalfWidth)
            vertexDataList.add(0.0f) // Z
            vertexDataList.add(currentAlpha) // Alpha

            vertexDataList.add(p0.x - normalX * currentHalfWidth)
            vertexDataList.add(p0.y - normalY * currentHalfWidth)
            vertexDataList.add(0.0f) // Z
            vertexDataList.add(currentAlpha) // Alpha

            // 循环生成中间点的顶点 (i = 1 to numSegments - 1)
            for (i in 1 until smoothPathPoints.size - 1) { // 使用插值后的点数
                val prevP = smoothPathPoints[i - 1]
                val currentP = smoothPathPoints[i]
                val nextP = smoothPathPoints[i + 1]

                // 计算前一段和后一段的切线
                val dx1 = currentP.x - prevP.x
                val dy1 = currentP.y - prevP.y
                val len1 = kotlin.math.sqrt(dx1 * dx1 + dy1 * dy1)
                val tx1 = if (len1 > 0) dx1 / len1 else 0f
                val ty1 = if (len1 > 0) dy1 / len1 else 0f

                val dx2 = nextP.x - currentP.x
                val dy2 = nextP.y - currentP.y
                val len2 = kotlin.math.sqrt(dx2 * dx2 + dy2 * dy2)
                val tx2 = if (len2 > 0) dx2 / len2 else 0f
                val ty2 = if (len2 > 0) dy2 / len2 else 0f

                // 计算平均切线 (角平分线方向近似)
                tangentX = (tx1 + tx2) / 2f
                tangentY = (ty1 + ty2) / 2f
                val tangentLength = kotlin.math.sqrt(tangentX * tangentX + tangentY * tangentY)
                if (tangentLength > 0) {
                    tangentX /= tangentLength
                    tangentY /= tangentLength
                } else {
                    // 如果平均切线为0 (例如180度转弯)，使用前一段的切线作为法线方向的基础
                    tangentX = tx1
                    tangentY = ty1
                }

                // 计算法线
                normalX = -tangentY
                normalY = tangentX

                // 更新累计长度
                accumulatedLength += len1
                t = if (totalLength > 0) accumulatedLength / totalLength else 0f // 当前点的 t 值
                currentHalfWidth = (minWidth + (maxWidth - minWidth) * t) / 2.0f
                currentAlpha = 1.0f - t

                // 添加当前点的两个顶点
                vertexDataList.add(currentP.x + normalX * currentHalfWidth)
                vertexDataList.add(currentP.y + normalY * currentHalfWidth)
                vertexDataList.add(0.0f) // Z
                vertexDataList.add(currentAlpha) // Alpha

                vertexDataList.add(currentP.x - normalX * currentHalfWidth)
                vertexDataList.add(currentP.y - normalY * currentHalfWidth)
                vertexDataList.add(0.0f) // Z
                vertexDataList.add(currentAlpha) // Alpha
            }

            // 处理最后一个点 (i = numSegments)
            val lastP = smoothPathPoints[smoothPathPoints.size - 1]
            val secondLastP = smoothPathPoints[smoothPathPoints.size - 2]
            dx = lastP.x - secondLastP.x
            dy = lastP.y - secondLastP.y
            segmentLength = kotlin.math.sqrt(dx * dx + dy * dy)
            tangentX = if (segmentLength > 0) dx / segmentLength else 1.0f
            tangentY = if (segmentLength > 0) dy / segmentLength else 0.0f
            normalX = -tangentY
            normalY = tangentX
            t = 1f // 最后一个点的 t 值为 1
            currentHalfWidth = (minWidth + (maxWidth - minWidth) * t) / 2.0f
            currentAlpha = 1.0f - t // Alpha 为 0

            // 添加最后一个点的两个顶点
            vertexDataList.add(lastP.x + normalX * currentHalfWidth)
            vertexDataList.add(lastP.y + normalY * currentHalfWidth)
            vertexDataList.add(0.0f) // Z
            vertexDataList.add(currentAlpha) // Alpha

            vertexDataList.add(lastP.x - normalX * currentHalfWidth)
            vertexDataList.add(lastP.y - normalY * currentHalfWidth)
            vertexDataList.add(0.0f) // Z
            vertexDataList.add(currentAlpha) // Alpha

            vertexData = vertexDataList.toFloatArray() // 将可变列表转换为 FloatArray
            // 每个顶点有 4 个 float (X, Y, Z, A)
            vertexCount = vertexData.size / (COORDS_PER_VERTEX_POS + COORDS_PER_VERTEX_ALPHA) // 计算顶点总数

            // 初始化顶点字节缓冲区，用于存储顶点数据
            val bb = ByteBuffer.allocateDirect(vertexData.size * 4) // 分配直接字节缓冲区 (每个 float 4字节)
            bb.order(ByteOrder.nativeOrder()) // 设置字节顺序为本地顺序
            vertexBuffer = bb.asFloatBuffer() // 将字节缓冲区转换为 FloatBuffer
            vertexBuffer.put(vertexData) // 将顶点数据放入缓冲区
            vertexBuffer.position(0) // 将缓冲区的位置重置为0，以便从头读取
        }

        // --- 准备着色器和 OpenGL 程序 --- (这部分逻辑不变)
        val vertexShader: Int = CometRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode) // 加载顶点着色器
        val fragmentShader: Int = CometRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode) // 加载片段着色器

        // 创建 OpenGL 程序并链接着色器
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            checkGlError("glLinkProgram")

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val errorLog = GLES20.glGetProgramInfoLog(it)
                android.util.Log.e("Comet", "Program linking failed: $errorLog")
                GLES20.glDeleteProgram(it)
                throw RuntimeException("OpenGL Program Linking Failed: $errorLog")
            }
        }
        checkGlError("glCreateProgram")

        // 获取着色器成员的句柄 (这部分逻辑不变)
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        checkGlError("glGetAttribLocation vPosition")
        if (positionHandle == -1) { throw RuntimeException("Could not get attrib location for vPosition") }

        alphaHandle = GLES20.glGetAttribLocation(program, "aAlpha")
        checkGlError("glGetAttribLocation aAlpha")
        if (alphaHandle == -1) { throw RuntimeException("Could not get attrib location for aAlpha") }

        colorUniformHandle = GLES20.glGetUniformLocation(program, "uColor")
        checkGlError("glGetUniformLocation uColor")
        if (colorUniformHandle == -1) { throw RuntimeException("Could not get uniform location for uColor") }

        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        checkGlError("glGetUniformLocation uMVPMatrix")
        if (mvpMatrixHandle == -1) { throw RuntimeException("Could not get uniform location for uMVPMatrix") }

    }

    // --- Catmull-Rom 插值函数 ---
    private fun interpolatePath(points: List<PointF>, numPointsPerSegment: Int): List<PointF> {
        if (points.size < 2) return points // 至少需要两个点
        if (numPointsPerSegment <= 0) return points // 插值点数需大于0

        val interpolatedPoints = mutableListOf<PointF>()
        val numSegments = points.size - 1

        for (i in 0..numSegments) {
            // 获取 Catmull-Rom 需要的四个控制点 P0, P1, P2, P3
            // 对于边界情况，复制端点
            val p0 = points[maxOf(0, i - 1)]
            val p1 = points[i]
            val p2 = points[minOf(points.size - 1, i + 1)]
            val p3 = points[minOf(points.size - 1, i + 2)]

            // 只在 P1 和 P2 之间插值 (即当前段)
            // 对于第一个点 (i=0)，我们只添加 P1 (points[0])
            if (i == 0) {
                interpolatedPoints.add(p1)
            }

            // 对于 P1 和 P2 之间的段 (i < numSegments)
            if (i < numSegments) {
                for (j in 1..numPointsPerSegment) {
                    val t = j.toFloat() / (numPointsPerSegment + 1) // t 从 0 到 1 (不包括 0，因为 P1 已添加)
                    val tt = t * t
                    val ttt = tt * t

                    // Catmull-Rom 公式 (alpha = 0.5, 向心 Catmull-Rom)
                    val q0 = -0.5f * ttt + tt - 0.5f * t
                    val q1 = 1.5f * ttt - 2.5f * tt + 1.0f
                    val q2 = -1.5f * ttt + 2.0f * tt + 0.5f * t
                    val q3 = 0.5f * ttt - 0.5f * tt

                    val tx = p0.x * q0 + p1.x * q1 + p2.x * q2 + p3.x * q3
                    val ty = p0.y * q0 + p1.y * q1 + p2.y * q2 + p3.y * q3

                    interpolatedPoints.add(PointF(tx, ty))
                }
                // 添加 P2 (points[i+1])，确保段的终点被包含
                // 避免在最后一段重复添加最后一个点
                if (i < numSegments -1) {
                     interpolatedPoints.add(p2)
                } else if (i == numSegments -1) {
                    // 这是最后一段，确保最后一个原始点被精确添加
                    interpolatedPoints.add(points.last())
                }
            }
        }
        // 移除可能因浮点精度产生的重复点 (可选，但建议)
        return interpolatedPoints.distinctBy { Pair(it.x, it.y) }
    }

    // 更新动画进度 (这部分逻辑不变)
    fun update(deltaTime: Float) {
        animationProgress += animationSpeed * deltaTime
        if (animationProgress > 1.0f) {
            animationProgress = 0.0f // 动画循环
        }
    }

    // 绘制彗星，接受外部传入的进度参数
    fun draw(projectionMatrix: FloatArray, progress: Float = -1f) { // 传入投影矩阵和进度参数
        if (vertexCount == 0) return // 如果没有顶点，则不绘制

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

        // 设置 MVP 矩阵 (模型-视图-投影)
        // 对于简单的2D场景，我们只使用投影矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, projectionMatrix, 0)
        checkGlError("glUniformMatrix4fv - mvpMatrix")

        // --- 绘制动画部分 ---
        // 使用外部传入的进度参数或内部动画进度
        val progressToUse = if (progress >= 0f) progress else animationProgress

        // 计算需要绘制的顶点数量，从尾部开始
        // vertexCount 是总顶点数
        // progressToUse 从 0 到 1
        // 我们想绘制最后 (progressToUse * vertexCount) 个顶点
        val verticesToDraw = (progressToUse * vertexCount).toInt()
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

    // 检查 OpenGL 错误 (这部分逻辑不变)
    private fun checkGlError(op: String) { // op: 操作名称，用于日志记录
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) { // 循环检查错误直到没有错误
            android.util.Log.e("Comet", "$op: glError $error") // 打印错误日志
            // 根据需要考虑在此处抛出异常
        }
    }

    companion object {
        // 定义每个顶点属性的分量数量 (这部分逻辑不变)
        const val COORDS_PER_VERTEX_POS = 3 // 位置坐标数 (X, Y, Z)
        const val COORDS_PER_VERTEX_ALPHA = 1 // Alpha 分量数 (A)
    }
}

// 添加 Float.pow 扩展函数，如果项目中没有的话