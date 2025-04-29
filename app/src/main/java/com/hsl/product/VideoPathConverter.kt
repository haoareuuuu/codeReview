package com.hsl.product

import android.graphics.PointF
import java.util.ArrayList

/**
 * 视频路径转换工具类
 * 负责将视频坐标数据转换为OpenGL可用的路径点
 */
class VideoPathConverter {

    companion object {
        /**
         * 将视频坐标转换为OpenGL坐标
         * @param videoCoordinates 视频坐标列表，格式为 [x1, y1, x2, y2, ...]
         * @param videoWidth 视频宽度
         * @param videoHeight 视频高度
         * @return 转换后的OpenGL坐标点列表
         */
        fun convertVideoCoordinatesToOpenGL(
            videoCoordinates: List<Float>,
            videoWidth: Int,
            videoHeight: Int
        ): List<PointF> {
            val result = ArrayList<PointF>()

            // 确保坐标数量是偶数
            if (videoCoordinates.size % 2 != 0) {
                throw IllegalArgumentException("视频坐标数量必须是偶数")
            }

            // 转换每一对坐标
            for (i in videoCoordinates.indices step 2) {
                if (i + 1 < videoCoordinates.size) {
                    val videoX = videoCoordinates[i]
                    val videoY = videoCoordinates[i + 1]

                    // 将视频坐标转换为OpenGL坐标 (-1.0 到 1.0 范围)
                    // 注意：OpenGL坐标系原点在中心，Y轴向上为正
                    val openGLX = (videoX / videoWidth) * 2.0f - 1.0f
                    val openGLY = -((videoY / videoHeight) * 2.0f - 1.0f) // Y轴翻转

                    result.add(PointF(openGLX, openGLY))
                }
            }

            return result
        }

//        /**
//         * 将路径数据添加到Intent中
//         * @param intent 目标Intent
//         * @param path 路径点列表
//         * @return 添加了路径数据的Intent
//         */
//        fun addPathToIntent(intent: Intent, path: List<PointF>): Intent {
//            val pathArrayList = ArrayList<PointF>(path)
//            intent.putParcelableArrayListExtra("samplePath", pathArrayList as ArrayList<out Parcelable>)
//            return intent
//        }

        /**
         * 示例：如何在业务层使用此工具类
         */
        fun exampleUsage(): List<PointF> {

            val viewHeight = 1920f
            val viewWidth = 1080f

            // 定义输入点 (使用 PointF)
            val inputPoints = listOf(
                PointF(100f, viewHeight / 2f + 200f),
                PointF(200f, viewHeight / 2f),
                PointF(300f, viewHeight / 2f - 100f),
                PointF(400f, viewHeight / 2f - 150f),
                PointF(500f, viewHeight / 2f - 150f),
                PointF(600f, viewHeight / 2f - 100f),
                PointF(700f, viewHeight / 2f),
                PointF(800f, viewHeight / 2f + 200f)
            )

            // 生成拟合贝塞尔曲线上的点
            val videoCoordinates = generatePointsOnBezierCurve(
                inputPoints,
                100 // 需要生成100个点
            )

            // 视频尺寸 (假设与视图尺寸相同，用于OpenGL转换)
            val videoWidth = 1080
            val videoHeight = 1920

            // 转换坐标为OpenGL坐标
            val openGLPath = convertVideoCoordinatesToOpenGL(
                videoCoordinates,
                videoWidth,
                videoHeight
            )
            return openGLPath
            // ... (Intent 相关代码保持注释)
        }
        /**
         * 根据输入点列表拟合一条复合三次贝塞尔曲线，并沿曲线采样指定数量的点。
         * 使用 Catmull-Rom 样条生成控制点，确保曲线通过所有输入点。
         *
         * @param inputPoints 输入的点列表 (至少需要2个点才能形成曲线段)
         * @param numOutputPoints 需要生成的输出点数量 (沿整个曲线路径均匀分布)
         * @return 包含采样点坐标的列表，格式为 [x1, y1, x2, y2, ...]
         */
        fun generatePointsOnBezierCurve(
            inputPoints: List<PointF>,
            numOutputPoints: Int
        ): List<Float> {
            if (inputPoints.size < 2) {
                // 如果点数少于2，无法生成曲线，可以返回空列表或包含单个点的列表
                return inputPoints.flatMap { listOf(it.x, it.y) }
            }
            if (numOutputPoints <= 0) {
                return emptyList()
            }
            if (numOutputPoints == 1) {
                return listOf(inputPoints.first().x, inputPoints.first().y)
            }

            val result = ArrayList<Float>(numOutputPoints * 2)
            val numSegments = inputPoints.size - 1

            // Catmull-Rom 需要前后点，处理端点情况 (重复首尾点)
            val points = mutableListOf<PointF>()
            points.add(inputPoints.first()) // 重复第一个点 P(-1) = P0
            points.addAll(inputPoints)
            points.add(inputPoints.last())  // 重复最后一个点 P(n) = P(n-1)

            val bezierSegments = mutableListOf<Triple<PointF, PointF, PointF>>() // Control1, Control2, EndPoint

            // 生成每个 Catmull-Rom 段对应的三次贝塞尔控制点
            for (i in 0 until numSegments) {
                val p0 = points[i]     // 对应 Catmull-Rom P(i-1)
                val p1 = points[i + 1] // 对应 Catmull-Rom P(i)   -> Bezier Start
                val p2 = points[i + 2] // 对应 Catmull-Rom P(i+1) -> Bezier End
                val p3 = points[i + 3] // 对应 Catmull-Rom P(i+2)

                // Catmull-Rom to Bezier control points (tension t=0)
                // Control Point 1 = P1 + (P2 - P0) / 6
                val c1x = p1.x + (p2.x - p0.x) / 6.0f
                val c1y = p1.y + (p2.y - p0.y) / 6.0f
                val control1 = PointF(c1x, c1y)

                // Control Point 2 = P2 - (P3 - P1) / 6
                val c2x = p2.x - (p3.x - p1.x) / 6.0f
                val c2y = p2.y - (p3.y - p1.y) / 6.0f
                val control2 = PointF(c2x, c2y)

                bezierSegments.add(Triple(control1, control2, p2))
            }

            // 采样 numOutputPoints 个点，均匀分布在所有段上
            // 注意：这里是按参数 t 均匀采样，不是按弧长均匀采样，对于速度变化大的曲线可能不均匀
            val totalSteps = numOutputPoints - 1
            result.add(inputPoints.first().x) // 添加第一个点
            result.add(inputPoints.first().y)

            for (step in 1..totalSteps) {
                val globalT = step.toFloat() / totalSteps // 全局参数 [0, 1]
                val targetSegmentIndex = (globalT * numSegments).toInt().coerceAtMost(numSegments - 1)
                val segmentTStart = targetSegmentIndex.toFloat() / numSegments
                val segmentTEnd = (targetSegmentIndex + 1).toFloat() / numSegments
                // 将全局 t 映射到当前段的局部 t [0, 1]
                val localT = if (segmentTEnd == segmentTStart) 0f else (globalT - segmentTStart) / (segmentTEnd - segmentTStart)

                val startPoint = inputPoints[targetSegmentIndex]
                val (control1, control2, endPoint) = bezierSegments[targetSegmentIndex]

                // 计算三次贝塞尔曲线上的点 B(t)
                val t = localT
                val tInv = 1.0f - t
                val tInvSq = tInv * tInv
                val tSq = t * t

                val bx = tInvSq * tInv * startPoint.x + 3 * tInvSq * t * control1.x + 3 * tInv * tSq * control2.x + tSq * t * endPoint.x
                val by = tInvSq * tInv * startPoint.y + 3 * tInvSq * t * control1.y + 3 * tInv * tSq * control2.y + tSq * t * endPoint.y

                result.add(bx)
                result.add(by)
            }

            // 如果由于浮点精度问题导致点数不足，补充最后一个点
            if (result.size < numOutputPoints * 2 && inputPoints.isNotEmpty()) {
                 if (result.size < 2 || result[result.size-2] != inputPoints.last().x || result.last() != inputPoints.last().y) {
                    result.add(inputPoints.last().x)
                    result.add(inputPoints.last().y)
                 }
            }
            // 如果点数超出，截断
            while (result.size > numOutputPoints * 2) {
                result.removeAt(result.size - 1)
                result.removeAt(result.size - 1)
            }


            return result
        }
    }


}
