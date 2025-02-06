/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.permissioncontroller.wear.permission.components.material3

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/* A common class for calculating padding for list items as per the latest design.
https://www.figma.com/design/nb1atBKcK3luF8AXWLUe0X/BC25-Settings-on-Wear?node-id=2336-3304&t=n35PgTUC2O8hGSI0-0 */
data class WearPermissionScaffoldPaddingDefaults(
    private val screenWidth: Int,
    private val screenHeight: Int,
) {
    private val scrollContentHorizontalPadding = (screenWidth * 0.052).dp
    private val titleHorizontalPadding = (screenWidth * 0.0520).dp
    private val subtitleHorizontalPadding = (screenWidth * 0.0624).dp
    private val scrollContentTopPadding = (screenHeight * 0.1664).dp
    private val dialogScrollContentLargeTopPadding = (screenHeight * 0.10).dp
    private val dialogScrollContentTopPadding = (screenHeight * 0.012).dp
    private val scrollContentBottomPadding = (screenHeight * 0.3646).dp
    private val noPadding = 0.dp
    private val defaultItemPadding = 4.dp
    private val largeItemPadding = 8.dp
    private val extraLargePadding = 12.dp

    fun titlePaddingValues(needsLargePadding: Boolean): PaddingValues =
        PaddingValues(
            start = titleHorizontalPadding,
            top = defaultItemPadding,
            bottom = if (needsLargePadding) largeItemPadding else defaultItemPadding,
            end = titleHorizontalPadding,
        )

    fun subHeaderPaddingValues(needsLargePadding: Boolean): PaddingValues =
        PaddingValues(
            start = subtitleHorizontalPadding,
            top = if (needsLargePadding) extraLargePadding else noPadding,
            bottom = largeItemPadding,
            end = subtitleHorizontalPadding,
        )

    fun scrollContentPaddingForDialogs(needsLargePadding: Boolean) =
        PaddingValues(
            start = scrollContentHorizontalPadding,
            end = scrollContentHorizontalPadding,
            top =
                if (needsLargePadding) {
                    dialogScrollContentLargeTopPadding
                } else {
                    dialogScrollContentTopPadding
                },
            bottom = scrollContentBottomPadding,
        )

    val subTitlePaddingValues =
        PaddingValues(
            start = subtitleHorizontalPadding,
            top = defaultItemPadding,
            bottom = largeItemPadding,
            end = subtitleHorizontalPadding,
        )
    val scrollContentPadding =
        PaddingValues(
            start = scrollContentHorizontalPadding,
            end = scrollContentHorizontalPadding,
            top = scrollContentTopPadding,
            bottom = scrollContentBottomPadding,
        )
}
