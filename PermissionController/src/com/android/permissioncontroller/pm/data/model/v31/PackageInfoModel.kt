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

package com.android.permissioncontroller.pm.data.model.v31

import android.content.pm.PackageInfo

/** A model/data class representing [PackageInfo] class. */
data class PackageInfoModel(
    val packageName: String,
    val requestedPermissions: List<String> = emptyList(),
    val requestedPermissionsFlags: List<Int> = emptyList(),
    val applicationFlags: Int = 0,
) {
    constructor(
        packageInfo: PackageInfo
    ) : this(
        packageInfo.packageName,
        packageInfo.requestedPermissions?.toList() ?: emptyList(),
        packageInfo.requestedPermissionsFlags?.toList() ?: emptyList(),
        requireNotNull(packageInfo.applicationInfo).flags
    )
}

data class PackageAttributionModel(
    val packageName: String,
    val areUserVisible: Boolean = false,
    /** A map of attribution tag to attribution resource label identifier. */
    val tagResourceMap: Map<String, Int>? = null,
    /** A map of attribution resource label identifier to attribution label. */
    val resourceLabelMap: Map<Int, String>? = null,
)
