package com.memoria.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.memoria.R

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.dark_surface_variant)
    }
    
    private val waveformColors = intArrayOf(
        ContextCompat.getColor(context, R.color.waveform_active),
        ContextCompat.getColor(context, R.color.waveform_active),
        ContextCompat.getColor(context, R.color.waveform_active)
    )
    
    private var levels = FloatArray(40) { 0f }
    private var shader: LinearGradient? = null
    private var running = false
    private var phase = 0f
    private var lastLevel = 0.5f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            waveformColors,
            null,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2f
        
        // Draw background
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)
        
        // Update waveform data
        if (running) {
            updateWaveform()
        }
        
        // Draw waveform
        paint.shader = shader
        
        val path = Path()
        path.moveTo(0f, centerY)
        
        val barWidth = width / levels.size
        
        for (i in levels.indices) {
            val level = levels[i]
            val x = i * barWidth
            val y = centerY - (level * height / 2)
            
            path.lineTo(x, y)
        }
        
        path.lineTo(width, centerY)
        canvas.drawPath(path, paint)
        
        if (running) {
            phase += 0.5f
            postInvalidateOnAnimation()
        }
    }

    fun startRecording() {
        running = true
        postInvalidateOnAnimation()
    }

    fun stopRecording() {
        running = false
        postInvalidate()
    }

    fun updateLevel(level: Float) {
        lastLevel = level.coerceIn(0f, 1f)
    }

    private fun updateWaveform() {
        for (i in 0 until levels.size - 1) {
            levels[i] = levels[i + 1]
        }
        
        val noise = (Math.random() * 0.3 - 0.15).toFloat()
        levels[levels.size - 1] = lastLevel * (0.5f + 0.5f * Math.sin(phase * Math.PI / 180).toFloat()) + noise
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }
}
