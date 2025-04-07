package com.juhwan.zoomista

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowInsets
import android.view.animation.Interpolator
import android.widget.ImageView
import androidx.core.animation.addListener

class ZoomableTouchListener(
    private val targetContainer: Zoomista.TargetContainer,
    private val targetView: View,
    private val config: Zoomista.Config,
    private val endZoomingInterpolator: Interpolator,
    private val zoomListener: Zoomista.ZoomListener? = null,
    tapListener: Zoomista.TapListener? = null,
    longPressListener: Zoomista.LongPressListener? = null,
    doubleTabListener: Zoomista.DoubleTapListener? = null,
) : View.OnTouchListener, ScaleGestureDetector.OnScaleGestureListener {
    private var state = State.IDLE
    private var zoomableView: ImageView? = null
    private var shadow = View(targetView.context)
    private var scaleFactor = 1f
    private var currentMovementMidPoint = PointF()
    private var initialPinchMidPoint = PointF()
    private var targetViewCords = Point()
    private var animatingZoomEnding = false
    private val scaleGestureDetector: ScaleGestureDetector = ScaleGestureDetector(targetView.context, this)
    private val gestureDetector: GestureDetector = GestureDetector(targetView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            tapListener?.onTap(targetView)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            longPressListener?.onLongPress(targetView)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleTabListener?.onDoubleTap(targetView)
            return true
        }
    })
    private val endingZoomAction = kotlinx.coroutines.Runnable {
        removeFromDecorView(shadow)
        zoomableView?.let { removeFromDecorView(it) }
        targetView.visibility = View.VISIBLE
        zoomableView = null
        currentMovementMidPoint = PointF()
        initialPinchMidPoint = PointF()
        animatingZoomEnding = false
        state = State.IDLE
        zoomListener?.onViewEndedZooming(targetView)
        if (config.isImmersiveModeEnabled) {
            targetContainer.getDecorView()?.let { showSystemUI(it) }
        }
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        fun calculateMidPoint(point: PointF, event: MotionEvent) {
            if (event.pointerCount == 2) {
                val x = event.getX(0) + event.getX(1)
                val y = event.getY(0) + event.getY(1)
                point.set(x / 2, y / 2)
            }
        }

        if (animatingZoomEnding || ev.pointerCount > 2) {
            return true
        }

        scaleGestureDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_DOWN -> {
                when (state) {
                    State.IDLE -> state = State.POINTER_DOWN
                    State.POINTER_DOWN -> {
                        state = State.ZOOMING
                        calculateMidPoint(initialPinchMidPoint, ev)
                        startZoomingView(targetView)
                    }
                    State.ZOOMING -> Unit
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (state) {
                    State.IDLE,
                    State.POINTER_DOWN -> Unit
                    State.ZOOMING -> {
                        calculateMidPoint(currentMovementMidPoint, ev)
                        currentMovementMidPoint.x -= initialPinchMidPoint.x
                        currentMovementMidPoint.y -= initialPinchMidPoint.y

                        currentMovementMidPoint.x += targetViewCords.x
                        currentMovementMidPoint.y += targetViewCords.y

                        zoomableView?.x = currentMovementMidPoint.x
                        zoomableView?.y = currentMovementMidPoint.y
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                when (state) {
                    State.IDLE -> Unit
                    State.POINTER_DOWN -> state = State.IDLE
                    State.ZOOMING -> endingZoomView()
                }
            }
        }

        return true
    }

    private fun endingZoomView() {
        fun createZoomAnimation(targetX: Float, targetY: Float): AnimatorSet {
            return AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(zoomableView, View.X, targetX),
                    ObjectAnimator.ofFloat(zoomableView, View.Y, targetY),
                    ObjectAnimator.ofFloat(zoomableView, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(zoomableView, View.SCALE_Y, 1f)
                )
                interpolator = endZoomingInterpolator
            }
        }

        if (config.isZoomAnimationEnabled) {
            animatingZoomEnding = true
            createZoomAnimation(targetViewCords.x.toFloat(), targetViewCords.y.toFloat()).apply {
                addListener(onEnd = { endingZoomAction.run() })
                start()
            }
        } else {
            endingZoomAction.run()
        }
    }

    private fun startZoomingView(view: View) {
        zoomableView = ImageView(targetView.context)
        zoomableView?.layoutParams = ViewGroup.LayoutParams(targetView.width, targetView.height)
        zoomableView?.setImageBitmap(getBitmapFromView(view))

        targetViewCords = getViewAbsoluteCords(view)

        zoomableView?.x = targetViewCords.x.toFloat()
        zoomableView?.y = targetViewCords.y.toFloat()

        shadow.setBackgroundResource(REMOVE_BACKGROUND_VALUE)

        addToDecorView(shadow)
        zoomableView?.let { addToDecorView(it) }

        disableParentTouch(targetView.parent)
        targetView.visibility = View.INVISIBLE

        if (config.isImmersiveModeEnabled) {
            targetContainer.getDecorView()?.let { hideSystemUI(it) }
        }
        zoomListener?.onViewStartedZooming(targetView)
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        if (zoomableView == null) return false

        scaleFactor *= detector.scaleFactor
        scaleFactor = scaleFactor.coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)

        zoomableView?.apply {
            scaleX = scaleFactor
            scaleY = scaleFactor
        }
        obscureDecorView(scaleFactor)
        return true
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        return zoomableView != null
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {
        scaleFactor = 1f
    }

    private fun addToDecorView(v: View) {
        targetContainer.getDecorView()?.addView(v)
    }

    private fun removeFromDecorView(v: View) {
        targetContainer.getDecorView()?.removeView(v)
    }

    private fun obscureDecorView(factor: Float) {
        val normalizedValue = (factor - MIN_SCALE_FACTOR) / (MAX_SCALE_FACTOR - MIN_SCALE_FACTOR)
        val clampedValue = normalizedValue.coerceAtMost(0.75f) * 2
        val obscureColor = Color.argb((clampedValue * 255).toInt(), 0, 0, 0)
        shadow.setBackgroundColor(obscureColor)
    }

    private fun hideSystemUI(decorView: ViewGroup) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.windowInsetsController?.hide(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun showSystemUI(decorView: ViewGroup) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decorView.windowInsetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun disableParentTouch(view: ViewParent) {
        var parent: ViewParent? = view
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true)
            parent = parent.parent
        }
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        view.draw(canvas)
        return returnedBitmap
    }

    private fun getViewAbsoluteCords(v: View): Point {
        val location = IntArray(2)
        v.getLocationInWindow(location)
        val x = location[0]
        val y = location[1]

        return Point(x, y)
    }

    enum class State {
        IDLE,
        POINTER_DOWN,
        ZOOMING,
    }

    companion object {
        const val MIN_SCALE_FACTOR = 1f
        const val MAX_SCALE_FACTOR = 5f
        const val REMOVE_BACKGROUND_VALUE = 0
    }
}