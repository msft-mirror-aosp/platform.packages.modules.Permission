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

package com.android.permissioncontroller.permission.utils.v31

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.permission.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class AdminRestrictedPermissionsUtilsTest {

    @JvmField @Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dpm: DevicePolicyManager = mock(DevicePolicyManager::class.java)

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
    @RequiresFlagsEnabled(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    @Test
    fun mayAdminGrantPermission_healthPermissions_restricted() {
        val permissions: Set<String> = HealthConnectManager.getHealthPermissions(context)
        for (permission in permissions) {
            val canGrant =
              AdminRestrictedPermissionsUtils.mayAdminGrantPermission(
                  permission,
                  HealthPermissions.HEALTH_PERMISSION_GROUP,
                  /* canAdminGrantSensorsPermissions= */ false,
                  /* isManagedProfile= */ false,
                  dpm,
              )
            assertEquals(false, canGrant)
        }
    }
}
