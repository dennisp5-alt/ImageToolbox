/*
 * Smart Auto module for ImageToolbox (fork by dennisp5-alt)
 * Licensed under the Apache License, Version 2.0
 */

package com.t8rin.imagetoolbox.feature.smart_auto

import android.graphics.Bitmap
import com.t8rin.imagetoolbox.core.filters.presentation.model.UiFilter
import java.util.Locale

data class SmartAutoResult(
    val stats: SmartAutoStats,
    val params: SmartAutoParams,
    val filters: List<UiFilter<*>>
) {
    /** Human-readable debug summary for the overlay card. */
    val report: String
        get() = buildString {
            appendLine("Measured")
            appendLine(
                "  luma  p0.5 ${stats.p005.f()}  p25 ${stats.p25.f()}  " +
                        "med ${stats.median.f()}  p75 ${stats.p75.f()}  p99.5 ${stats.p995.f()}"
            )
            appendLine(
                "  dark ${stats.darkShare.pct()}  sat ${stats.avgSaturation.f()}  " +
                        "neutral ${stats.neutralShare.pct()}"
            )
            appendLine(
                "  RGB ${stats.meanR.f()} / ${stats.meanG.f()} / ${stats.meanB.f()}  " +
                        "green ${stats.greenShare.pct()}  yellow ${stats.yellowShare.pct()}"
            )
            appendLine("Applied")
            appendLine(
                "  black ${params.blackPoint.f()}  white ${params.whitePoint.f()}  " +
                        "mid ${params.midIn.f()}→${params.midOut.f()}"
            )
            appendLine("  WB  R×${params.gainR.f()}  B×${params.gainB.f()}")
            appendLine(
                "  sat  greens ${params.greenSatDelta.signed()}  yellows ${params.yellowSatDelta.signed()}"
            )
            appendLine(
                "  shadows ${params.shadowLift.signed()}  vibrance ${params.vibrance.signed()}"
            )
            append("Filters added: ${filters.size}")
        }

    private fun Float.f() = String.format(Locale.US, "%.2f", this)
    private fun Float.signed() = String.format(Locale.US, "%+.2f", this)
    private fun Float.pct() = String.format(Locale.US, "%.0f%%", this * 100f)
}

/**
 * Entry point. Call off the main thread.
 *
 * Swap [SmartAutoAnalyser.decide] for the LumaWorks Smart Auto logic and the
 * rest (mapping to filters, UI, batch) keeps working unchanged.
 */
object SmartAuto {
    fun run(bitmap: Bitmap): SmartAutoResult {
        val stats = SmartAutoAnalyser.measure(bitmap)
        val params = SmartAutoAnalyser.decide(stats)
        return SmartAutoResult(
            stats = stats,
            params = params,
            filters = SmartAutoMapper.toFilters(params)
        )
    }
}
