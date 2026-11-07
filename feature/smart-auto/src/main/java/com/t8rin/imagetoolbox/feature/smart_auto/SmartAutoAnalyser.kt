/*
 * Smart Auto module for ImageToolbox (fork by dennisp5-alt)
 * Licensed under the Apache License, Version 2.0
 */

package com.t8rin.imagetoolbox.feature.smart_auto

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Measurements taken from a downscaled copy of the photo.
 * All luma values are sRGB-encoded, 0..1.
 */
data class SmartAutoStats(
    val p005: Float,
    val p25: Float,
    val median: Float,
    val p75: Float,
    val p995: Float,
    val darkShare: Float,
    val avgSaturation: Float,
    val neutralShare: Float,
    val meanR: Float,
    val meanG: Float,
    val meanB: Float,
    val greenShare: Float,
    val yellowShare: Float,
    val sampleCount: Int
)

/**
 * Edit decisions. Everything is expressed relative to neutral so a value
 * of 0 (or a gain of 1) means "leave alone".
 */
data class SmartAutoParams(
    val blackPoint: Float,
    val whitePoint: Float,
    val p25In: Float,
    val p25Out: Float,
    val midIn: Float,
    val midOut: Float,
    val p75In: Float,
    val p75Out: Float,
    val gainR: Float,
    val gainB: Float,
    val greenSatDelta: Float,
    val yellowSatDelta: Float,
    val shadowLift: Float,
    val vibrance: Float
)

/**
 * Starter analyser. Replace [decide] with the LumaWorks Smart Auto logic;
 * the mapper only needs a [SmartAutoParams].
 */
object SmartAutoAnalyser {

    private const val AnalysisLongEdge = 512
    private const val TargetMid = 0.46f

    fun measure(source: Bitmap): SmartAutoStats {
        val small = source.toAnalysisBitmap()
        val width = small.width
        val height = small.height
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== source) small.recycle()

        val histogram = IntArray(256)
        val hsv = FloatArray(3)
        var dark = 0
        var satSum = 0.0
        var satCount = 0
        var neutralCount = 0
        var nR = 0.0
        var nG = 0.0
        var nB = 0.0
        var aR = 0.0
        var aG = 0.0
        var aB = 0.0
        var midCount = 0
        var green = 0
        var yellow = 0

