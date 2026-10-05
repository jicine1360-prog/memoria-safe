package com.memoria.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.memoria.R

class SOSButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.emergency_red)
        style = Paint.Style.FILL
    }
    
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 100
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        textSize = 64f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private var radius: Float = 0f
    private var rippleRadius: Float = 0f
    private var pressed = false
    private var rippleExpansion = 0f
    
    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.SOSButton)
        radius = typedArray.getDimension(R.styleable.SOSButton_android_radius, 75f)
        typedArray.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        radius = (minOf(w, h) / 2).toFloat()
        super.onSizeChanged(w, h, oldw, oldh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        canvas.drawCircle(centerX, centerY, radius * (1 + rippleExpansion), ripplePaint)
        canvas.drawCircle(centerX, centerY, radius, basePaint)
        
        val text = context.getString(R.string.sos_text)
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textY = centerY - (textBounds.bottom + textBounds.top) / 2f
        
        canvas.drawText(text, centerX, textY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                rippleExpansion = 0.1f
                postInvalidateOnAnimation()
                
                performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                
                postDelayed({
                    if (pressed) {
                        performLongClick()
                    }
                }, 3000)
                
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.action == MotionEvent.ACTION_UP && pressed) {
                    performClick()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                
                pressed = false
                rippleExpansion = 0f
                postInvalidateOnAnimation()
                
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }

    fun setRippleColor(color: Int) {
        ripplePaint.color = color
        postInvalidate()
    }

    fun setBaseColor(color: Int) {
        basePaint.color = color
        postInvalidate()
    }

    fun setTextSize(size: Float) {
        textPaint.textSize = size
        postInvalidate()
    }
}
