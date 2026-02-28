package com.example.susapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CircleGestureView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.parseColor("#FF4081") // Kitty pink
        strokeWidth = 15f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private var minX = Float.MAX_VALUE
    private var maxX = Float.MIN_VALUE
    private var minY = Float.MAX_VALUE
    private var maxY = Float.MIN_VALUE
    private var startX = 0f
    private var startY = 0f
    
    var onCircleDrawnListener: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.reset()
                path.moveTo(x, y)
                startX = x
                startY = y
                
                minX = x
                maxX = x
                minY = y
                maxY = y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            MotionEvent.ACTION_UP -> {
                // Determine if the completed path roughly forms a circle
                val endX = x
                val endY = y
                
                // Check if starting and ending points are relatively close (closed loop)
                val isClosed = Math.hypot((endX - startX).toDouble(), (endY - startY).toDouble()) < 200
                
                val width = maxX - minX
                val height = maxY - minY
                
                // Ensure it's not just a tiny dot/scribble
                val isBigEnough = width > 200 && height > 200
                
                // Ensure it resembles a square-ish bounding box (circles don't have elongated boxes)
                val ratio = if (height != 0f) width / height else 0f
                val isRound = ratio > 0.5f && ratio < 1.5f
                
                if (isClosed && isBigEnough && isRound) {
                    onCircleDrawnListener?.invoke()
                    path.reset() // Clear after success
                } else {
                    // Didn't count as a circle, just clear it after a moment
                    postDelayed({
                        path.reset()
                        invalidate()
                    }, 500)
                }
            }
        }
        invalidate()
        return true
    }
}