        for (c in pixels) {
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).roundToInt().coerceIn(0, 255)
            histogram[luma]++
            if (luma < 31) dark++

            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            if (value > 0.1f) {
                satSum += sat
                satCount++
            }
            if (sat > 0.45f && value > 0.2f) {
                if (hue in 75f..160f) green++
                else if (hue in 45f..75f) yellow++
            }
            if (luma in 38..230) {
                aR += r; aG += g; aB += b
                midCount++
                if (sat < 0.30f) {
                    nR += r; nG += g; nB += b
                    neutralCount++
                }
            }
        }

        val n = pixels.size.coerceAtLeast(1)
        fun percentile(p: Float): Float {
            val target = (n * p).toInt()
            var acc = 0
            for (i in 0..255) {
                acc += histogram[i]
                if (acc > target) return i / 255f
            }
            return 1f
        }

        // Prefer near-neutral pixels for the white-balance estimate, fall back to all midtones.
        val useNeutral = neutralCount > n * 0.02f
        val count = (if (useNeutral) neutralCount else midCount).coerceAtLeast(1).toDouble()
        val sumR = if (useNeutral) nR else aR
        val sumG = if (useNeutral) nG else aG
        val sumB = if (useNeutral) nB else aB

        return SmartAutoStats(
            p005 = percentile(0.005f),
            p25 = percentile(0.25f),
            median = percentile(0.5f),
            p75 = percentile(0.75f),
            p995 = percentile(0.995f),
            darkShare = dark.toFloat() / n,
            avgSaturation = if (satCount > 0) (satSum / satCount).toFloat() else 0f,
            neutralShare = neutralCount.toFloat() / n,
            meanR = (sumR / count / 255.0).toFloat(),
            meanG = (sumG / count / 255.0).toFloat(),
            meanB = (sumB / count / 255.0).toFloat(),
            greenShare = green.toFloat() / n,
            yellowShare = yellow.toFloat() / n,
            sampleCount = n
        )
    }

    fun decide(stats: SmartAutoStats): SmartAutoParams {
        // Tone: gentle black/white point, capped so we never crush or blow intent.
        val black = if (stats.p005 > 0.01f) stats.p005.coerceAtMost(0.08f) else 0f
        val white = if (stats.p995 < 0.99f) stats.p995.coerceAtLeast(0.90f) else 1f
        val range = (white - black).coerceAtLeast(0.2f)
        fun stretch(x: Float) = ((x - black) / range).coerceIn(0f, 1f)

        val midStretched = stretch(stats.median)
        val midDelta = ((TargetMid - midStretched) * 0.5f).coerceIn(-0.10f, 0.10f)
        val midOut = (midStretched + midDelta).coerceIn(0.05f, 0.95f)

        val spread = stretch(stats.p75) - stretch(stats.p25)
        val contrast = ((0.40f - spread) * 0.3f).coerceIn(-0.02f, 0.05f)
        val p25Out = (stretch(stats.p25) + midDelta * 0.7f - contrast).coerceIn(0.02f, midOut - 0.02f)
        val p75Out = (stretch(stats.p75) + midDelta * 0.7f + contrast).coerceIn(midOut + 0.02f, 0.98f)

        // White balance: grey-world on near-neutrals, half strength, and weaker
        // when the cast is strong (sunsets, tungsten scenes you want to keep).
        val g = stats.meanG.coerceAtLeast(0.01f)
        val rawR = g / stats.meanR.coerceAtLeast(0.01f)
        val rawB = g / stats.meanB.coerceAtLeast(0.01f)
        val cast = max(abs(rawR - 1f), abs(rawB - 1f))
        val wbStrength = when {
            cast > 0.25f -> 0.25f
            cast > 0.15f -> 0.4f
            else -> 0.5f
        } * if (stats.neutralShare > 0.02f) 1f else 0.5f
        val gainR = (1f + (rawR - 1f) * wbStrength).coerceIn(0.88f, 1.12f)
        val gainB = (1f + (rawB - 1f) * wbStrength).coerceIn(0.88f, 1.12f)

        // Samsung-style green/yellow push: pull saturation back in proportion
        // to how much of the frame is saturated green or yellow.
        val greenSatDelta = -(stats.greenShare * 0.25f).coerceIn(0f, 0.08f)
        val yellowSatDelta = -(stats.yellowShare * 0.25f).coerceIn(0f, 0.06f)

        val shadowLift = ((stats.darkShare - 0.12f) * 1.2f).coerceIn(0f, 0.35f)
        val vibrance = ((0.30f - stats.avgSaturation) * 5f).coerceIn(-0.8f, 1.2f)

        return SmartAutoParams(
            blackPoint = black,
            whitePoint = white,
            p25In = stats.p25,
            p25Out = p25Out,
            midIn = stats.median,
            midOut = midOut,
            p75In = stats.p75,
            p75Out = p75Out,
            gainR = gainR,
            gainB = gainB,
            greenSatDelta = greenSatDelta,
            yellowSatDelta = yellowSatDelta,
            shadowLift = shadowLift,
            vibrance = vibrance
        )
    }

    private fun Bitmap.toAnalysisBitmap(): Bitmap {
        val readable = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE
        ) copy(Bitmap.Config.ARGB_8888, false) else this
        val longEdge = max(readable.width, readable.height)
        if (longEdge <= AnalysisLongEdge) return readable
        val factor = AnalysisLongEdge.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            readable,
            (readable.width * factor).roundToInt().coerceAtLeast(1),
            (readable.height * factor).roundToInt().coerceAtLeast(1),
            true
        )
        if (readable !== this) readable.recycle()
        return scaled
    }
}
