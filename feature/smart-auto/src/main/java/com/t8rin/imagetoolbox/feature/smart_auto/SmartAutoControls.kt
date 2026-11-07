/*
 * Smart Auto module for ImageToolbox (fork by dennisp5-alt)
 * Licensed under the Apache License, Version 2.0
 */

package com.t8rin.imagetoolbox.feature.smart_auto

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.t8rin.imagetoolbox.core.resources.Icons
import com.t8rin.imagetoolbox.core.resources.icons.AutoFixHigh
import com.t8rin.imagetoolbox.core.resources.icons.Close
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedButton
import com.t8rin.imagetoolbox.core.ui.widget.enhanced.EnhancedIconButton
import com.t8rin.imagetoolbox.core.ui.widget.modifier.ShapeDefaults
import com.t8rin.imagetoolbox.core.ui.widget.modifier.container
import com.t8rin.imagetoolbox.core.ui.widget.preferences.PreferenceRowSwitch

/**
 * Smart Auto button, optional per-image batch toggle and debug overlay.
 * Stateless: the host screen owns all state.
 */
@Composable
fun SmartAutoControls(
    isRunning: Boolean,
    onApply: () -> Unit,
    showEachImageToggle: Boolean,
    eachImage: Boolean,
    onEachImageChange: (Boolean) -> Unit,
    report: String?,
    onDismissReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EnhancedButton(
            onClick = onApply,
            enabled = !isRunning,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoFixHigh,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isRunning) "Analysing…" else "Smart Auto")
        }

        if (showEachImageToggle) {
            PreferenceRowSwitch(
                title = "Smart Auto each image",
                subtitle = "Re-analyse every photo when saving the batch, " +
                        "instead of using the preview photo's settings",
                checked = eachImage,
                onClick = onEachImageChange,
                shape = ShapeDefaults.extraLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AnimatedVisibility(visible = report != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .container(shape = ShapeDefaults.extraLarge)
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Smart Auto debug",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    EnhancedIconButton(onClick = onDismissReport) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                Text(
                    text = report.orEmpty(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
