/*
 * Smart Auto module for ImageToolbox (fork by dennisp5-alt)
 * Licensed under the Apache License, Version 2.0
 */

package com.t8rin.imagetoolbox.feature.smart_auto

import com.t8rin.imagetoolbox.core.filters.domain.model.Filter
import com.t8rin.imagetoolbox.core.filters.domain.model.FilterValues
import com.t8rin.imagetoolbox.core.filters.presentation.model.UiFilter
import com.t8rin.imagetoolbox.core.filters.presentation.model.createUiFilter
import kotlin.math.abs

/**
 * Turns [SmartAutoParams] into ordinary, editable ImageToolbox filters.
 *
 * Chain:
 *  1. Tone Curves  – luma curve (black/white point, midtone, contrast),
 *                    R/B channel curves (white balance), Hue-vs-Sat curve
 *                    (green/yellow pull-back). One filter, editable in the curves editor.
 *  2. Highlights & Shadows – shadow lift, only when needed.
 *  3. Vibrance – only when needed.
 */
object SmartAutoMapper {

    // Curve slots used by ImageCurvesEditorState (lib/curves).
    private const val LumaIndex = 0
    private const val RedIndex = 1
    private const val BlueIndex = 3
    private const val HueVsSatIndex = 11

    fun toFilters(params: SmartAutoParams): List<UiFilter<*>> = buildList {
        toneCurves(params)?.let { points ->
            add(createUiFilter(Filter.ToneCurves::class, FilterValues.toneCurves(points)))
        }
        if (params.shadowLift > 0.02f) {
            add(
                createUiFilter(
                    Filter.HighlightsAndShadows::class,
                    FilterValues.highlightsAndShadows(params.shadowLift)
                )
            )
        }
        if (abs(params.vibrance) > 0.05f) {
            add(createUiFilter(Filter.Vibrance::class, FilterValues.vibrance(params.vibrance)))
        }
    }

    private fun toneCurves(params: SmartAutoParams): List<List<Float>>? {
        val luma = lumaCurve(params)
        val red = channelCurve(params.gainR)
        val blue = channelCurve(params.gainB)
        val hueVsSat = hueVsSatCurve(params)

        if (luma == null && red == null && blue == null && hueVsSat == null) return null

        val identity = listOf(0f, 0f, 1f, 1f)
        val curves = MutableList(HueVsSatIndex + 1) { identity }
        luma?.let { curves[LumaIndex] = it }
        red?.let { curves[RedIndex] = it }
        blue?.let { curves[BlueIndex] = it }
        curves[HueVsSatIndex] = hueVsSat ?: listOf(0f, 0.5f, 1f, 0.5f)
        return curves
    }

    private fun lumaCurve(p: SmartAutoParams): List<Float>? {
        val raw = buildList {
            add(0f to 0f)
            if (p.blackPoint > 0.004f) add(p.blackPoint to 0f)
            add(p.p25In to p.p25Out)
            add(p.midIn to p.midOut)
            add(p.p75In to p.p75Out)
            if (p.whitePoint < 0.996f) add(p.whitePoint to 1f)
            add(1f to 1f)
        }

        // Keep x strictly increasing with a minimum gap and y monotonic,
        // so the spline can't fold back on itself.
        val points = mutableListOf<Pair<Float, Float>>()
        raw.sortedBy { it.first }.forEach { (x, y) ->
            val last = points.lastOrNull()
            if (last == null) {
                points += x to y
            } else if (x - last.first >= 0.03f || x >= 1f) {
                val safeX = if (x >= 1f) 1f else x
                if (safeX > last.first) points += safeX to y.coerceAtLeast(last.second)
            }
        }

        val isIdentity = points.all { (x, y) -> abs(x - y) < 0.01f }
        if (isIdentity) return null
        return points.flatMap { listOf(it.first, it.second.coerceIn(0f, 1f)) }
    }

    /** Midtone gain curve: neutral black and white, gain applied around 0.5. */
    private fun channelCurve(gain: Float): List<Float>? {
        if (abs(gain - 1f) < 0.01f) return null
        return listOf(
            0f, 0f,
            0.5f, (0.5f * gain).coerceIn(0.05f, 0.95f),
            1f, 1f
        )
    }

    /**
     * Circular hue curve, 0.5 = no change. In the ImageToolbox shader a
     * negative delta subtracts (delta * 2) saturation, so y = 0.5 + d / 2.
     */
    private fun hueVsSatCurve(p: SmartAutoParams): List<Float>? {
        if (p.greenSatDelta > -0.005f && p.yellowSatDelta > -0.005f) return null
        val yellowY = 0.5f + p.yellowSatDelta / 2f
        val greenY = 0.5f + p.greenSatDelta / 2f
        val points = listOf(
            0f to 0.5f,
            0.03f to 0.5f,   // red
            0.08f to 0.5f,   // orange / skin – protected
            0.115f to 0.5f,
            0.16f to yellowY, // yellow ~58°
            0.25f to (yellowY + greenY) / 2f,
            0.33f to greenY,  // green ~120°
            0.42f to 0.5f + (greenY - 0.5f) * 0.4f,
            0.50f to 0.5f,   // cyan
            0.62f to 0.5f,
            0.75f to 0.5f,   // magenta-blue
            0.88f to 0.5f,
            0.97f to 0.5f,
            1f to 0.5f
        )
        return points.flatMap { listOf(it.first, it.second) }
    }
}
