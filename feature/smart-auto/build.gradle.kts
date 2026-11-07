/*
 * Smart Auto module for ImageToolbox (fork by dennisp5-alt)
 * Licensed under the Apache License, Version 2.0
 *
 * Self-contained: analyses a bitmap and returns a chain of EXISTING
 * ImageToolbox filters. Contains no pixel-processing engine of its own.
 */

plugins {
    alias(libs.plugins.image.toolbox.library)
    alias(libs.plugins.image.toolbox.feature)
    alias(libs.plugins.image.toolbox.hilt)
    alias(libs.plugins.image.toolbox.compose)
}

android.namespace = "com.t8rin.imagetoolbox.feature.smart_auto"

dependencies {
    api(projects.core.filters)
}
