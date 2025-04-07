package com.juhwan.zoomista

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator

data class Zoomista(
    val config: Config = Config(),
    val targetContainer: TargetContainer,
    val targetView: View,
    val zoomInterpolator: Interpolator = AccelerateDecelerateInterpolator(),
    val zoomListener: ZoomListener? = null,
    val tapListener: TapListener? = null,
    val longPressListener: LongPressListener? = null,
    val doubleTapListener: DoubleTapListener? = null,
) {
    data class Config(
        var isZoomAnimationEnabled: Boolean = true,
        var isImmersiveModeEnabled: Boolean = true
    )

    sealed interface TargetContainer {
        data class ActivityContainer(val activity: Activity) : TargetContainer
        data class DialogContainer(val dialog: Dialog) : TargetContainer

        fun getDecorView(): ViewGroup? {
            return when (this) {
                is ActivityContainer -> activity.window.decorView as? ViewGroup
                is DialogContainer -> dialog.window?.decorView as? ViewGroup
            }
        }
    }

    fun register() {
        targetView.setOnTouchListener(
            ZoomableTouchListener(
                targetContainer = targetContainer,
                targetView = targetView,
                config = config,
                endZoomingInterpolator = zoomInterpolator,
                zoomListener = zoomListener,
                tapListener = tapListener,
                longPressListener = longPressListener,
                doubleTabListener = doubleTapListener
            )
        )
    }

    fun unregister(view: View) {
        view.setOnTouchListener(null)
    }

    interface DoubleTapListener {
        fun onDoubleTap(v: View)
    }

    interface ZoomListener {
        fun onViewStartedZooming(view: View)
        fun onViewEndedZooming(view: View)
    }

    interface TapListener {
        fun onTap(view: View)
    }

    interface LongPressListener {
        fun onLongPress(v: View)
    }
}