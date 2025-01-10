/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.permissioncontroller.permission.ui.wear.elements.material2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipColors
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import com.android.permissioncontroller.permission.ui.wear.elements.material2.layout.ScalingLazyColumn
import com.android.permissioncontroller.permission.ui.wear.elements.material2.layout.ScalingLazyColumnDefaults.responsive
import com.android.permissioncontroller.permission.ui.wear.elements.material2.layout.ScalingLazyColumnState
import com.android.permissioncontroller.permission.ui.wear.elements.material2.layout.rememberColumnState
import com.android.permissioncontroller.permission.ui.wear.elements.material3.DialogButtonContent
import com.android.permissioncontroller.permission.ui.wear.elements.material3.WearPermissionIconBuilder
import com.android.permissioncontroller.permission.ui.wear.elements.material3.defaultAlertConfirmIcon
import com.android.permissioncontroller.permission.ui.wear.elements.material3.defaultAlertDismissIcon

@Composable
fun ResponsiveDialogContent(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    message: @Composable (() -> Unit)? = null,
    positiveButtonContent: DialogButtonContent? = null,
    negativeButtonContent: DialogButtonContent? = null,
    state: ScalingLazyColumnState =
        rememberColumnState(
            responsive(firstItemIsFullWidth = icon == null, additionalPaddingAtBottom = 0.dp)
        ),
    showPositionIndicator: Boolean = true,
    content: (ScalingLazyListScope.() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        positionIndicator = {
            if (showPositionIndicator) {
                PositionIndicator(scalingLazyListState = state.state)
            }
        },
        timeText = {},
    ) {
        // This will be applied only to the content.
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.body2) {
            ScalingLazyColumn(columnState = state) {
                icon?.let {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp), // 8.dp below icon
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            it()
                        }
                    }
                }
                title?.let {
                    item {
                        CompositionLocalProvider(
                            LocalTextStyle provides
                                MaterialTheme.typography.title3.copy(fontWeight = FontWeight.W600)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(titleMaxWidthFraction)
                                    .padding(bottom = 8.dp) // 12.dp below icon
                            ) {
                                it()
                            }
                        }
                    }
                }
                if (icon == null && title == null) {
                    // Ensure the content is visible when there is nothing above it.
                    item { Spacer(Modifier.height(20.dp)) }
                }
                message?.let {
                    item { Box(Modifier.fillMaxWidth(messageMaxWidthFraction)) { it() } }
                }
                content?.let { it() }
                if (positiveButtonContent != null || negativeButtonContent != null) {
                    item {
                        val width = LocalConfiguration.current.screenWidthDp
                        // Single buttons, or buttons on smaller screens are not meant to be
                        // responsive.
                        val buttonWidth =
                            if (
                                width < 225 ||
                                    positiveButtonContent == null ||
                                    negativeButtonContent == null
                            ) {
                                ButtonDefaults.DefaultButtonSize
                            } else {
                                // 14.56% on top of 5.2% margin on the sides, 12.dp between.
                                ((width * (1f - (2 * 0.1456f) - (2 * 0.052f)) - 12) / 2).dp
                            }
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(
                                    top = if (content != null || message != null) 12.dp else 0.dp
                                ),
                            horizontalArrangement = spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            negativeButtonContent?.run {
                                ResponsiveButton(
                                    this.icon
                                        ?: WearPermissionIconBuilder.defaultAlertDismissIcon()
                                            .tint(
                                                ChipDefaults.secondaryChipColors()
                                                    .contentColor(true)
                                                    .value
                                            ),
                                    onClick,
                                    buttonWidth,
                                    ChipDefaults.secondaryChipColors(),
                                )
                            }
                            positiveButtonContent?.run {
                                ResponsiveButton(
                                    this.icon
                                        ?: WearPermissionIconBuilder.defaultAlertConfirmIcon(),
                                    onClick,
                                    buttonWidth,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveButton(
    icon: WearPermissionIconBuilder,
    onClick: () -> Unit,
    buttonWidth: Dp,
    colors: ChipColors = ChipDefaults.primaryChipColors(),
) {
    androidx.wear.compose.material.Chip(
        label = {
            Box(Modifier.fillMaxWidth()) {
                icon
                    .modifier(Modifier.size(ButtonDefaults.DefaultIconSize).align(Alignment.Center))
                    .build()
            }
        },
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        onClick = onClick,
        modifier = Modifier.width(buttonWidth),
        colors = colors,
    )
}

internal const val globalHorizontalPadding = 5.2f
internal const val messageExtraHorizontalPadding = 4.56f
internal const val titleExtraHorizontalPadding = 8.84f

// Fraction of the max available width that message should take (after global and message padding)
internal val messageMaxWidthFraction =
    1f - 2f * calculatePaddingFraction(messageExtraHorizontalPadding)

// Fraction of the max available width that title should take (after global and message padding)
internal val titleMaxWidthFraction = 1f - 2f * calculatePaddingFraction(titleExtraHorizontalPadding)

// Calculate total padding given global padding and additional padding required inside that.
internal fun calculatePaddingFraction(extraPadding: Float) =
    extraPadding / (100f - 2f * globalHorizontalPadding)
