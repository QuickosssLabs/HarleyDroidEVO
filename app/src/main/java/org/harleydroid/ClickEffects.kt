//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
package org.harleydroid

import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.view.menu.ActionMenuItemView

/**
 * Material Toolbar / menus inject RippleDrawable even when theme backgrounds are transparent.
 * Strip ripples and mute system key-click sounds so taps don't stack "clic clic clic".
 */
object ClickEffects {

	@JvmStatic
	fun strip(root: View?) {
		if (root == null) return
		stripView(root)
		if (root is ViewGroup) {
			for (i in 0 until root.childCount) {
				strip(root.getChildAt(i))
			}
		}
	}

	private fun stripView(view: View) {
		view.isSoundEffectsEnabled = false
		if (view.background is RippleDrawable || view is ActionMenuItemView) {
			view.background = null
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && view.foreground is RippleDrawable) {
			view.foreground = null
		}
		if (view is ImageButton && view.background is RippleDrawable) {
			view.background = null
		}
	}
}
