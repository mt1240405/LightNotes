package com.lightnotes.app.ui

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.View

object HighRefreshRateHelper {

    fun applyHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val display = activity.display ?: return
                val currentMode = display.mode
                val supportedModes = display.supportedModes

                var bestMode: Display.Mode? = null
                var highestRate = currentMode.refreshRate

                for (mode in supportedModes) {
                    if (mode.physicalWidth == currentMode.physicalWidth &&
                        mode.physicalHeight == currentMode.physicalHeight
                    ) {
                        if (mode.refreshRate > highestRate && mode.refreshRate <= 144.1f) {
                            highestRate = mode.refreshRate
                            bestMode = mode
                        }
                    }
                }

                if (bestMode != null) {
                    val params = activity.window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    activity.window.attributes = params
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun optimizeScrollingContainer(view: View) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                val field = View::class.java.getField("REQUESTED_FRAME_RATE_CATEGORY_HIGH")
                val highCategory = field.getInt(null)
                val method = View::class.java.getMethod("setRequestedFrameRate", Int::class.javaPrimitiveType)
                method.invoke(view, highCategory)
            } catch (_: Throwable) {
                // Fallback gracefully on custom vendor ROMs
            }
        }
    }
}
