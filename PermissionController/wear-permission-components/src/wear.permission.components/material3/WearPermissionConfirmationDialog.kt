/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.permissioncontroller.wear.permission.components.material3

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AlertDialog as Material3AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Text
import com.android.permissioncontroller.wear.permission.components.material2.AlertDialog
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionMaterialUIVersion

data class DialogButtonContent(
    val icon: WearPermissionIconBuilder? = null,
    val onClick: (() -> Unit),
)

@Composable
fun WearPermissionConfirmationDialog(
    materialUIVersion: WearPermissionMaterialUIVersion =
        WearPermissionMaterialUIVersion.MATERIAL2_5,
    show: Boolean,
    iconRes: WearPermissionIconBuilder? = null,
    title: String? = null,
    message: String? = null,
    positiveButtonContent: DialogButtonContent? = null,
    negativeButtonContent: DialogButtonContent? = null,
) {

    if (materialUIVersion == WearPermissionMaterialUIVersion.MATERIAL3) {
        if (
            (positiveButtonContent == null && negativeButtonContent != null) ||
                (positiveButtonContent != null && negativeButtonContent == null)
        ) {
            val edgeButtonContent = (positiveButtonContent ?: negativeButtonContent)!!
            WearPermissionConfirmationDialogInternal(
                show = show,
                edgeButtonContent = edgeButtonContent,
                iconRes = iconRes,
                title = title,
                message = message,
            )
        } else {
            WearPermissionConfirmationDialogInternal(
                show = show,
                positiveButtonContent = positiveButtonContent,
                negativeButtonContent = negativeButtonContent,
                iconRes = iconRes,
                title = title,
                message = message,
            )
        }
    } else {
        AlertDialog(
            title = title,
            iconRes = iconRes,
            message = message ?: "",
            positiveButtonContent = positiveButtonContent,
            negativeButtonContent = negativeButtonContent,
            showDialog = show,
            scalingLazyListState = rememberScalingLazyListState(),
        )
    }
}

@Composable
private fun WearPermissionConfirmationDialogInternal(
    show: Boolean,
    edgeButtonContent: DialogButtonContent,
    iconRes: WearPermissionIconBuilder?,
    title: String?,
    message: String?,
) {
    val edgeIcon: @Composable RowScope.() -> Unit =
        edgeButtonContent.icon?.let {
            { it.modifier(Modifier.size(36.dp).align(Alignment.CenterVertically)).build() }
        } ?: AlertDialogDefaults.ConfirmIcon

    Material3AlertDialog(
        visible = show,
        onDismissRequest = edgeButtonContent.onClick,
        edgeButton = {
            AlertDialogDefaults.EdgeButton(onClick = edgeButtonContent.onClick, content = edgeIcon)
        },
        icon = { iconRes?.build() },
        title = title?.let { { Text(text = title) } } ?: {},
        text = message?.let { { Text(text = message) } },
    )
}

@Composable
private fun WearPermissionConfirmationDialogInternal(
    show: Boolean,
    positiveButtonContent: DialogButtonContent?,
    negativeButtonContent: DialogButtonContent?,
    iconRes: WearPermissionIconBuilder?,
    title: String?,
    message: String?,
) {
    val positiveButton: (@Composable RowScope.() -> Unit)? =
        positiveButtonContent?.let {
            {
                val positiveIcon: @Composable RowScope.() -> Unit =
                    positiveButtonContent.icon?.let {
                        {
                            it.modifier(Modifier.size(36.dp).align(Alignment.CenterVertically))
                                .build()
                        }
                    } ?: AlertDialogDefaults.ConfirmIcon

                AlertDialogDefaults.ConfirmButton(
                    onClick = positiveButtonContent.onClick,
                    content = positiveIcon,
                )
            }
        }

    val negativeButton: (@Composable RowScope.() -> Unit)? =
        negativeButtonContent?.let {
            {
                val negativeIcon: @Composable RowScope.() -> Unit =
                    negativeButtonContent.icon?.let {
                        {
                            it.modifier(Modifier.size(36.dp).align(Alignment.CenterVertically))
                                .build()
                        }
                    } ?: AlertDialogDefaults.DismissIcon

                AlertDialogDefaults.DismissButton(
                    onClick = negativeButtonContent.onClick,
                    content = negativeIcon,
                )
            }
        }

    Material3AlertDialog(
        visible = show,
        onDismissRequest = negativeButtonContent?.onClick ?: {},
        confirmButton = positiveButton ?: {},
        dismissButton = negativeButton ?: {},
        icon = { iconRes?.build() },
        title = title?.let { { Text(text = title) } } ?: {},
        text = message?.let { { Text(text = message) } },
    )
}
