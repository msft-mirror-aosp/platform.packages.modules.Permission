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

package com.android.permissioncontroller.tests.mocking.permission.data.repository

import android.content.Context
import android.os.UserHandle
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.permission.data.repository.v31.PermissionRepository
import com.android.permissioncontroller.permission.utils.PermissionMapping

class FakePermissionRepository(private val permissionFlags: Map<String, Int> = emptyMap()) :
    PermissionRepository {
    override suspend fun getPermissionFlags(
        permissionName: String,
        packageName: String,
        user: UserHandle
    ): Int {
        return permissionFlags[permissionName] ?: 0
    }

    override suspend fun getPermissionGroupLabel(
        context: Context,
        groupName: String
    ): CharSequence {
        TODO("Not yet implemented")
    }

    override fun getPermissionGroupsForPrivacyDashboard(): List<String> {
        return if (SdkLevel.isAtLeastT()) {
            PermissionMapping.getPlatformPermissionGroups().filter {
                it != android.Manifest.permission_group.NOTIFICATIONS
            }
        } else {
            PermissionMapping.getPlatformPermissionGroups()
        }
    }
}
