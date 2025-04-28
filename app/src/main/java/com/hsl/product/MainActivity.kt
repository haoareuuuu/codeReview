package com.hsl.product

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity // Changed from ComponentActivity
import com.hsl.product.R // Import R class

class MainActivity : AppCompatActivity() { // Changed from ComponentActivity

    private lateinit var glSurfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view to the XML layout
        setContentView(R.layout.activity_main)

        // Initialize GLSurfaceView
        glSurfaceView = findViewById(R.id.glSurfaceView)

        // Set OpenGL ES client version
        glSurfaceView.setEGLContextClientVersion(2)

        // Set the Renderer for drawing on the GLSurfaceView
        glSurfaceView.setRenderer(CometRenderer(this))

        // Render the view continuously for animation
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
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