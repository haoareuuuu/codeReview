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

    // --- 坐标系控制 ---
    private var showCoordinateSystem = true // 控制坐标系显示的变量

    // --- 坐标系数据 ---
    // X轴和Y轴的顶点数据，范围从-1到1
    private val coordinateVertices = floatArrayOf(
        // X轴 (红色)
        -1.0f, 0.0f, 0.0f,  // 起点
        1.0f, 0.0f, 0.0f,   // 终点

        // Y轴 (绿色)
        0.0f, -1.0f, 0.0f,  // 起点
        0.0f, 1.0f, 0.0f    // 终点
    )

    // 坐标轴颜色数据
    private val coordinateColors = floatArrayOf(
        // X轴 (红色)
        1.0f, 0.0f, 0.0f, 1.0f,  // 起点颜色
        1.0f, 0.0f, 0.0f, 1.0f,  // 终点颜色

        // Y轴 (绿色)
        0.0f, 1.0f, 0.0f, 1.0f,  // 起点颜色
        0.0f, 1.0f, 0.0f, 1.0f   // 终点颜色
    )

    // --- 坐标系刻度数据 ---
    // 刻度间隔，每0.2个单位显示一个刻度
    private val tickInterval = 0.2f
    // 标准刻度线长度
    private val tickLength = 0.02f
    // 主要刻度线长度（如0.5、1.0等）
    private val majorTickLength = 0.04f
    // 刻度线顶点数据
    private val tickVertices: FloatArray
    // 刻度线颜色数据
    private val tickColors: FloatArray
    // 刻度线数量
    private val tickCount: Int

    // 坐标轴和刻度线的顶点缓冲区
    private val coordinateVertexBuffer: FloatBuffer
    private val coordinateColorBuffer: FloatBuffer
    private val tickVertexBuffer: FloatBuffer
    private val tickColorBuffer: FloatBuffer

    // --- 着色器 --- (顶点着色器传递位置，片段着色器设置颜色)
    private val vertexShaderCode = """
        attribute vec4 vPosition; // 顶点位置属性 (x, y, z, w)
        attribute float aAlpha;   // 顶点透明度属性
        varying float vAlpha;     // 传递给片段着色器的透明度
        void main() {
            // 直接使用顶点位置，不需要矩阵变换
            gl_Position = vPosition;
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
    // 不再需要矩阵句柄

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
    // 不再需要变换矩阵

    // --- 插值参数 ---
    private val numInterpolationPointsPerSegment = 30 // 每个原始线段插值点的数量 (增加点数以提高平滑度)

    init {
        // --- 初始化刻度数据 ---
        // 计算X轴和Y轴上的刻度数量
        val xTickCount = (2.0f / tickInterval).toInt() + 1 // -1到1的范围内的刻度数量
        val yTickCount = xTickCount // X和Y轴刻度数量相同
        tickCount = xTickCount + yTickCount

        // 创建刻度线顶点数据和颜色数据数组
        tickVertices = FloatArray(tickCount * 2 * 3) // 每个刻度线有2个点，每个点有3个坐标
        tickColors = FloatArray(tickCount * 2 * 4) // 每个刻度线有2个点，每个点有4个颜色分量

        var vertexIndex = 0
        var colorIndex = 0

        // 生成X轴上的刻度线
        for (i in 0 until xTickCount) {
            val x = -1.0f + i * tickInterval

            // 跳过原点，因为原点是坐标轴的交叉点
            if (Math.abs(x) < 0.001f) continue

            // 判断是否是主要刻度线（0.5、1.0等）
            val isMajorTick = Math.abs(x * 10 % 5) < 0.001f
            val currentTickLength = if (isMajorTick) majorTickLength else tickLength

            // 刻度线的两个点
            tickVertices[vertexIndex++] = x
            tickVertices[vertexIndex++] = 0.0f
            tickVertices[vertexIndex++] = 0.0f

            tickVertices[vertexIndex++] = x
            tickVertices[vertexIndex++] = currentTickLength
            tickVertices[vertexIndex++] = 0.0f

            // 刻度线颜色（红色，与X轴相同）
            for (j in 0 until 2) { // 每个刻度线有2个点
                tickColors[colorIndex++] = 1.0f // R
                tickColors[colorIndex++] = 0.0f // G
                tickColors[colorIndex++] = 0.0f // B
                tickColors[colorIndex++] = 0.7f // A（稍微透明）
            }
        }

        // 生成Y轴上的刻度线
        for (i in 0 until yTickCount) {
            val y = -1.0f + i * tickInterval

            // 跳过原点
            if (Math.abs(y) < 0.001f) continue

            // 判断是否是主要刻度线（0.5、1.0等）
            val isMajorTick = Math.abs(y * 10 % 5) < 0.001f
            val currentTickLength = if (isMajorTick) majorTickLength else tickLength

            // 刻度线的两个点
            tickVertices[vertexIndex++] = 0.0f
            tickVertices[vertexIndex++] = y
            tickVertices[vertexIndex++] = 0.0f

            tickVertices[vertexIndex++] = currentTickLength
            tickVertices[vertexIndex++] = y
            tickVertices[vertexIndex++] = 0.0f

            // 刻度线颜色（绿色，与Y轴相同）
            for (j in 0 until 2) { // 每个刻度线有2个点
                tickColors[colorIndex++] = 0.0f // R
                tickColors[colorIndex++] = 1.0f // G
                tickColors[colorIndex++] = 0.0f // B
                tickColors[colorIndex++] = 0.7f // A（稍微透明）
            }
        }

        // --- 初始化坐标系的顶点缓冲区 ---
        // 初始化坐标轴顶点缓冲区
        val coordVB = ByteBuffer.allocateDirect(coordinateVertices.size * 4)
        coordVB.order(ByteOrder.nativeOrder())
        coordinateVertexBuffer = coordVB.asFloatBuffer()
        coordinateVertexBuffer.put(coordinateVertices)
        coordinateVertexBuffer.position(0)

        // 初始化坐标轴颜色缓冲区
        val coordCB = ByteBuffer.allocateDirect(coordinateColors.size * 4)
        coordCB.order(ByteOrder.nativeOrder())
        coordinateColorBuffer = coordCB.asFloatBuffer()
        coordinateColorBuffer.put(coordinateColors)
        coordinateColorBuffer.position(0)

        // 初始化刻度线顶点缓冲区
        val tickVB = ByteBuffer.allocateDirect(tickVertices.size * 4)
        tickVB.order(ByteOrder.nativeOrder())
        tickVertexBuffer = tickVB.asFloatBuffer()
        tickVertexBuffer.put(tickVertices)
        tickVertexBuffer.position(0)

        // 初始化刻度线颜色缓冲区
        val tickCB = ByteBuffer.allocateDirect(tickColors.size * 4)
        tickCB.order(ByteOrder.nativeOrder())
        tickColorBuffer = tickCB.asFloatBuffer()
        tickColorBuffer.put(tickColors)
        tickColorBuffer.position(0)

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
            var currentHalfWidth = (maxWidth - (maxWidth - minWidth) * t) / 2.0f
            var currentAlpha = t

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
                currentHalfWidth = (maxWidth - (maxWidth - minWidth) * t) / 2.0f
                currentAlpha = t

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
            currentHalfWidth = (maxWidth - (maxWidth - minWidth) * t) / 2.0f
            currentAlpha = t // Alpha 为 1

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

        // 不再需要获取矩阵句柄

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

    // 绘制坐标系
    private fun drawCoordinateSystem() {
        if (!showCoordinateSystem) return // 如果不显示坐标系，则直接返回

        // 使用着色器程序
        GLES20.glUseProgram(program)
        checkGlError("glUseProgram - coordinate system")

        // --- 绘制坐标轴 ---
        // 设置线宽
        GLES20.glLineWidth(2.0f)

        // 设置顶点位置属性
        coordinateVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX_POS,
            GLES20.GL_FLOAT,
            false,
            COORDS_PER_VERTEX_POS * 4, // 每个顶点只有位置数据，没有Alpha
            coordinateVertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 设置Alpha属性为1.0
        val fixedAlpha = floatArrayOf(1.0f)
        GLES20.glVertexAttrib1fv(alphaHandle, fixedAlpha, 0)

        // 绘制X轴
        GLES20.glUniform4fv(colorUniformHandle, 1, floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f), 0) // 红色
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2) // 绘制第一段线（X轴）

        // 绘制Y轴
        GLES20.glUniform4fv(colorUniformHandle, 1, floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f), 0) // 绿色
        GLES20.glDrawArrays(GLES20.GL_LINES, 2, 2) // 绘制第二段线（Y轴）

        // 禁用顶点属性数组
        GLES20.glDisableVertexAttribArray(positionHandle)

        // --- 绘制刻度线 ---
        // 设置线宽
        GLES20.glLineWidth(1.0f) // 标准刻度线宽度

        // 设置顶点位置属性
        tickVertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX_POS,
            GLES20.GL_FLOAT,
            false,
            COORDS_PER_VERTEX_POS * 4,
            tickVertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        // 设置Alpha属性为0.7（稍微透明）
        val tickAlpha = floatArrayOf(0.7f)
        GLES20.glVertexAttrib1fv(alphaHandle, tickAlpha, 0)

        // 计算实际的刻度线数量（去除原点后）
        val actualTickCount = tickCount - 2 // 去除X轴和Y轴上的原点刻度

        // 绘制X轴上的刻度线（红色）
        GLES20.glUniform4fv(colorUniformHandle, 1, floatArrayOf(1.0f, 0.0f, 0.0f, 0.7f), 0)

        // 计算X轴刻度线数量
        val xTickCount = (2.0f / tickInterval).toInt() - 1 // 去除原点

        // 绘制X轴刻度线
        for (i in 0 until xTickCount) {
            // 计算当前刻度值
            val x = -1.0f + (i + 1) * tickInterval // +1是因为我们跳过了原点

            // 判断是否是主要刻度线（0.5、1.0等）
            val isMajorTick = Math.abs(x * 10 % 5) < 0.001f

            // 主要刻度线用更粗的线宽
            if (isMajorTick) {
                GLES20.glLineWidth(1.5f)
            } else {
                GLES20.glLineWidth(1.0f)
            }

            // 每个刻度线有2个点
            GLES20.glDrawArrays(GLES20.GL_LINES, i * 2, 2)
        }

        // 绘制Y轴上的刻度线（绿色）
        GLES20.glUniform4fv(colorUniformHandle, 1, floatArrayOf(0.0f, 1.0f, 0.0f, 0.7f), 0)

        // 绘制Y轴刻度线
        for (i in 0 until xTickCount) { // Y轴刻度线数量与X轴相同
            // 计算当前刻度值
            val y = -1.0f + (i + 1) * tickInterval // +1是因为我们跳过了原点

            // 判断是否是主要刻度线（0.5、1.0等）
            val isMajorTick = Math.abs(y * 10 % 5) < 0.001f

            // 主要刻度线用更粗的线宽
            if (isMajorTick) {
                GLES20.glLineWidth(1.5f)
            } else {
                GLES20.glLineWidth(1.0f)
            }

            // 每个刻度线有2个点，从 X轴刻度线后开始
            GLES20.glDrawArrays(GLES20.GL_LINES, (xTickCount + i) * 2, 2)
        }

        // 禁用顶点属性数组
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    // 绘制彗星，接受外部传入的进度参数
    fun draw(progress: Float = -1f) { // 只传入进度参数，不再需要投影矩阵
        // 先绘制坐标系（如果启用）
        drawCoordinateSystem()

        if (vertexCount == 0) return // 如果没有顶点，则不绘制彗星

        GLES20.glUseProgram(program) // 使用此 OpenGL 程序进行绘制
        checkGlError("glUseProgram") // 检查错误

        // 在继续之前检查句柄是否有效
        if (positionHandle == -1 || alphaHandle == -1 || colorUniformHandle == -1) {
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

        // 不再需要设置矩阵，直接使用顶点位置

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
        // 从头部开始绘制
        val first = 0

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

    /**
     * 设置坐标系的显示状态
     * @param show 是否显示坐标系
     */
    fun setCoordinateSystemVisible(show: Boolean) {
        showCoordinateSystem = show
    }

    /**
     * 获取坐标系的显示状态
     * @return 是否显示坐标系
     */
    fun isCoordinateSystemVisible(): Boolean {
        return showCoordinateSystem
    }

    companion object {
        // 定义每个顶点属性的分量数量 (这部分逻辑不变)
        const val COORDS_PER_VERTEX_POS = 3 // 位置坐标数 (X, Y, Z)
        const val COORDS_PER_VERTEX_ALPHA = 1 // Alpha 分量数 (A)
    }
}

// 添加 Float.pow 扩展函数，如果项目中没有的话