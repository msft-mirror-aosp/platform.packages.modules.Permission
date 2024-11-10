/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.ui.wear.elements.material3

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.android.permissioncontroller.permission.ui.wear.elements.ListFooter
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionMaterialUIVersion

@Composable
fun WearPermissionListFooter(
    materialUIVersion: WearPermissionMaterialUIVersion,
    label: String,
    iconBuilder: WearPermissionIconBuilder? = null,
    onClick: (() -> Unit) = {},
) {
    if (materialUIVersion == WearPermissionMaterialUIVersion.MATERIAL2_5) {
        ListFooter(
            description = label,
            iconRes = iconBuilder?.let { it.iconResource as Int },
            onClick = onClick,
        )
    } else {
        WearPermissionListFooterInternal(label, iconBuilder, onClick)
    }
}

@Composable
private fun WearPermissionListFooterInternal(
    label: String,
    iconBuilder: WearPermissionIconBuilder?,
    onClick: () -> Unit,
) {
    val footerTextComposable: (@Composable RowScope.() -> Unit) = {
        Text(modifier = Modifier.fillMaxWidth(), text = label, maxLines = Int.MAX_VALUE)
    }
    Button(
        icon = { iconBuilder?.build() },
        label = {},
        secondaryLabel = footerTextComposable,
        enabled = true,
        onClick = onClick,
        modifier = Modifier.requiredHeightIn(min = 1.dp).fillMaxWidth(),
        colors = ButtonDefaults.childButtonColors(),
    )
}
