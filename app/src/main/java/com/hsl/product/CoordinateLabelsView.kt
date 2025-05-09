package com.hsl.product

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * 坐标刻度标注View
 * 用于在GLSurfaceView上方绘制坐标刻度值
 */
class CoordinateLabelsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 控制坐标标注的显示
    private var showLabels = true

    // 刻度间隔
    private val tickInterval = 0.2f

    // 文本画笔
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(1.5f, 0f, 0f, Color.BLACK) // 添加阴影使文字在任何背景上都清晰可见
    }

    // X轴标签画笔（红色）
    private val xLabelPaint = Paint().apply {
        color = Color.RED
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(1.5f, 0f, 0f, Color.BLACK)
    }

    // Y轴标签画笔（绿色）
    private val yLabelPaint = Paint().apply {
        color = Color.GREEN
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        setShadowLayer(1.5f, 0f, 0f, Color.BLACK)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showLabels) return

        // 计算保持宽高比例为1:1的正方形区域
        val size = Math.min(width, height)
        val x = (width - size) / 2f
        val y = (height - size) / 2f

        // 计算坐标系原点在屏幕上的位置（正方形区域的中心）
        val originX = width / 2f
        val originY = height / 2f

        // 计算坐标系单位长度（正方形的一半）
        val unitLength = size / 2f

        // 绘制X轴刻度值
        for (i in -5..5) {
            // 只绘制主要刻度（0.5的倍数）
            if (i % 5 != 0 && i != 0) continue

            val x = i * tickInterval
            // 跳过原点
            if (Math.abs(x) < 0.001f) continue

            // 计算刻度在屏幕上的位置
            val screenX = originX + x * unitLength
            val screenY = originY + 25f // 稍微偏下，避免遮挡刻度线

            // 确保刻度值在正方形区域内
            if (screenX < x || screenX > x + size) continue

            // 绘制刻度值
            canvas.drawText(
                String.format("%.1f", x),
                screenX,
                screenY,
                xLabelPaint
            )
        }

        // 绘制Y轴刻度值
        for (i in -5..5) {
            // 只绘制主要刻度（0.5的倍数）
            if (i % 5 != 0 && i != 0) continue

            val y = i * tickInterval
            // 跳过原点
            if (Math.abs(y) < 0.001f) continue

            // 计算刻度在屏幕上的位置
            val screenX = originX - 35f // 稍微偏左，避免遮挡刻度线
            val screenY = originY - y * unitLength // 注意Y轴方向是相反的

            // 确保刻度值在正方形区域内
            if (screenY < y || screenY > y + size) continue

            // 绘制刻度值
            canvas.drawText(
                String.format("%.1f", y),
                screenX,
                screenY + 8f, // 稍微调整，使文字垂直居中对齐刻度线
                yLabelPaint
            )
        }

        // 绘制原点标签
        canvas.drawText(
            "0.0",
            originX - 35f,
            originY + 25f,
            textPaint
        )

        // 绘制坐标范围提示
        val rangeText = "X: [-1, 1], Y: [-1, 1]"
        canvas.drawText(
            rangeText,
            width / 2f,
            height - 20f, // 底部位置
            textPaint
        )
    }

    /**
     * 设置标签的显示状态
     * @param show 是否显示标签
     */
    fun setLabelsVisible(show: Boolean) {
        if (showLabels != show) {
            showLabels = show
            invalidate() // 重绘视图
        }
    }

    /**
     * 获取标签的显示状态
     * @return 是否显示标签
     */
    fun isLabelsVisible(): Boolean {
        return showLabels
    }
}
