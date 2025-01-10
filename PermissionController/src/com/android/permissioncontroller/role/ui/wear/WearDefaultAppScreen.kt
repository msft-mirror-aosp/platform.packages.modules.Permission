/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.wear

import android.content.pm.ApplicationInfo
import android.util.Pair
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.ToggleChipDefaults
import com.android.permissioncontroller.permission.ui.wear.elements.ScrollableScreen
import com.android.permissioncontroller.permission.ui.wear.elements.material2.DialogButtonContent
import com.android.permissioncontroller.permission.ui.wear.elements.material2.ListFooter
import com.android.permissioncontroller.permission.ui.wear.elements.material2.ToggleChip
import com.android.permissioncontroller.permission.ui.wear.elements.material2.ToggleChipToggleControl
import com.android.permissioncontroller.permission.ui.wear.elements.material2.toggleChipDisabledColors
import com.android.permissioncontroller.permission.ui.wear.elements.material3.WearPermissionConfirmationDialog
import com.android.permissioncontroller.permission.ui.wear.theme.ResourceHelper
import com.android.permissioncontroller.permission.ui.wear.theme.WearPermissionMaterialUIVersion
import com.android.permissioncontroller.role.ui.wear.model.ConfirmDialogArgs

@Composable
fun WearDefaultAppScreen(helper: WearDefaultAppHelper) {
    val roleLiveData = helper.viewModel.roleLiveData.observeAsState(emptyList())
    val showConfirmDialog =
        helper.confirmDialogViewModel.showConfirmDialogLiveData.observeAsState(false)
    var isLoading by remember { mutableStateOf(true) }
    val materialUIVersion = ResourceHelper.materialUIVersionInSettings
    Box {
        WearDefaultAppContent(isLoading, roleLiveData.value, helper)
        ConfirmDialog(
            materialUIVersion = materialUIVersion,
            showDialog = showConfirmDialog.value,
            args = helper.confirmDialogViewModel.confirmDialogArgs,
        )
    }
    if (isLoading && roleLiveData.value.isNotEmpty()) {
        isLoading = false
    }
}

@Composable
private fun WearDefaultAppContent(
    isLoading: Boolean,
    qualifyingApplications: List<Pair<ApplicationInfo, Boolean>>,
    helper: WearDefaultAppHelper,
) {
    ScrollableScreen(title = helper.getTitle(), isLoading = isLoading) {
        helper.getNonePreference(qualifyingApplications)?.let {
            item {
                ToggleChip(
                    label = it.title.toString(),
                    icon = it.icon,
                    checked = it.checked,
                    onCheckedChanged = it.onDefaultCheckChanged,
                    toggleControl = ToggleChipToggleControl.Radio,
                    labelMaxLine = Integer.MAX_VALUE,
                )
            }
        }
        for (pref in helper.getPreferences(qualifyingApplications)) {
            item {
                ToggleChip(
                    label = pref.title.toString(),
                    icon = pref.icon,
                    colors =
                        if (pref.isEnabled) {
                            ToggleChipDefaults.toggleChipColors()
                        } else {
                            toggleChipDisabledColors()
                        },
                    secondaryLabel = pref.summary?.toString(),
                    checked = pref.checked,
                    onCheckedChanged = pref.getOnCheckChanged(),
                    toggleControl = ToggleChipToggleControl.Radio,
                    labelMaxLine = Integer.MAX_VALUE,
                    secondaryLabelMaxLine = Integer.MAX_VALUE,
                )
            }
        }

        item { ListFooter(description = helper.getDescription()) }
    }
}

@Composable
private fun ConfirmDialog(
    materialUIVersion: WearPermissionMaterialUIVersion,
    showDialog: Boolean,
    args: ConfirmDialogArgs?,
) {
    args?.run {
        WearPermissionConfirmationDialog(
            materialUIVersion = materialUIVersion,
            show = showDialog,
            message = message,
            positiveButtonContent = DialogButtonContent(onClick = onOkButtonClick),
            negativeButtonContent = DialogButtonContent(onClick = onCancelButtonClick),
        )
    }
}
