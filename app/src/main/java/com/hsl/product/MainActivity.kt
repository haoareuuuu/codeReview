package com.hsl.product

import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity // Changed from ComponentActivity
import com.hsl.product.R // Import R class
import com.hsl.product.VideoPathConverter // Import VideoPathConverter

class MainActivity : AppCompatActivity() { // Changed from ComponentActivity

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cometRenderer: CometRenderer
    private lateinit var progressSeekBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view to the XML layout
        setContentView(R.layout.activity_main)

        // Initialize GLSurfaceView
        glSurfaceView = findViewById(R.id.glSurfaceView)
        progressSeekBar = findViewById(R.id.progressSeekBar)

        // Set OpenGL ES client version
        glSurfaceView.setEGLContextClientVersion(2)

        // 从业务层获取路径数据 (通过Intent或其他方式传入)
        // 这里假设路径数据已经在业务层转换好并通过Intent传入
        val samplePath = VideoPathConverter.exampleUsage()
        // Set the Renderer for drawing on the GLSurfaceView
        cometRenderer = CometRenderer(this, samplePath)
        glSurfaceView.setRenderer(cometRenderer)

        // Render the view continuously for animation
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // 设置进度条监听器
        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 将进度值转换为0-1范围
                val normalizedProgress = progress / 100f
                // 设置渲染器的绘制进度
                cometRenderer.setDrawProgress(normalizedProgress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
}