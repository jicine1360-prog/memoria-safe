package com.memoria.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.memoria.R

class StatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 32f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private val statusIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.safe_green)
    }
    
    private val statusText = "SAFE"
    private var status = Status.SAFE
    private var animating = false
    private var animationFrame = 0f
    
    enum class Status {
        SAFE, WARNING, DANGER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 200
        val desiredHeight = 80
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(desiredWidth, widthSize)
            else -> desiredWidth
        }
        
        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        if (status == Status.DANGER && animating) {
            val alpha = ((animationFrame % 1f) * 255).toInt()
            canvas.drawColor(Color.argb(alpha, 255, 0, 0))
        }
        
        val statusColor: Int
        val statusText: String
        when (status) {
            Status.SAFE -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.safe_green)
                statusColor = ContextCompat.getColor(context, R.color.safe_green)
                statusText = "SAFE"
            }
            Status.WARNING -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.warning_yellow)
                statusColor = ContextCompat.getColor(context, R.color.warning_yellow)
                statusText = "WARNING"
            }
            Status.DANGER -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.emergency_red)
                statusColor = ContextCompat.getColor(context, R.color.emergency_red)
                statusText = "DANGER"
            }
        }
        
        canvas.drawCircle(centerX - 60f, centerY, 24f, statusIconPaint)
        
        statusTextPaint.color = statusColor
        canvas.drawText(statusText, centerX + 20f, centerY + 12f, statusTextPaint)
        
        if (animating) {
            animationFrame += 0.1f
            if (animationFrame > 2f) {
                animationFrame = 0f
            }
            postInvalidateOnAnimation()
        }
    }

    fun setStatus(status: Status) {
        this.status = status
        
        when (status) {
            Status.SAFE -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.safe_green)
            }
            Status.WARNING -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.warning_yellow)
            }
            Status.DANGER -> {
                statusIconPaint.color = ContextCompat.getColor(context, R.color.emergency_red)
            }
        }
        
        postInvalidate()
    }

    fun startAlertAnimation() {
        animating = true
        postInvalidateOnAnimation()
    }

    fun stopAlertAnimation() {
        animating = false
        postInvalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animating) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animating = false
    }
}
